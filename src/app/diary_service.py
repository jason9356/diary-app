"""
Diary domain service: load / save / search / export.

Keeps SQLite index, Markdown files, and day JSON in sync.
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
from storage.database import Database, DayMeta, EntryMeta, SearchHit, utc_now_iso
from storage.day_store import DayContext, DayStore, can_overwrite_context
from storage.export import export_zip
from storage.markdown_store import MarkdownStore
from utils.paths import diary_md_relpath_for_id

logger = logging.getLogger("diary.service")


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


class DiaryService:
    def __init__(self, config: AppConfig) -> None:
        self.config = config
        self.db = Database(config.db_path)
        self.md = MarkdownStore(config.diary_root, config.assets_root)
        self.days = DayStore(config.diary_root)
        self.assets = AssetStore(config.assets_root, config.data_path)
        self._run_file_migration()

    def close(self) -> None:
        self.db.close()

    def reindex_from_disk(self) -> None:
        """Rebuild SQLite index from vault markdown after cloud pull."""
        self._reindex_from_disk()

    def _run_file_migration(self) -> None:
        """One-time v1 → v2 file layout migration + reindex."""
        migrated = self.md.migrate_v1_layout(self.days)
        if migrated:
            logger.info("Migrated %d v1 markdown files to v2 layout", migrated)
        self._reindex_from_disk()

    def _reindex_from_disk(self) -> None:
        """Ensure DB reflects all id-keyed markdown files on disk."""
        notes: list[tuple[str, str, str, EntryMeta]] = []
        for entry_id, entry_date in self.md.list_note_ids():
            body = self.md.read(entry_id, entry_date)
            fm = self.md.read_front_matter(entry_id, entry_date)
            title = fm.title or extract_title(body, entry_date)
            existing = self.db.get_by_id(entry_id)
            meta = EntryMeta(
                id=entry_id,
                entry_date=entry_date,
                title=title,
                word_count=count_words(body),
                created_at=fm.created_at or (existing.created_at if existing else utc_now_iso()),
                updated_at=fm.updated_at or (existing.updated_at if existing else utc_now_iso()),
                writing_duration_sec=fm.writing_duration_sec
                or (existing.writing_duration_sec if existing else 0),
                file_relpath=diary_md_relpath_for_id(entry_date, entry_id),
                content_hash=content_hash(body),
                synced_at=existing.synced_at if existing else None,
                deleted=0,
            )
            notes.append((entry_id, entry_date, body, meta))
        if notes:
            self.db.reindex_from_files(notes)

        # Sync day JSON into DB index.
        if self.config.diary_root.exists():
            for path in self.config.diary_root.rglob("*.day.json"):
                entry_date = path.name.removesuffix(".day.json")
                ctx = self.days.read(entry_date)
                if ctx is None:
                    continue
                self.db.upsert_day(
                    DayMeta(
                        date=ctx.date,
                        location=ctx.location,
                        weather=ctx.weather,
                        temp_c=ctx.temp_c,
                        context_source=ctx.context_source,
                        context_updated_at=ctx.context_updated_at,
                        updated_at=ctx.updated_at,
                    )
                )

    def today(self) -> str:
        return date.today().isoformat()

    def _day_context(self, entry_date: str) -> DayContext:
        ctx = self.days.read(entry_date)
        if ctx is not None:
            return ctx
        db_day = self.db.get_day(entry_date)
        if db_day is not None:
            return DayContext(
                date=db_day.date,
                location=db_day.location,
                weather=db_day.weather,
                temp_c=db_day.temp_c,
                context_source=db_day.context_source,
                context_updated_at=db_day.context_updated_at,
                updated_at=db_day.updated_at,
            )
        return DayContext(date=entry_date)

    def _images(self, entry_id: str) -> list[str]:
        return [p.as_posix() for p in self.assets.list_for_entry(entry_id)]

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
        day_ctx: Optional[DayContext] = None,
    ) -> DiaryEntry:
        ctx = day_ctx or self._day_context(entry_date)
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
            image_paths=self._images(entry_id),
            location=ctx.location,
            weather=ctx.weather,
            temp_c=ctx.temp_c,
            context_source=ctx.context_source,
            context_updated_at=ctx.context_updated_at,
        )

    def create_entry(self, entry_date: str) -> DiaryEntry:
        """Create a new empty note for the given calendar day."""
        now = utc_now_iso()
        entry_id = str(uuid.uuid4())
        rel = self.md.write(
            entry_id,
            entry_date,
            "",
            title=entry_date,
            created_at=now,
            updated_at=now,
        )
        meta = EntryMeta(
            id=entry_id,
            entry_date=entry_date,
            title=entry_date,
            word_count=0,
            created_at=now,
            updated_at=now,
            writing_duration_sec=0,
            file_relpath=rel,
            content_hash=content_hash(""),
            deleted=0,
        )
        self.db.upsert_entry(meta, body="")
        return self._to_entry(
            entry_date=entry_date,
            title=entry_date,
            body="",
            word_count=0,
            created_at=now,
            updated_at=now,
            writing_duration_sec=0,
            entry_id=entry_id,
            file_relpath=rel,
        )

    def get_by_id(self, entry_id: str) -> Optional[DiaryEntry]:
        meta = self.db.get_by_id(entry_id)
        if meta is None:
            return None
        body = self.md.read(entry_id, meta.entry_date)
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
        )

    def list_for_date(self, entry_date: str) -> list[DiaryEntry]:
        result: list[DiaryEntry] = []
        day_ctx = self._day_context(entry_date)
        for meta in self.db.list_by_date(entry_date):
            body = self.md.read(meta.id, meta.entry_date)
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
                    day_ctx=day_ctx,
                )
            )
        return result

    def get_or_create(self, entry_date: str) -> DiaryEntry:
        """Open the first note of the day, or return an unsaved stub if none exist."""
        notes = self.list_for_date(entry_date)
        if notes:
            return notes[0]
        now = utc_now_iso()
        entry_id = str(uuid.uuid4())
        return self._to_entry(
            entry_date=entry_date,
            title=entry_date,
            body="",
            word_count=0,
            created_at=now,
            updated_at=now,
            writing_duration_sec=0,
            entry_id=entry_id,
            file_relpath=diary_md_relpath_for_id(entry_date, entry_id),
        )

    def save(
        self,
        entry_date: str,
        body: str,
        writing_duration_sec: int,
        entry_id: Optional[str] = None,
        created_at: Optional[str] = None,
    ) -> DiaryEntry:
        existing = self.db.get_by_id(entry_id) if entry_id else None
        now = utc_now_iso()
        title = extract_title(body, entry_date)
        words = count_words(body)

        eid = entry_id or (existing.id if existing else str(uuid.uuid4()))
        created = created_at or (existing.created_at if existing else now)
        duration = max(
            writing_duration_sec,
            existing.writing_duration_sec if existing else 0,
        )
        new_hash = content_hash(body)
        if existing and new_hash == existing.content_hash:
            updated = existing.updated_at or now
        else:
            updated = now
        rel = self.md.write(
            eid,
            entry_date,
            body,
            title=title,
            created_at=created,
            updated_at=updated,
            writing_duration_sec=duration,
        )
        meta = EntryMeta(
            id=eid,
            entry_date=entry_date,
            title=title,
            word_count=words,
            created_at=created,
            updated_at=updated,
            writing_duration_sec=duration,
            file_relpath=rel,
            content_hash=new_hash,
            synced_at=existing.synced_at if existing else None,
            deleted=0,
        )
        self.db.upsert_entry(meta, body=body)
        logger.debug("Saved note %s on %s (%d words)", eid, entry_date, words)
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
        )

    def get_day_context(self, entry_date: str) -> DayContext:
        return self._day_context(entry_date)

    def set_day_context(
        self,
        entry_date: str,
        *,
        location: str,
        weather: str,
        temp_c: Optional[float],
        context_source: str,
        force: bool = False,
    ) -> Optional[DayContext]:
        """Update day-level place/weather. Respects phone > desktop > manual unless force."""
        existing = self._day_context(entry_date)
        if not force and not can_overwrite_context(existing.context_source, context_source):
            logger.info(
                "Skip context write for %s (have %s, got %s)",
                entry_date,
                existing.context_source,
                context_source,
            )
            return None
        now = utc_now_iso()
        incoming = DayContext(
            date=entry_date,
            location=location,
            weather=weather,
            temp_c=temp_c,
            context_source=context_source,
            context_updated_at=now,
            updated_at=now,
        )
        merged = self.days.merge_write(incoming)
        self.db.upsert_day(
            DayMeta(
                date=merged.date,
                location=merged.location,
                weather=merged.weather,
                temp_c=merged.temp_c,
                context_source=merged.context_source,
                context_updated_at=merged.context_updated_at,
                updated_at=merged.updated_at,
            )
        )
        return merged

    def save_context(
        self,
        entry_date: str,
        *,
        location: str,
        weather: str,
        temp_c: Optional[float],
        context_source: str,
        force: bool = False,
    ) -> Optional[DayContext]:
        return self.set_day_context(
            entry_date,
            location=location,
            weather=weather,
            temp_c=temp_c,
            context_source=context_source,
            force=force,
        )

    def dates_with_content(
        self, year: Optional[int] = None, month: Optional[int] = None
    ) -> set[str]:
        return set(self.db.list_dates_with_entries(year=year, month=month))

    def timeline(
        self, year: Optional[int] = None, month: Optional[int] = None
    ) -> list[DiaryEntry]:
        result: list[DiaryEntry] = []
        day_cache: dict[str, DayContext] = {}
        for meta in self.db.list_entries(year=year, month=month):
            if meta.entry_date not in day_cache:
                day_cache[meta.entry_date] = self._day_context(meta.entry_date)
            body = self.md.read(meta.id, meta.entry_date)
            ctx = day_cache[meta.entry_date]
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
                    day_ctx=ctx,
                )
            )
        return result

    def search(self, query: str) -> list[SearchHit]:
        return self.db.search(query)

    def save_dropped_image(self, entry_id: str, source: Path) -> str:
        return self.assets.save_image(entry_id, source)

    def resolve_asset(self, rel: str) -> Path:
        return self.assets.absolute(rel)

    def list_image_rels(self, entry_id: str) -> list[str]:
        """Relative paths from data root for the note's assets."""
        return [
            f"assets/{entry_id}/{p.name}"
            for p in self.assets.list_for_entry(entry_id)
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
