"""
Export all diary data as a ZIP archive.
"""
from __future__ import annotations

import logging
import zipfile
from datetime import datetime
from pathlib import Path

logger = logging.getLogger("diary.storage.export")


def export_zip(data_root: Path, dest_zip: Path | None = None) -> Path:
    """
    Pack ``diary/`` and ``assets/`` into a ZIP.
    Returns the path of the created archive.
    """
    data_root = data_root.resolve()
    if dest_zip is None:
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        dest_zip = data_root.parent / f"diary-export-{stamp}.zip"

    dest_zip.parent.mkdir(parents=True, exist_ok=True)
    include_roots = ["diary", "assets"]

    with zipfile.ZipFile(dest_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for name in include_roots:
            root = data_root / name
            if not root.exists():
                continue
            for path in root.rglob("*"):
                if path.is_file():
                    arcname = path.relative_to(data_root).as_posix()
                    zf.write(path, arcname)

    logger.info("Exported ZIP → %s", dest_zip)
    return dest_zip
