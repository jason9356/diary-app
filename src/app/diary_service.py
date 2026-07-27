"""
Diary domain service: load / save / search / export.

Keeps SQLite index and Markdown files in sync.
"""
from __future__ import annotations

import hashlib
import logging
import re
import uuid
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Optional

from app.config import AppConfig
from storage.asset_store import AssetStore
from storage.database import Database, EntryMeta, SearchHit, utc_now_iso
from storage.export import export_zip
from storage.markdown_store import MarkdownStore
from utils.paths import diary_md_relpath

logger = logging.getLogger("diary.service")

# phone overrides desktop/manual when syncing later.
CONTEXT_RANK = {"phone": 3, "desktop": 2, "manual": 1, "": 0}


@dataclass
class DiaryEntry:
    entry_date: str
    title: str
    body: str
    word_count: int
    created_at: str
    updated_at: str
    writing_duration_sec: int
    id: str
    file_relpath: str
    image_paths: list[str]
    location: str = ""
    weather: str = ""
    temp_c: Optional[float] = None
    context_source: str = ""
    context_updated_at: str = ""


def extract_title(body: str, fallback: str) -> str:
    for line in body.splitlines():
        s = line.strip()
        if not s:
            continue
        if s.startswith("#"):
            return s.lstrip("#").strip() or fallback
        return s[:80]
    return fallback


def count_words(text: str) -> int:
    """Count CJK chars as words + latin tokens."""
    if not text.strip():
        return 0
    cjk = len(re.findall(r"[\u4e00-\u9fff]", text))
    latin = len(re.findall(r"[A-Za-z0-9]+(?:'[A-Za-z0-9]+)?", text))
    return cjk + latin


def content_hash(body: str) -> str:
    return hashlib.sha256(body.encode("utf-8")).hexdigest()


def first_image_relpath(body: str) -> Optional[str]:
    m = re.search(r"!\[[^\]]*]\(([^)]+)\)", body)
    return m.group(1).strip() if m else None


def can_overwrite_context(existing_source: str, new_source: str) -> bool:
    return CONTEXT_RANK.get(new_source, 0) >= CONTEXT_RANK.get(existing_source or "", 0)


