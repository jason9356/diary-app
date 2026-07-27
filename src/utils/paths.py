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


def diary_md_relpath(entry_date: str) -> str:
    """
    Relative path under diary root: ``YYYY/MM/YYYY-MM-DD.md``.

    ``entry_date`` must be ``YYYY-MM-DD``.
    """
    year, month, _ = entry_date.split("-")
    return f"{year}/{month}/{entry_date}.md"


def asset_dir_relpath(entry_date: str) -> str:
    """Relative asset folder: ``YYYY-MM-DD`` under assets root."""
    return entry_date
