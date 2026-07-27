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
        self.md = MarkdownStore(config.diary_root)
        self.assets = AssetStore(config.assets_root, config.data_path)

    def close(self) -> None:
        self.db.close()

    def today(self) -> str:
        return date.today().isoformat()

    def get_or_create(self, entry_date: str) -> DiaryEntry:
        meta = self.db.get_by_date(entry_date)
        body = self.md.read(entry_date) if self.md.exists(entry_date) else ""
        if meta is None:
            now = utc_now_iso()
            return DiaryEntry(
                entry_date=entry_date,
                title=entry_date,
                body=body,
                word_count=count_words(body),
                created_at=now,
                updated_at=now,
                writing_duration_sec=0,
                id=str(uuid.uuid4()),
                file_relpath=diary_md_relpath(entry_date),
                image_paths=[p.as_posix() for p in self.assets.list_for_date(entry_date)],
            )
        return DiaryEntry(
            entry_date=meta.entry_date,
            title=meta.title,
            body=body,
            word_count=meta.word_count,
            created_at=meta.created_at,
            updated_at=meta.updated_at,
            writing_duration_sec=meta.writing_duration_sec,
            id=meta.id,
            file_relpath=meta.file_relpath,
            image_paths=[p.as_posix() for p in self.assets.list_for_date(entry_date)],
        )

    def save(
        self,
        entry_date: str,
        body: str,
        writing_duration_sec: int,
        entry_id: Optional[str] = None,
        created_at: Optional[str] = None,
    ) -> DiaryEntry:
        existing = self.db.get_by_date(entry_date)
        now = utc_now_iso()
        title = extract_title(body, entry_date)
        words = count_words(body)
        rel = self.md.write(entry_date, body, title=title)
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
        )
        self.db.upsert_entry(meta, body=body)
        logger.debug("Saved %s (%d words)", entry_date, words)
        return DiaryEntry(
            entry_date=meta.entry_date,
            title=meta.title,
            body=body,
            word_count=meta.word_count,
            created_at=meta.created_at,
            updated_at=meta.updated_at,
            writing_duration_sec=meta.writing_duration_sec,
            id=meta.id,
            file_relpath=meta.file_relpath,
            image_paths=[p.as_posix() for p in self.assets.list_for_date(entry_date)],
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
            result.append(
                DiaryEntry(
                    entry_date=meta.entry_date,
                    title=meta.title,
                    body=body,
                    word_count=meta.word_count,
                    created_at=meta.created_at,
                    updated_at=meta.updated_at,
                    writing_duration_sec=meta.writing_duration_sec,
                    id=meta.id,
                    file_relpath=meta.file_relpath,
                    image_paths=[
                        p.as_posix() for p in self.assets.list_for_date(meta.entry_date)
                    ],
                )
            )
        return result

    def search(self, query: str) -> list[SearchHit]:
        return self.db.search(query)

    def save_dropped_image(self, entry_date: str, source: Path) -> str:
        return self.assets.save_image(entry_date, source)

    def resolve_asset(self, rel: str) -> Path:
        return self.assets.absolute(rel)

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
