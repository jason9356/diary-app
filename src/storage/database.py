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

SCHEMA_VERSION = 2


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
    location: str = ""
    weather: str = ""
    temp_c: Optional[float] = None
    context_source: str = ""
    context_updated_at: str = ""


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
                    deleted INTEGER NOT NULL DEFAULT 0,
                    location TEXT NOT NULL DEFAULT '',
                    weather TEXT NOT NULL DEFAULT '',
                    temp_c REAL,
                    context_source TEXT NOT NULL DEFAULT '',
                    context_updated_at TEXT NOT NULL DEFAULT ''
                )
                """
            )
            cur.execute(
                "CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(entry_date DESC)"
            )
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
            row = cur.execute(
                "SELECT value FROM schema_meta WHERE key = 'version'"
            ).fetchone()
            current = int(row[0]) if row else 0
            if current < 2:
                self._migrate_to_v2(cur)
                current = 2
            cur.execute(
                "INSERT INTO schema_meta(key, value) VALUES('version', ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (str(SCHEMA_VERSION),),
            )
        logger.info("Database ready → %s (v%s)", self.db_path, SCHEMA_VERSION)

    def _migrate_to_v2(self, cur: sqlite3.Cursor) -> None:
        cols = {r[1] for r in cur.execute("PRAGMA table_info(entries)").fetchall()}
        additions = [
            ("location", "TEXT NOT NULL DEFAULT ''"),
            ("weather", "TEXT NOT NULL DEFAULT ''"),
            ("temp_c", "REAL"),
            ("context_source", "TEXT NOT NULL DEFAULT ''"),
            ("context_updated_at", "TEXT NOT NULL DEFAULT ''"),
        ]
        for name, decl in additions:
            if name not in cols:
                cur.execute(f"ALTER TABLE entries ADD COLUMN {name} {decl}")
        logger.info("Migrated entries schema to v2 (context fields)")

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
                    writing_duration_sec, file_relpath, content_hash, synced_at, deleted,
                    location, weather, temp_c, context_source, context_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(entry_date) DO UPDATE SET
                    title=excluded.title,
                    word_count=excluded.word_count,
                    updated_at=excluded.updated_at,
                    writing_duration_sec=excluded.writing_duration_sec,
                    file_relpath=excluded.file_relpath,
                    content_hash=excluded.content_hash,
                    deleted=excluded.deleted,
                    location=excluded.location,
                    weather=excluded.weather,
                    temp_c=excluded.temp_c,
                    context_source=excluded.context_source,
                    context_updated_at=excluded.context_updated_at
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
                    meta.location or "",
                    meta.weather or "",
                    meta.temp_c,
                    meta.context_source or "",
                    meta.context_updated_at or "",
                ),
            )
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
        keys = row.keys()
        temp = row["temp_c"] if "temp_c" in keys else None
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
            location=row["location"] if "location" in keys and row["location"] else "",
            weather=row["weather"] if "weather" in keys and row["weather"] else "",
            temp_c=float(temp) if temp is not None else None,
            context_source=(
                row["context_source"] if "context_source" in keys and row["context_source"] else ""
            ),
            context_updated_at=(
                row["context_updated_at"]
                if "context_updated_at" in keys and row["context_updated_at"]
                else ""
            ),
        )


def utc_now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")
