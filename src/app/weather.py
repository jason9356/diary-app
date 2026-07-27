"""
Desktop weather / place lookup via Open-Meteo (no API key).

Phone clients may overwrite with context_source=phone later.
"""
from __future__ import annotations

import json
import logging
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger("diary.weather")

# WMO weather interpretation codes → short Chinese labels.
_WMO_ZH = {
    0: "晴",
    1: "晴间多云",
    2: "多云",
    3: "阴",
    45: "雾",
    48: "雾凇",
    51: "小毛毛雨",
    53: "毛毛雨",
    55: "大毛毛雨",
    56: "冻毛毛雨",
    57: "强冻毛毛雨",
    61: "小雨",
    63: "中雨",
    65: "大雨",
    66: "冻雨",
    67: "强冻雨",
    71: "小雪",
    73: "中雪",
    75: "大雪",
    77: "雪粒",
    80: "小阵雨",
    81: "阵雨",
    82: "强阵雨",
    85: "小阵雪",
    86: "强阵雪",
    95: "雷阵雨",
    96: "雷阵雨伴冰雹",
    99: "强雷阵雨伴冰雹",
}


@dataclass
class WeatherSnapshot:
    location: str
    weather: str
    temp_c: float
    latitude: float
    longitude: float


def _get_json(url: str, timeout: float = 8.0) -> dict:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "DiaryApp/0.2 (local; Open-Meteo)"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def resolve_city(city: str) -> Optional[tuple[str, float, float]]:
    """Geocode a city name via Open-Meteo. Returns (label, lat, lon)."""
    q = city.strip()
    if not q:
        return None
    url = (
        "https://geocoding-api.open-meteo.com/v1/search?"
        + urllib.parse.urlencode({"name": q, "count": 1, "language": "zh", "format": "json"})
    )
    try:
        data = _get_json(url)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
        logger.warning("Geocode failed for %s: %s", q, exc)
        return None
    results = data.get("results") or []
    if not results:
        return None
    r = results[0]
    name = r.get("name") or q
    admin = r.get("admin1") or ""
    country = r.get("country") or ""
    label = name
    if admin and admin != name:
        label = f"{name}·{admin}"
    elif country and country not in label:
        label = f"{name}·{country}"
    return label, float(r["latitude"]), float(r["longitude"])


def resolve_ip_location() -> Optional[tuple[str, float, float]]:
    """Coarse place from public IP (best-effort)."""
    try:
        data = _get_json("https://ipapi.co/json/")
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
        logger.warning("IP geolocation failed: %s", exc)
        return None
    if data.get("error"):
        return None
    lat, lon = data.get("latitude"), data.get("longitude")
    if lat is None or lon is None:
        return None
    city = data.get("city") or data.get("region") or "当前位置"
    return str(city), float(lat), float(lon)


def fetch_weather(lat: float, lon: float, location_label: str) -> Optional[WeatherSnapshot]:
    url = (
        "https://api.open-meteo.com/v1/forecast?"
        + urllib.parse.urlencode(
            {
                "latitude": f"{lat:.4f}",
                "longitude": f"{lon:.4f}",
                "current": "temperature_2m,weather_code",
                "timezone": "auto",
            }
        )
    )
    try:
        data = _get_json(url)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
        logger.warning("Open-Meteo failed: %s", exc)
        return None
    current = data.get("current") or {}
    code = int(current.get("weather_code", 0))
    temp = current.get("temperature_2m")
    if temp is None:
        return None
    return WeatherSnapshot(
        location=location_label,
        weather=_WMO_ZH.get(code, "天气"),
        temp_c=round(float(temp), 1),
        latitude=lat,
        longitude=lon,
    )


def fetch_for_city(city: str) -> Optional[WeatherSnapshot]:
    resolved = resolve_city(city)
    if not resolved:
        return None
    label, lat, lon = resolved
    return fetch_weather(lat, lon, label)


def fetch_desktop(preferred_city: str = "") -> Optional[WeatherSnapshot]:
    """
    Desktop auto path: preferred city → IP coarse location.
    """
    if preferred_city.strip():
        snap = fetch_for_city(preferred_city)
        if snap:
            return snap
    ip = resolve_ip_location()
    if not ip:
        return None
    label, lat, lon = ip
    return fetch_weather(lat, lon, label)
