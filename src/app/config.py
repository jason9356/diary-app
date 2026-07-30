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
EditorMode = Literal["wysiwyg", "source", "edit", "preview", "split"]  # legacy modes accepted


@dataclass
class AppConfig:
    """User-facing preferences persisted as JSON."""

    data_dir: str = ""
    theme: ThemeMode = "system"
    font_mode: FontMode = "system"
    editor_mode: EditorMode = "wysiwyg"  # type: ignore[assignment]
    window_width: int = 1100
    window_height: int = 720
    sidebar_width: int = 256
    autosave_ms: int = 600
    min_window_width: int = 800
    min_window_height: int = 520

    # Storage: local | cloud (WebDAV). Legacy sync_endpoint/token ignored by UI.
    storage_target: str = "local"
    cloud_provider: str = "webdav"
    webdav_url: str = ""
    webdav_user: str = ""
    webdav_root: str = "/sparkbox"
    webdav_pass: str = ""

    # Legacy self-hosted sync (kept for config migration only; not used by UI).
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
    def todos_root(self) -> Path:
        return self.data_path / "todos"

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
                if hasattr(cfg, key) and key not in ("sync_token", "webdav_pass"):
                    setattr(cfg, key, value)
        except Exception as exc:  # noqa: BLE001 — recover with defaults
            logger.warning("Failed to load config, using defaults: %s", exc)

    secrets = project_root() / "data" / "sync_secrets.json"
    if secrets.exists():
        try:
            sec = json.loads(secrets.read_text(encoding="utf-8"))
            if isinstance(sec.get("sync_token"), str):
                cfg.sync_token = sec["sync_token"]
            if isinstance(sec.get("webdav_pass"), str):
                cfg.webdav_pass = sec["webdav_pass"]
        except Exception as exc:  # noqa: BLE001
            logger.warning("Failed to load sync secrets: %s", exc)

    # Empty / missing data_dir → project-local default (portable; no machine path).
    if not str(cfg.data_dir).strip():
        cfg.data_dir = ""

    # Migrate legacy self-hosted endpoint → local (cloud must be reconfigured as WebDAV).
    if cfg.storage_target not in ("local", "cloud"):
        cfg.storage_target = "local"

    cfg.data_path.mkdir(parents=True, exist_ok=True)
    cfg.diary_root.mkdir(parents=True, exist_ok=True)
    cfg.assets_root.mkdir(parents=True, exist_ok=True)
    cfg.todos_root.mkdir(parents=True, exist_ok=True)
    default_log_dir().mkdir(parents=True, exist_ok=True)
    return cfg


def save_config(cfg: AppConfig, *, update_secrets: bool = False, update_token: bool = False) -> None:
    """Persist preferences to JSON.

    Secrets (``webdav_pass`` / legacy ``sync_token``) are only written when
    ``update_secrets`` or ``update_token`` is True.
    """
    path = cfg.config_path
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = asdict(cfg)
    token = payload.pop("sync_token", "")
    webdav_pass = payload.pop("webdav_pass", "")
    # Keep default data location as "" so tracked config stays machine-agnostic.
    try:
        if not str(cfg.data_dir).strip() or Path(cfg.data_dir).resolve() == default_data_dir().resolve():
            payload["data_dir"] = ""
    except OSError:
        payload["data_dir"] = ""
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    if update_secrets or update_token:
        secrets = project_root() / "data" / "sync_secrets.json"
        existing: dict = {}
        if secrets.exists():
            try:
                existing = json.loads(secrets.read_text(encoding="utf-8"))
            except Exception:  # noqa: BLE001
                existing = {}
        existing["sync_token"] = token
        existing["webdav_pass"] = webdav_pass
        secrets.write_text(
            json.dumps(existing, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
    logger.debug("Config saved → %s", path)
