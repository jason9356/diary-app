"""
Local image / attachment storage.

Layout: ``assets_root/YYYY-MM-DD/<uuid>.<ext>``
Paths stored in Markdown are relative to the data root:
``assets/YYYY-MM-DD/filename.jpg``
"""
from __future__ import annotations

import logging
import shutil
import uuid
from pathlib import Path

from utils.paths import asset_dir_relpath

logger = logging.getLogger("diary.storage.assets")

ALLOWED_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"}


class AssetStore:
    def __init__(self, assets_root: Path, data_root: Path) -> None:
        self.assets_root = assets_root
        self.data_root = data_root
        self.assets_root.mkdir(parents=True, exist_ok=True)

    def save_image(self, entry_date: str, source: Path) -> str:
        """
        Copy image into assets folder. Returns markdown-relative path
        from data root, e.g. ``assets/2026-07-27/abc.jpg``.
        """
        ext = source.suffix.lower()
        if ext not in ALLOWED_EXT:
            raise ValueError(f"Unsupported image type: {ext}")
        if not source.exists():
            raise FileNotFoundError(str(source))

        folder = self.assets_root / asset_dir_relpath(entry_date)
        folder.mkdir(parents=True, exist_ok=True)
        name = f"{uuid.uuid4().hex[:12]}{ext}"
        dest = folder / name
        shutil.copy2(source, dest)
        rel = f"assets/{entry_date}/{name}"
        logger.info("Saved asset %s", rel)
        return rel

    def absolute(self, rel_from_data: str) -> Path:
        return (self.data_root / rel_from_data).resolve()

    def list_for_date(self, entry_date: str) -> list[Path]:
        folder = self.assets_root / asset_dir_relpath(entry_date)
        if not folder.exists():
            return []
        return sorted(
            p for p in folder.iterdir() if p.is_file() and p.suffix.lower() in ALLOWED_EXT
        )
