"""
Markdown file store on disk.

Layout: ``diary_root/YYYY/MM/YYYY-MM-DD.md``
Files are plain UTF-8 Markdown so they remain human-readable
and sync-friendly for future clients.
"""
from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from utils.paths import diary_md_relpath

logger = logging.getLogger("diary.storage.markdown")

FRONT_MATTER_RE = re.compile(
    r"^---\s*\n(.*?)\n---\s*\n(.*)$",
    re.DOTALL,
)


@dataclass
class EntryFrontMatter:
    date: str = ""
    title: str = ""
    id: str = ""
    created_at: str = ""
    updated_at: str = ""
    location: str = ""
    weather: str = ""
    temp_c: Optional[float] = None
    context_source: str = ""  # phone | desktop | manual
    context_updated_at: str = ""
    writing_duration_sec: int = 0


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
        body, _ = self._parse(text)
        return body

    def read_raw(self, entry_date: str) -> str:
        path = self.path_for(entry_date)
        if not path.exists():
            return ""
        return path.read_text(encoding="utf-8")

    def read_front_matter(self, entry_date: str) -> EntryFrontMatter:
        path = self.path_for(entry_date)
        if not path.exists():
            return EntryFrontMatter(date=entry_date)
        text = path.read_text(encoding="utf-8")
        _, fm = self._parse(text)
        if not fm.date:
            fm.date = entry_date
        return fm

    def write(
        self,
        entry_date: str,
        body: str,
        title: str = "",
        *,
        entry_id: str = "",
        created_at: str = "",
        updated_at: str = "",
        location: str = "",
        weather: str = "",
        temp_c: Optional[float] = None,
        context_source: str = "",
        context_updated_at: str = "",
        writing_duration_sec: int = 0,
    ) -> str:
        """Write markdown file. Returns relative path from diary_root."""
        rel = diary_md_relpath(entry_date)
        path = self.diary_root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        title = title or entry_date
        lines = [
            "---",
            f"date: {entry_date}",
            f"title: {self._yaml_escape(title)}",
        ]
        if entry_id:
            lines.append(f"id: {entry_id}")
        if created_at:
            lines.append(f"created_at: {created_at}")
        if updated_at:
            lines.append(f"updated_at: {updated_at}")
        if location:
            lines.append(f"location: {self._yaml_escape(location)}")
        if weather:
            lines.append(f"weather: {self._yaml_escape(weather)}")
        if temp_c is not None:
            lines.append(f"temp_c: {temp_c:g}")
        if context_source:
            lines.append(f"context_source: {context_source}")
        if context_updated_at:
            lines.append(f"context_updated_at: {context_updated_at}")
        if writing_duration_sec:
            lines.append(f"writing_duration_sec: {writing_duration_sec}")
        lines.append("---")
        content = "\n".join(lines) + "\n\n" + body.rstrip() + "\n"
        path.write_text(content, encoding="utf-8")
        logger.debug("Wrote markdown %s", path)
        return rel

    def write_raw(self, entry_date: str, markdown: str) -> str:
        rel = diary_md_relpath(entry_date)
        path = self.diary_root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(markdown, encoding="utf-8")
        return rel

    def exists(self, entry_date: str) -> bool:
        return self.path_for(entry_date).exists()

    def _parse(self, text: str) -> tuple[str, EntryFrontMatter]:
        m = FRONT_MATTER_RE.match(text)
        if not m:
            return text, EntryFrontMatter()
        raw, body = m.group(1), m.group(2).lstrip("\n")
        data: dict[str, str] = {}
        for line in raw.splitlines():
            if ":" not in line:
                continue
            key, val = line.split(":", 1)
            data[key.strip()] = val.strip().strip('"')
        temp: Optional[float] = None
        if data.get("temp_c"):
            try:
                temp = float(data["temp_c"])
            except ValueError:
                temp = None
        duration = 0
        if data.get("writing_duration_sec"):
            try:
                duration = int(float(data["writing_duration_sec"]))
            except ValueError:
                duration = 0
        fm = EntryFrontMatter(
            date=data.get("date", ""),
            title=data.get("title", ""),
            id=data.get("id", ""),
            created_at=data.get("created_at", ""),
            updated_at=data.get("updated_at", ""),
            location=data.get("location", ""),
            weather=data.get("weather", ""),
            temp_c=temp,
            context_source=data.get("context_source", ""),
            context_updated_at=data.get("context_updated_at", ""),
            writing_duration_sec=duration,
        )
        return body, fm

    @staticmethod
    def _yaml_escape(value: str) -> str:
        if any(c in value for c in ":#{}[],&*?|>!%@`'\"\\"):
            escaped = value.replace('"', '\\"')
            return f'"{escaped}"'
        return value
