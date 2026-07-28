"""
Day-level context (location / weather) stored as JSON per calendar date.

Merge priority: phone > desktop > manual; same rank compares context_updated_at.
"""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Optional

from utils.paths import day_json_relpath

logger = logging.getLogger("diary.storage.day")

CONTEXT_RANK = {"phone": 3, "desktop": 2, "manual": 1, "": 0}


@dataclass
class DayContext:
    date: str = ""
    location: str = ""
    weather: str = ""
    temp_c: Optional[float] = None
    context_source: str = ""
    context_updated_at: str = ""
    updated_at: str = ""


def can_overwrite_context(existing_source: str, new_source: str) -> bool:
    return CONTEXT_RANK.get(new_source, 0) >= CONTEXT_RANK.get(existing_source or "", 0)


def merge_day_context(server: Optional[DayContext], incoming: DayContext) -> DayContext:
    if server is None:
        out = incoming
        if not out.updated_at:
            out.updated_at = out.context_updated_at
        return out
    in_rank = CONTEXT_RANK.get(incoming.context_source or "", 0)
    srv_rank = CONTEXT_RANK.get(server.context_source or "", 0)
    if in_rank > srv_rank or (
        in_rank == srv_rank
        and (incoming.context_updated_at or "") > (server.context_updated_at or "")
    ):
        winner = incoming
    else:
        winner = server
    updated = winner.updated_at or winner.context_updated_at
    return DayContext(
        date=winner.date or incoming.date or server.date,
        location=winner.location,
        weather=winner.weather,
        temp_c=winner.temp_c,
        context_source=winner.context_source,
        context_updated_at=winner.context_updated_at,
        updated_at=updated or incoming.updated_at or server.updated_at,
    )


class DayStore:
    def __init__(self, diary_root: Path) -> None:
        self.diary_root = diary_root
        self.diary_root.mkdir(parents=True, exist_ok=True)

    def path_for(self, entry_date: str) -> Path:
        return self.diary_root / day_json_relpath(entry_date)

    def read(self, entry_date: str) -> Optional[DayContext]:
        path = self.path_for(entry_date)
        if not path.exists():
            return None
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as exc:
            logger.warning("Failed to read day context %s: %s", path, exc)
            return None
        temp = data.get("temp_c")
        return DayContext(
            date=data.get("date", entry_date),
            location=data.get("location", ""),
            weather=data.get("weather", ""),
            temp_c=float(temp) if temp is not None else None,
            context_source=data.get("context_source", ""),
            context_updated_at=data.get("context_updated_at", ""),
            updated_at=data.get("updated_at", ""),
        )

    def write(self, ctx: DayContext) -> str:
        """Write day context JSON. Returns relative path from diary root."""
        rel = day_json_relpath(ctx.date)
        path = self.diary_root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "date": ctx.date,
            "location": ctx.location,
            "weather": ctx.weather,
            "temp_c": ctx.temp_c,
            "context_source": ctx.context_source,
            "context_updated_at": ctx.context_updated_at,
            "updated_at": ctx.updated_at or ctx.context_updated_at,
        }
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        logger.debug("Wrote day context %s", path)
        return rel

    def merge_write(self, incoming: DayContext) -> DayContext:
        existing = self.read(incoming.date)
        merged = merge_day_context(existing, incoming)
        self.write(merged)
        return merged

    def exists(self, entry_date: str) -> bool:
        return self.path_for(entry_date).exists()
