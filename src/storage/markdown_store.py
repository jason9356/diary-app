"""
Markdown file store on disk.

Layout: ``diary_root/YYYY/MM/YYYY-MM-DD.md``
Files are plain UTF-8 Markdown so they remain human-readable
and sync-friendly for future clients.
"""
from __future__ import annotations

import logging
import re
from pathlib import Path

from utils.paths import diary_md_relpath

logger = logging.getLogger("diary.storage.markdown")

FRONT_MATTER_RE = re.compile(
    r"^---\s*\n(.*?)\n---\s*\n(.*)$",
    re.DOTALL,
)


class MarkdownStore:
    def __init__(self, diary_root: Path) -> None:
        self.diary_root = diary_root
        self.diary_root.mkdir(parents=True, exist_ok=True)

    def path_for(self, entry_date: str) -> Path:
        return self.diary_root / diary_md_relpath(entry_date)

    def read(self, entry_date: str) -> str:
        path = self.path_for(entry_date)
        if not path.exists():
            return ""
        text = path.read_text(encoding="utf-8")
        body = self._strip_front_matter(text)
        return body

    def write(self, entry_date: str, body: str, title: str = "") -> str:
        """
        Write markdown file. Returns relative path from diary_root.
        """
        rel = diary_md_relpath(entry_date)
        path = self.diary_root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        title = title or entry_date
        # Optional YAML front matter reserved for sync metadata later.
        content = (
            f"---\n"
            f"date: {entry_date}\n"
            f"title: {self._yaml_escape(title)}\n"
            f"---\n\n"
            f"{body.rstrip()}\n"
        )
        path.write_text(content, encoding="utf-8")
        logger.debug("Wrote markdown %s", path)
        return rel

    def exists(self, entry_date: str) -> bool:
        return self.path_for(entry_date).exists()

    @staticmethod
    def _strip_front_matter(text: str) -> str:
        m = FRONT_MATTER_RE.match(text)
        if m:
            return m.group(2).lstrip("\n")
        return text

    @staticmethod
    def _yaml_escape(value: str) -> str:
        if any(c in value for c in ":#{}[],&*?|>!%@`'\"\\"):
            escaped = value.replace('"', '\\"')
            return f'"{escaped}"'
        return value
