"""
Path helpers and project root resolution.
"""
from __future__ import annotations

from pathlib import Path


def project_root() -> Path:
    """Return repository root (parent of ``src/``)."""
    return Path(__file__).resolve().parents[2]


def default_data_dir() -> Path:
    return project_root() / "data"


def default_log_dir() -> Path:
    return project_root() / "logs"


def diary_md_relpath_for_id(entry_date: str, entry_id: str) -> str:
    """
    Relative path under diary root: ``YYYY/MM/<id>.md``.

    ``entry_date`` must be ``YYYY-MM-DD``; ``entry_id`` is the note UUID.
    """
    year, month, _ = entry_date.split("-")
    return f"{year}/{month}/{entry_id}.md"


def day_json_relpath(entry_date: str) -> str:
    """Relative day-context path: ``YYYY/MM/<date>.day.json``."""
    year, month, _ = entry_date.split("-")
    return f"{year}/{month}/{entry_date}.day.json"


def asset_dir_relpath(entry_id: str) -> str:
    """Relative asset folder: ``<id>`` under assets root."""
    return entry_id


def diary_md_relpath(entry_date: str) -> str:
    """
    Legacy v1 path: ``YYYY/MM/YYYY-MM-DD.md``.

    Prefer ``diary_md_relpath_for_id`` for protocol v2 notes.
    """
    year, month, _ = entry_date.split("-")
    return f"{year}/{month}/{entry_date}.md"