class DiaryService:
    def __init__(self, config: AppConfig) -> None:
        self.config = config
        self.db = Database(config.db_path)
        self.md = MarkdownStore(config.diary_root)
        self.assets = AssetStore(config.assets_root, config.data_path)

    def close(self) -> None:
        self.db.close()

    def today(self) -> str:
        return date.today().isoformat()

    def _images(self, entry_date: str) -> list[str]:
        return [p.as_posix() for p in self.assets.list_for_date(entry_date)]

    def _merge_context(
        self, entry_date: str, meta: Optional[EntryMeta]
    ) -> tuple[str, str, Optional[float], str, str]:
        """Prefer DB, fall back to markdown front matter."""
        fm = self.md.read_front_matter(entry_date) if self.md.exists(entry_date) else None
        if meta and (meta.location or meta.weather or meta.temp_c is not None):
            return (
                meta.location,
                meta.weather,
                meta.temp_c,
                meta.context_source,
                meta.context_updated_at,
            )
        if fm:
            return (
                fm.location,
                fm.weather,
                fm.temp_c,
                fm.context_source,
                fm.context_updated_at,
            )
        return "", "", None, "", ""

    def _to_entry(
        self,
        *,
        entry_date: str,
        title: str,
        body: str,
        word_count: int,
        created_at: str,
        updated_at: str,
        writing_duration_sec: int,
        entry_id: str,
        file_relpath: str,
        location: str = "",
        weather: str = "",
        temp_c: Optional[float] = None,
        context_source: str = "",
        context_updated_at: str = "",
    ) -> DiaryEntry:
        return DiaryEntry(
            entry_date=entry_date,
            title=title,
            body=body,
            word_count=word_count,
            created_at=created_at,
            updated_at=updated_at,
            writing_duration_sec=writing_duration_sec,
            id=entry_id,
            file_relpath=file_relpath,
            image_paths=self._images(entry_date),
            location=location,
            weather=weather,
            temp_c=temp_c,
            context_source=context_source,
            context_updated_at=context_updated_at,
        )

    def get_or_create(self, entry_date: str) -> DiaryEntry:
        meta = self.db.get_by_date(entry_date)
        body = self.md.read(entry_date) if self.md.exists(entry_date) else ""
        loc, weather, temp, src, ctx_at = self._merge_context(entry_date, meta)
        if meta is None:
            now = utc_now_iso()
            return self._to_entry(
                entry_date=entry_date,
                title=entry_date,
                body=body,
                word_count=count_words(body),
                created_at=now,
                updated_at=now,
                writing_duration_sec=0,
                entry_id=str(uuid.uuid4()),
                file_relpath=diary_md_relpath(entry_date),
                location=loc,
                weather=weather,
                temp_c=temp,
                context_source=src,
                context_updated_at=ctx_at,
            )
        return self._to_entry(
            entry_date=meta.entry_date,
            title=meta.title,
            body=body,
            word_count=meta.word_count,
            created_at=meta.created_at,
            updated_at=meta.updated_at,
            writing_duration_sec=meta.writing_duration_sec,
            entry_id=meta.id,
            file_relpath=meta.file_relpath,
            location=loc,
            weather=weather,
            temp_c=temp,
            context_source=src,
            context_updated_at=ctx_at,
        )

    def save(
        self,
        entry_date: str,
        body: str,
        writing_duration_sec: int,
        entry_id: Optional[str] = None,
        created_at: Optional[str] = None,
        *,
        location: Optional[str] = None,
        weather: Optional[str] = None,
        temp_c: Optional[float] = None,
        context_source: Optional[str] = None,
        context_updated_at: Optional[str] = None,
        preserve_context: bool = True,
    ) -> DiaryEntry:
        existing = self.db.get_by_date(entry_date)
        now = utc_now_iso()
        title = extract_title(body, entry_date)
        words = count_words(body)

        if preserve_context and existing:
            loc = existing.location if location is None else location
            wx = existing.weather if weather is None else weather
            tmp = existing.temp_c if temp_c is None else temp_c
            src = existing.context_source if context_source is None else context_source
            ctx_at = (
                existing.context_updated_at
                if context_updated_at is None
                else context_updated_at
            )
        else:
            fm = self.md.read_front_matter(entry_date) if self.md.exists(entry_date) else None
            loc = location if location is not None else (fm.location if fm else "")
            wx = weather if weather is not None else (fm.weather if fm else "")
            tmp = temp_c if temp_c is not None else (fm.temp_c if fm else None)
            src = (
                context_source
                if context_source is not None
                else (fm.context_source if fm else "")
            )
            ctx_at = (
                context_updated_at
                if context_updated_at is not None
                else (fm.context_updated_at if fm else "")
            )

        rel = self.md.write(
            entry_date,
            body,
            title=title,
            location=loc or "",
            weather=wx or "",
            temp_c=tmp,
            context_source=src or "",
            context_updated_at=ctx_at or "",
        )
        meta = EntryMeta(
            id=entry_id or (existing.id if existing else str(uuid.uuid4())),
            entry_date=entry_date,
            title=title,
            word_count=words,
            created_at=created_at or (existing.created_at if existing else now),
            updated_at=now,
            writing_duration_sec=max(
                writing_duration_sec,
                existing.writing_duration_sec if existing else 0,
            ),
            file_relpath=rel,
            content_hash=content_hash(body),
            synced_at=existing.synced_at if existing else None,
            deleted=0,
            location=loc or "",
            weather=wx or "",
            temp_c=tmp,
            context_source=src or "",
            context_updated_at=ctx_at or "",
        )
        self.db.upsert_entry(meta, body=body)
        logger.debug("Saved %s (%d words)", entry_date, words)
        return self._to_entry(
            entry_date=meta.entry_date,
            title=meta.title,
            body=body,
            word_count=meta.word_count,
            created_at=meta.created_at,
            updated_at=meta.updated_at,
            writing_duration_sec=meta.writing_duration_sec,
            entry_id=meta.id,
            file_relpath=meta.file_relpath,
            location=meta.location,
            weather=meta.weather,
            temp_c=meta.temp_c,
            context_source=meta.context_source,
            context_updated_at=meta.context_updated_at,
        )

    def save_context(
        self,
        entry_date: str,
        *,
        location: str,
        weather: str,
        temp_c: Optional[float],
        context_source: str,
        body: Optional[str] = None,
        writing_duration_sec: int = 0,
        entry_id: Optional[str] = None,
        created_at: Optional[str] = None,
        force: bool = False,
    ) -> Optional[DiaryEntry]:
        """Update place/weather. Respects phone > desktop > manual unless force."""
        existing = self.db.get_by_date(entry_date)
        old_src = ""
        if existing:
            old_src = existing.context_source
        else:
            fm = self.md.read_front_matter(entry_date)
            old_src = fm.context_source
        if not force and not can_overwrite_context(old_src, context_source):
            logger.info(
                "Skip context write for %s (have %s, got %s)",
                entry_date,
                old_src,
                context_source,
            )
            return None
        text = body if body is not None else (
            self.md.read(entry_date) if self.md.exists(entry_date) else ""
        )
        return self.save(
            entry_date=entry_date,
            body=text,
            writing_duration_sec=writing_duration_sec
            or (existing.writing_duration_sec if existing else 0),
            entry_id=entry_id or (existing.id if existing else None),
            created_at=created_at or (existing.created_at if existing else None),
            location=location,
            weather=weather,
            temp_c=temp_c,
            context_source=context_source,
            context_updated_at=utc_now_iso(),
            preserve_context=False,
        )

    def dates_with_content(
        self, year: Optional[int] = None, month: Optional[int] = None
    ) -> set[str]:
        return set(self.db.list_dates_with_entries(year=year, month=month))

    def timeline(
        self, year: Optional[int] = None, month: Optional[int] = None
    ) -> list[DiaryEntry]:
        result: list[DiaryEntry] = []
        for meta in self.db.list_entries(year=year, month=month):
            body = self.md.read(meta.entry_date)
            loc, weather, temp, src, ctx_at = self._merge_context(meta.entry_date, meta)
            result.append(
                self._to_entry(
                    entry_date=meta.entry_date,
                    title=meta.title,
                    body=body,
                    word_count=meta.word_count,
                    created_at=meta.created_at,
                    updated_at=meta.updated_at,
                    writing_duration_sec=meta.writing_duration_sec,
                    entry_id=meta.id,
                    file_relpath=meta.file_relpath,
                    location=loc,
                    weather=weather,
                    temp_c=temp,
                    context_source=src,
                    context_updated_at=ctx_at,
                )
            )
        return result

    def search(self, query: str) -> list[SearchHit]:
        return self.db.search(query)

    def save_dropped_image(self, entry_date: str, source: Path) -> str:
        return self.assets.save_image(entry_date, source)

    def resolve_asset(self, rel: str) -> Path:
        return self.assets.absolute(rel)

    def list_image_rels(self, entry_date: str) -> list[str]:
        """Relative paths from data root for the day's assets."""
        return [
            f"assets/{entry_date}/{p.name}"
            for p in self.assets.list_for_date(entry_date)
        ]

    def export(self, dest: Optional[Path] = None) -> Path:
        return export_zip(self.config.data_path, dest)

    def available_years(self) -> list[int]:
        years = self.db.available_years()
        current = datetime.now().year
        if current not in years:
            years.insert(0, current)
        return years or [current]

    def summary_text(self, body: str, limit: int = 120) -> str:
        plain = re.sub(r"!\[[^\]]*]\([^)]+\)", "", body)
        plain = re.sub(r"[#>*_`\-]+", " ", plain)
        plain = re.sub(r"\s+", " ", plain).strip()
        if len(plain) <= limit:
            return plain
        return plain[: limit - 1] + "…"
