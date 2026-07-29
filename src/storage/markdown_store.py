"""
Markdown file store on disk.

Layout (v2): ``diary_root/YYYY/MM/<id>.md``
Day context: ``diary_root/YYYY/MM/<date>.day.json``
"""
from __future__ import annotations

import logging
import re
import shutil
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from storage.day_store import DayContext, DayStore
from utils.paths import diary_md_relpath, diary_md_relpath_for_id

logger = logging.getLogger("diary.storage.markdown")

FRONT_MATTER_RE = re.compile(
    r"^---\s*\n(.*?)\n---\s*\n(.*)$",
    re.DOTALL,
)
DATE_FILE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}\.md$")
UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)


@dataclass
class EntryFrontMatter:
    date: str = ""
    title: str = ""
    id: str = ""
    created_at: str = ""
    updated_at: str = ""
    writing_duration_sec: int = 0
    tags: list[str] | None = None
    pinned: bool = False
    # Legacy v1 fields (read during migration only)
    location: str = ""
    weather: str = ""
    temp_c: Optional[float] = None
    context_source: str = ""
    context_updated_at: str = ""


class MarkdownStore:
    def __init__(self, diary_root: Path, assets_root: Path | None = None) -> None:
        self.diary_root = diary_root
        self.assets_root = assets_root
        self.diary_root.mkdir(parents=True, exist_ok=True)

    def path_for(self, entry_id: str, entry_date: str) -> Path:
        return self.diary_root / diary_md_relpath_for_id(entry_date, entry_id)

    def read(self, entry_id: str, entry_date: str) -> str:
        path = self.path_for(entry_id, entry_date)
        if not path.exists():
            return ""
        text = path.read_text(encoding="utf-8")
        body, _ = self._parse(text)
        return body

    def read_raw(self, entry_id: str, entry_date: str) -> str:
        path = self.path_for(entry_id, entry_date)
        if not path.exists():
            return ""
        return path.read_text(encoding="utf-8")

    def read_front_matter(self, entry_id: str, entry_date: str) -> EntryFrontMatter:
        path = self.path_for(entry_id, entry_date)
        if not path.exists():
            return EntryFrontMatter(date=entry_date, id=entry_id)
        text = path.read_text(encoding="utf-8")
        _, fm = self._parse(text)
        if not fm.date:
            fm.date = entry_date
        if not fm.id:
            fm.id = entry_id
        return fm

    def write(
        self,
        entry_id: str,
        entry_date: str,
        body: str,
        title: str = "",
        *,
        created_at: str = "",
        updated_at: str = "",
        writing_duration_sec: int = 0,
        tags: list[str] | None = None,
        pinned: bool = False,
    ) -> str:
        """Write markdown file. Returns relative path from diary_root."""
        rel = diary_md_relpath_for_id(entry_date, entry_id)
        path = self.diary_root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        title = title or entry_date
        lines = [
            "---",
            f"date: {entry_date}",
            f"title: {self._yaml_escape(title)}",
            f"id: {entry_id}",
        ]
        if created_at:
            lines.append(f"created_at: {created_at}")
        if updated_at:
            lines.append(f"updated_at: {updated_at}")
        if writing_duration_sec:
            lines.append(f"writing_duration_sec: {writing_duration_sec}")
        if tags:
            joined = ", ".join(self._yaml_escape(t) for t in tags)
            lines.append(f"tags: [{joined}]")
        if pinned:
            lines.append("pinned: true")
        lines.append("---")
        content = "\n".join(lines) + "\n\n" + body.rstrip() + "\n"
        path.write_text(content, encoding="utf-8")
        logger.debug("Wrote markdown %s", path)
        return rel

    def write_raw(self, entry_id: str, entry_date: str, markdown: str) -> str:
        rel = diary_md_relpath_for_id(entry_date, entry_id)
        path = self.diary_root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(markdown, encoding="utf-8")
        return rel

    def exists(self, entry_id: str, entry_date: str) -> bool:
        return self.path_for(entry_id, entry_date).exists()

    def list_note_ids(self) -> list[tuple[str, str]]:
        """Scan ``*.md`` files whose stem is a UUID. Returns (id, date) pairs."""
        found: list[tuple[str, str]] = []
        if not self.diary_root.exists():
            return found
        for path in sorted(self.diary_root.rglob("*.md")):
            if DATE_FILE_RE.match(path.name):
                continue
            stem = path.stem
            if not UUID_RE.match(stem):
                continue
            text = path.read_text(encoding="utf-8")
            _, fm = self._parse(text)
            entry_date = fm.date or ""
            if not entry_date:
                continue
            found.append((stem, entry_date))
        return found

    def migrate_v1_layout(self, day_store: DayStore) -> int:
        """
        Convert ``YYYY-MM-DD.md`` files and date-keyed assets to id-keyed layout.
        Returns count of migrated notes.
        """
        if not self.diary_root.exists():
            return 0
        count = 0
        for path in sorted(self.diary_root.rglob("*.md")):
            if not DATE_FILE_RE.match(path.name):
                continue
            entry_date = path.stem
            text = path.read_text(encoding="utf-8")
            body, fm = self._parse(text)
            entry_id = fm.id or str(uuid.uuid4())
            fm.id = entry_id
            fm.date = fm.date or entry_date

            day_ctx = self._extract_legacy_context(text, entry_date)
            if day_ctx and (
                day_ctx.location or day_ctx.weather or day_ctx.temp_c is not None
            ):
                day_store.merge_write(day_ctx)

            new_body = body.replace(f"assets/{entry_date}/", f"assets/{entry_id}/")
            dest = self.path_for(entry_id, fm.date)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(
                self._render_v2(new_body, fm),
                encoding="utf-8",
            )

            if self.assets_root is not None:
                old_assets = self.assets_root / entry_date
                if old_assets.is_dir():
                    new_assets = self.assets_root / entry_id
                    new_assets.mkdir(parents=True, exist_ok=True)
                    for ap in old_assets.iterdir():
                        if ap.is_file():
                            target = new_assets / ap.name
                            if not target.exists():
                                shutil.move(str(ap), str(target))
                    try:
                        old_assets.rmdir()
                    except OSError:
                        pass

            path.unlink(missing_ok=True)
            count += 1
            logger.info("Migrated v1 note %s → %s", entry_date, entry_id)
        return count

    def _render_v2(self, body: str, fm: EntryFrontMatter) -> str:
        title = fm.title or fm.date or "untitled"
        lines = [
            "---",
            f"date: {fm.date}",
            f"title: {self._yaml_escape(title)}",
            f"id: {fm.id}",
        ]
        if fm.created_at:
            lines.append(f"created_at: {fm.created_at}")
        if fm.updated_at:
            lines.append(f"updated_at: {fm.updated_at}")
        if fm.writing_duration_sec:
            lines.append(f"writing_duration_sec: {fm.writing_duration_sec}")
        if fm.tags:
            joined = ", ".join(self._yaml_escape(t) for t in fm.tags)
            lines.append(f"tags: [{joined}]")
        if fm.pinned:
            lines.append("pinned: true")
        lines.append("---")
        return "\n".join(lines) + "\n\n" + body.rstrip() + "\n"

    def _extract_legacy_context(self, text: str, entry_date: str) -> Optional[DayContext]:
        m = re.match(r"^---\s*\n(.*?)\n---", text, re.DOTALL)
        if not m:
            return None
        data: dict[str, str] = {}
        for line in m.group(1).splitlines():
            if ":" not in line:
                continue
            k, v = line.split(":", 1)
            data[k.strip()] = v.strip().strip('"')
        if not any(k in data for k in ("location", "weather", "temp_c", "context_source")):
            return None
        temp = None
        if data.get("temp_c"):
            try:
                temp = float(data["temp_c"])
            except ValueError:
                temp = None
        return DayContext(
            date=entry_date,
            location=data.get("location", ""),
            weather=data.get("weather", ""),
            temp_c=temp,
            context_source=data.get("context_source", "") or "desktop",
            context_updated_at=data.get("context_updated_at", "")
            or data.get("updated_at", ""),
            updated_at=data.get("context_updated_at", "") or data.get("updated_at", ""),
        )

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
        tags = self._parse_tags(data.get("tags", ""))
        fm = EntryFrontMatter(
            date=data.get("date", ""),
            title=data.get("title", ""),
            id=data.get("id", ""),
            created_at=data.get("created_at", ""),
            updated_at=data.get("updated_at", ""),
            writing_duration_sec=duration,
            tags=tags or None,
            pinned=str(data.get("pinned", "")).lower() == "true",
            location=data.get("location", ""),
            weather=data.get("weather", ""),
            temp_c=temp,
            context_source=data.get("context_source", ""),
            context_updated_at=data.get("context_updated_at", ""),
        )
        return body, fm

    @staticmethod
    def _parse_tags(raw: str) -> list[str]:
        s = (raw or "").strip()
        if not s:
            return []
        if s.startswith("[") and s.endswith("]"):
            s = s[1:-1]
        out: list[str] = []
        for part in s.split(","):
            t = part.strip().strip('"').strip("'")
            if t:
                out.append(t)
        return out

    @staticmethod
    def _yaml_escape(value: str) -> str:
        if any(c in value for c in ":#{}[],&*?|>!%@`'\"\\"):
            escaped = value.replace('"', '\\"')
            return f'"{escaped}"'
        return value

    # Legacy v1 helpers (date-keyed paths) — used only during migration detection
    def legacy_path_for(self, entry_date: str) -> Path:
        return self.diary_root / diary_md_relpath(entry_date)

    def legacy_exists(self, entry_date: str) -> bool:
        return self.legacy_path_for(entry_date).exists()
