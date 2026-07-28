"""
Application configuration.

Data layout (sync-ready):
  data/
    diary/YYYY/MM/YYYY-MM-DD.md
    assets/YYYY-MM-DD/<file>
    diary.db                 # SQLite index + metadata
"""
from __future__ import annotations

import json
import logging
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Literal

from utils.paths import default_data_dir, default_log_dir, project_root

logger = logging.getLogger("diary.config")

ThemeMode = Literal["system", "light", "dark"]
FontMode = Literal["system", "mono"]
EditorMode = Literal["edit", "preview", "split"]


@dataclass
class AppConfig:
    """User-facing preferences persisted as JSON."""

    data_dir: str = ""
    theme: ThemeMode = "system"
    font_mode: FontMode = "system"
    editor_mode: EditorMode = "split"
    window_width: int = 1100
    window_height: int = 720
    sidebar_width: int = 256
    autosave_ms: int = 600
    min_window_width: int = 800
    min_window_height: int = 520

    # Sync (personal VPS). Token stays local — do not commit real values.
    sync_endpoint: str = ""
    sync_token: str = ""
    device_id: str = ""
    sync_cursor: int = 0

    # Preferred city for Open-Meteo (desktop). Empty → IP geolocation once.
    weather_city: str = ""

    @property
    def data_path(self) -> Path:
        return Path(self.data_dir) if self.data_dir else default_data_dir()

    @property
    def diary_root(self) -> Path:
        return self.data_path / "diary"

    @property
    def assets_root(self) -> Path:
        return self.data_path / "assets"

    @property
    def db_path(self) -> Path:
        return self.data_path / "diary.db"

    @property
    def config_path(self) -> Path:
        return project_root() / "data" / "config.json"


def load_config() -> AppConfig:
    """Load config from disk or create defaults."""
    cfg = AppConfig()
    path = cfg.config_path
    if path.exists():
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
            for key, value in raw.items():
                if hasattr(cfg, key) and key != "sync_token":
                    setattr(cfg, key, value)
        except Exception as exc:  # noqa: BLE001 — recover with defaults
            logger.warning("Failed to load config, using defaults: %s", exc)

    secrets = project_root() / "data" / "sync_secrets.json"
    if secrets.exists():
        try:
            sec = json.loads(secrets.read_text(encoding="utf-8"))
            if isinstance(sec.get("sync_token"), str):
                cfg.sync_token = sec["sync_token"]
        except Exception as exc:  # noqa: BLE001
            logger.warning("Failed to load sync secrets: %s", exc)

    # Empty / missing data_dir → project-local default (portable; no machine path).
    if not str(cfg.data_dir).strip():
        cfg.data_dir = ""

    cfg.data_path.mkdir(parents=True, exist_ok=True)
    cfg.diary_root.mkdir(parents=True, exist_ok=True)
    cfg.assets_root.mkdir(parents=True, exist_ok=True)
    default_log_dir().mkdir(parents=True, exist_ok=True)
    return cfg


def save_config(cfg: AppConfig, *, update_token: bool = False) -> None:
    """Persist preferences to JSON.

    ``sync_token`` is only written when ``update_token=True`` (sync settings dialog).
    Other saves (window size, theme, …) must not overwrite ``sync_secrets.json``
    with a stale in-memory token.
    """
    path = cfg.config_path
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = asdict(cfg)
    token = payload.pop("sync_token", "")
    # Keep default data location as "" so tracked config stays machine-agnostic.
    try:
        if not str(cfg.data_dir).strip() or Path(cfg.data_dir).resolve() == default_data_dir().resolve():
            payload["data_dir"] = ""
    except OSError:
        payload["data_dir"] = ""
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    if update_token:
        secrets = project_root() / "data" / "sync_secrets.json"
        secrets.write_text(
            json.dumps({"sync_token": token}, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
    logger.debug("Config saved → %s", path)
