"""
SQLite database layer: metadata index + FTS5 full-text search.

Schema is designed so a future sync service can map rows by UUID
(``id``) and detect changes via ``content_hash`` / ``updated_at``.
"""
from __future__ import annotations

import logging
import sqlite3
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterator, Optional

logger = logging.getLogger("diary.storage.db")

SCHEMA_VERSION = 1


@dataclass
class EntryMeta:
    id: str
    entry_date: str
    title: str
    word_count: int
    created_at: str
    updated_at: str
    writing_duration_sec: int
    file_relpath: str
    content_hash: str
    synced_at: Optional[str] = None
    deleted: int = 0


@dataclass
class SearchHit:
    entry_date: str
    title: str
    snippet: str
    updated_at: str


class Database:
    """Thin SQLite wrapper for diary metadata and search."""

    def __init__(self, db_path: Path) -> None:
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA foreign_keys = ON")
        self._conn.execute("PRAGMA journal_mode = WAL")
        self._migrate()

    def close(self) -> None:
        self._conn.close()

    @contextmanager
    def cursor(self) -> Iterator[sqlite3.Cursor]:
        cur = self._conn.cursor()
        try:
            yield cur
            self._conn.commit()
        except Exception:
            self._conn.rollback()
            raise
        finally:
            cur.close()

    def _migrate(self) -> None:
        with self.cursor() as cur:
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """
            )
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS entries (
                    id TEXT PRIMARY KEY,
                    entry_date TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL DEFAULT '',
                    word_count INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    writing_duration_sec INTEGER NOT NULL DEFAULT 0,
                    file_relpath TEXT NOT NULL,
                    content_hash TEXT NOT NULL DEFAULT '',
                    synced_at TEXT,
                    deleted INTEGER NOT NULL DEFAULT 0
                )
                """
            )
            cur.execute(
                "CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(entry_date DESC)"
            )
            # FTS5 for full-text search (title + body).
            cur.execute(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING fts5(
                    entry_id UNINDEXED,
                    entry_date UNINDEXED,
                    title,
                    body,
                    tokenize = 'unicode61'
                )
                """
            )
            cur.execute(
                "INSERT OR IGNORE INTO schema_meta(key, value) VALUES('version', ?)",
                (str(SCHEMA_VERSION),),
            )
        logger.info("Database ready → %s", self.db_path)

    def upsert_entry(
        self,
        meta: EntryMeta,
        body: str,
    ) -> None:
        with self.cursor() as cur:
            cur.execute(
                """
                INSERT INTO entries (
                    id, entry_date, title, word_count, created_at, updated_at,
                    writing_duration_sec, file_relpath, content_hash, synced_at, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(entry_date) DO UPDATE SET
                    title=excluded.title,
                    word_count=excluded.word_count,
                    updated_at=excluded.updated_at,
                    writing_duration_sec=excluded.writing_duration_sec,
                    file_relpath=excluded.file_relpath,
                    content_hash=excluded.content_hash,
                    deleted=excluded.deleted
                """,
                (
                    meta.id,
                    meta.entry_date,
                    meta.title,
                    meta.word_count,
                    meta.created_at,
                    meta.updated_at,
                    meta.writing_duration_sec,
                    meta.file_relpath,
                    meta.content_hash,
                    meta.synced_at,
                    meta.deleted,
                ),
            )
            # Keep FTS in sync by entry_date (stable key for local v1).
            cur.execute("DELETE FROM entries_fts WHERE entry_date = ?", (meta.entry_date,))
            cur.execute(
                """
                INSERT INTO entries_fts(entry_id, entry_date, title, body)
                VALUES (?, ?, ?, ?)
                """,
                (meta.id, meta.entry_date, meta.title, body),
            )

    def get_by_date(self, entry_date: str) -> Optional[EntryMeta]:
        cur = self._conn.execute(
            "SELECT * FROM entries WHERE entry_date = ? AND deleted = 0",
            (entry_date,),
        )
        row = cur.fetchone()
        return self._row_to_meta(row) if row else None

    def list_dates_with_entries(
        self,
        year: Optional[int] = None,
        month: Optional[int] = None,
    ) -> list[str]:
        sql = "SELECT entry_date FROM entries WHERE deleted = 0 AND word_count > 0"
        params: list[object] = []
        if year is not None:
            sql += " AND entry_date LIKE ?"
            prefix = f"{year:04d}-"
            if month is not None:
                prefix = f"{year:04d}-{month:02d}-"
            params.append(prefix + "%")
        sql += " ORDER BY entry_date DESC"
        rows = self._conn.execute(sql, params).fetchall()
        return [r["entry_date"] for r in rows]

    def list_entries(
        self,
        year: Optional[int] = None,
        month: Optional[int] = None,
        limit: int = 500,
    ) -> list[EntryMeta]:
        sql = "SELECT * FROM entries WHERE deleted = 0 AND word_count > 0"
        params: list[object] = []
        if year is not None:
            sql += " AND entry_date LIKE ?"
            prefix = f"{year:04d}-"
            if month is not None:
                prefix = f"{year:04d}-{month:02d}-"
            params.append(prefix + "%")
        sql += " ORDER BY entry_date DESC LIMIT ?"
        params.append(limit)
        rows = self._conn.execute(sql, params).fetchall()
        return [self._row_to_meta(r) for r in rows]

    def search(self, query: str, limit: int = 100) -> list[SearchHit]:
        q = query.strip()
        if not q:
            return []
        # Prefer LIKE for CJK / mixed text (FTS unicode61 is weak on Chinese).
        like = f"%{q}%"
        rows = self._conn.execute(
            """
            SELECT e.entry_date, e.title, e.updated_at,
                   substr(replace(f.body, char(10), ' '), 1, 120) AS snippet
            FROM entries_fts f
            JOIN entries e ON e.entry_date = f.entry_date
            WHERE e.deleted = 0
              AND (f.title LIKE ? OR f.body LIKE ?)
            ORDER BY e.entry_date DESC
            LIMIT ?
            """,
            (like, like, limit),
        ).fetchall()

        # Optional FTS pass for latin tokens if LIKE found nothing.
        if not rows:
            safe = q.replace('"', " ").replace("'", " ")
            tokens = [t for t in safe.split() if t]
            if tokens:
                match = " ".join(f'"{t}"*' for t in tokens)
                try:
                    rows = self._conn.execute(
                        """
                        SELECT e.entry_date, e.title, e.updated_at,
                               snippet(entries_fts, 3, '«', '»', '…', 24) AS snippet
                        FROM entries_fts
                        JOIN entries e ON e.entry_date = entries_fts.entry_date
                        WHERE entries_fts MATCH ? AND e.deleted = 0
                        ORDER BY e.entry_date DESC
                        LIMIT ?
                        """,
                        (match, limit),
                    ).fetchall()
                except sqlite3.OperationalError as exc:
                    logger.warning("FTS search failed: %s", exc)

        hits: list[SearchHit] = []
        for r in rows:
            snippet = r["snippet"] or ""
            # Soft-highlight keyword markers for UI.
            if q and q in snippet and "«" not in snippet:
                snippet = snippet.replace(q, f"«{q}»")
            hits.append(
                SearchHit(
                    entry_date=r["entry_date"],
                    title=r["title"] or r["entry_date"],
                    snippet=snippet,
                    updated_at=r["updated_at"],
                )
            )
        return hits

    def all_file_relpaths(self) -> list[tuple[str, str]]:
        """Return (entry_date, file_relpath) for export."""
        rows = self._conn.execute(
            "SELECT entry_date, file_relpath FROM entries WHERE deleted = 0"
        ).fetchall()
        return [(r["entry_date"], r["file_relpath"]) for r in rows]

    def available_years(self) -> list[int]:
        rows = self._conn.execute(
            """
            SELECT DISTINCT substr(entry_date, 1, 4) AS y
            FROM entries WHERE deleted = 0 AND word_count > 0
            ORDER BY y DESC
            """
        ).fetchall()
        return [int(r["y"]) for r in rows]

    @staticmethod
    def _row_to_meta(row: sqlite3.Row) -> EntryMeta:
        return EntryMeta(
            id=row["id"],
            entry_date=row["entry_date"],
            title=row["title"],
            word_count=row["word_count"],
            created_at=row["created_at"],
            updated_at=row["updated_at"],
            writing_duration_sec=row["writing_duration_sec"],
            file_relpath=row["file_relpath"],
            content_hash=row["content_hash"],
            synced_at=row["synced_at"],
            deleted=row["deleted"],
        )


def utc_now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")
