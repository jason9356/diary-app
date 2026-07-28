"""
SQLite database layer: metadata index + FTS5 full-text search.

Schema v3: multiple notes per day keyed by ``id``; day context in ``days`` table.
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

SCHEMA_VERSION = 3


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
class DayMeta:
    date: str
    location: str = ""
    weather: str = ""
    temp_c: Optional[float] = None
    context_source: str = ""
    context_updated_at: str = ""
    updated_at: str = ""
    synced_at: Optional[str] = None


@dataclass
class SearchHit:
    entry_id: str
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
            row = cur.execute(
                "SELECT value FROM schema_meta WHERE key = 'version'"
            ).fetchone()
            current = int(row[0]) if row else 0

            if current < 1:
                self._create_v1_tables(cur)
                current = 1

            if current < 2:
                self._migrate_to_v2(cur)
                current = 2

            if current < 3:
                self._migrate_to_v3(cur)
                current = 3

            cur.execute(
                "INSERT INTO schema_meta(key, value) VALUES('version', ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (str(SCHEMA_VERSION),),
            )
        logger.info("Database ready → %s (v%s)", self.db_path, SCHEMA_VERSION)

    def _create_v1_tables(self, cur: sqlite3.Cursor) -> None:
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

    def _migrate_to_v3(self, cur: sqlite3.Cursor) -> None:
        """Rebuild entries without UNIQUE on entry_date; move context to days table."""
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS days (
                date TEXT PRIMARY KEY,
                location TEXT NOT NULL DEFAULT '',
                weather TEXT NOT NULL DEFAULT '',
                temp_c REAL,
                context_source TEXT NOT NULL DEFAULT '',
                context_updated_at TEXT NOT NULL DEFAULT '',
                updated_at TEXT NOT NULL DEFAULT '',
                synced_at TEXT
            )
            """
        )

        # Migrate per-day context from legacy entry columns before table rebuild.
        cols = {r[1] for r in cur.execute("PRAGMA table_info(entries)").fetchall()}
        if "location" in cols:
            try:
                rows = cur.execute(
                    """
                    SELECT entry_date, location, weather, temp_c,
                           context_source, context_updated_at, updated_at
                    FROM entries
                    WHERE deleted = 0
                      AND (location != '' OR weather != '' OR temp_c IS NOT NULL)
                    ORDER BY entry_date ASC
                    """
                ).fetchall()
            except sqlite3.OperationalError:
                rows = []
            rank = {"phone": 3, "desktop": 2, "manual": 1, "": 0}
            best: dict[str, sqlite3.Row] = {}
            for r in rows:
                d = r["entry_date"]
                prev = best.get(d)
                if prev is None:
                    best[d] = r
                    continue
                r_rank = rank.get(r["context_source"] or "", 0)
                p_rank = rank.get(prev["context_source"] or "", 0)
                if r_rank > p_rank or (
                    r_rank == p_rank
                    and (r["context_updated_at"] or "") > (prev["context_updated_at"] or "")
                ):
                    best[d] = r
            for d, r in best.items():
                cur.execute(
                    """
                    INSERT OR IGNORE INTO days(
                        date, location, weather, temp_c, context_source,
                        context_updated_at, updated_at, synced_at
                    ) VALUES (?,?,?,?,?,?,?,NULL)
                    """,
                    (
                        d,
                        r["location"] or "",
                        r["weather"] or "",
                        r["temp_c"],
                        r["context_source"] or "",
                        r["context_updated_at"] or "",
                        r["context_updated_at"] or r["updated_at"] or "",
                    ),
                )

        cur.execute(
            """
            CREATE TABLE entries_v3 (
                id TEXT PRIMARY KEY,
                entry_date TEXT NOT NULL,
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
            """
            INSERT OR IGNORE INTO entries_v3(
                id, entry_date, title, word_count, created_at, updated_at,
                writing_duration_sec, file_relpath, content_hash, synced_at, deleted
            )
            SELECT id, entry_date, title, word_count, created_at, updated_at,
                   writing_duration_sec, file_relpath, content_hash, synced_at, deleted
            FROM entries
            """
        )
        cur.execute("DROP TABLE entries")
        cur.execute("ALTER TABLE entries_v3 RENAME TO entries")
        cur.execute(
            "CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(entry_date DESC)"
        )

        cur.execute("DROP TABLE IF EXISTS entries_fts")
        cur.execute(
            """
            CREATE VIRTUAL TABLE entries_fts USING fts5(
                entry_id UNINDEXED,
                entry_date UNINDEXED,
                title,
                body,
                tokenize = 'unicode61'
            )
            """
        )

        logger.info("Migrated entries schema to v3 (multi-note + days table)")

    def upsert_entry(self, meta: EntryMeta, body: str) -> None:
        with self.cursor() as cur:
            cur.execute(
                """
                INSERT INTO entries (
                    id, entry_date, title, word_count, created_at, updated_at,
                    writing_duration_sec, file_relpath, content_hash, synced_at, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    entry_date=excluded.entry_date,
                    title=excluded.title,
                    word_count=excluded.word_count,
                    updated_at=excluded.updated_at,
                    writing_duration_sec=excluded.writing_duration_sec,
                    file_relpath=excluded.file_relpath,
                    content_hash=excluded.content_hash,
                    synced_at=excluded.synced_at,
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
            cur.execute("DELETE FROM entries_fts WHERE entry_id = ?", (meta.id,))
            cur.execute(
                """
                INSERT INTO entries_fts(entry_id, entry_date, title, body)
                VALUES (?, ?, ?, ?)
                """,
                (meta.id, meta.entry_date, meta.title, body),
            )

    def get_by_id(self, entry_id: str) -> Optional[EntryMeta]:
        cur = self._conn.execute(
            "SELECT * FROM entries WHERE id = ? AND deleted = 0",
            (entry_id,),
        )
        row = cur.fetchone()
        return self._row_to_meta(row) if row else None

    def get_by_date(self, entry_date: str) -> Optional[EntryMeta]:
        """Return the first note for a date (legacy helper)."""
        cur = self._conn.execute(
            """
            SELECT * FROM entries WHERE entry_date = ? AND deleted = 0
            ORDER BY created_at ASC LIMIT 1
            """,
            (entry_date,),
        )
        row = cur.fetchone()
        return self._row_to_meta(row) if row else None

    def list_by_date(self, entry_date: str) -> list[EntryMeta]:
        rows = self._conn.execute(
            """
            SELECT * FROM entries WHERE entry_date = ? AND deleted = 0
            ORDER BY created_at ASC
            """,
            (entry_date,),
        ).fetchall()
        return [self._row_to_meta(r) for r in rows]

    def mark_synced(self, entry_id: str, synced_at: str) -> None:
        with self.cursor() as cur:
            cur.execute(
                "UPDATE entries SET synced_at=? WHERE id=?",
                (synced_at, entry_id),
            )

    def upsert_day(self, meta: DayMeta) -> None:
        with self.cursor() as cur:
            cur.execute(
                """
                INSERT INTO days (
                    date, location, weather, temp_c, context_source,
                    context_updated_at, updated_at, synced_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(date) DO UPDATE SET
                    location=excluded.location,
                    weather=excluded.weather,
                    temp_c=excluded.temp_c,
                    context_source=excluded.context_source,
                    context_updated_at=excluded.context_updated_at,
                    updated_at=excluded.updated_at,
                    synced_at=excluded.synced_at
                """,
                (
                    meta.date,
                    meta.location,
                    meta.weather,
                    meta.temp_c,
                    meta.context_source,
                    meta.context_updated_at,
                    meta.updated_at,
                    meta.synced_at,
                ),
            )

    def get_day(self, entry_date: str) -> Optional[DayMeta]:
        row = self._conn.execute(
            "SELECT * FROM days WHERE date = ?",
            (entry_date,),
        ).fetchone()
        if row is None:
            return None
        return DayMeta(
            date=row["date"],
            location=row["location"] or "",
            weather=row["weather"] or "",
            temp_c=float(row["temp_c"]) if row["temp_c"] is not None else None,
            context_source=row["context_source"] or "",
            context_updated_at=row["context_updated_at"] or "",
            updated_at=row["updated_at"] or "",
            synced_at=row["synced_at"],
        )

    def mark_day_synced(self, entry_date: str, synced_at: str) -> None:
        with self.cursor() as cur:
            cur.execute(
                "UPDATE days SET synced_at=? WHERE date=?",
                (synced_at, entry_date),
            )

    def list_dates_with_entries(
        self,
        year: Optional[int] = None,
        month: Optional[int] = None,
    ) -> list[str]:
        sql = (
            "SELECT DISTINCT entry_date FROM entries WHERE deleted = 0 AND word_count > 0"
        )
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
        sql += " ORDER BY entry_date DESC, updated_at DESC LIMIT ?"
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
            SELECT e.id, e.entry_date, e.title, e.updated_at,
                   substr(replace(f.body, char(10), ' '), 1, 120) AS snippet
            FROM entries_fts f
            JOIN entries e ON e.id = f.entry_id
            WHERE e.deleted = 0
              AND (f.title LIKE ? OR f.body LIKE ?)
            ORDER BY e.entry_date DESC, e.updated_at DESC
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
                        SELECT e.id, e.entry_date, e.title, e.updated_at,
                               snippet(entries_fts, 3, '«', '»', '…', 24) AS snippet
                        FROM entries_fts
                        JOIN entries e ON e.id = entries_fts.entry_id
                        WHERE entries_fts MATCH ? AND e.deleted = 0
                        ORDER BY e.entry_date DESC, e.updated_at DESC
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
                    entry_id=r["id"],
                    entry_date=r["entry_date"],
                    title=r["title"] or r["entry_date"],
                    snippet=snippet,
                    updated_at=r["updated_at"],
                )
            )
        return hits

    def all_file_relpaths(self) -> list[tuple[str, str]]:
        """Return (entry_id, file_relpath) for export."""
        rows = self._conn.execute(
            "SELECT id, file_relpath FROM entries WHERE deleted = 0"
        ).fetchall()
        return [(r["id"], r["file_relpath"]) for r in rows]

    def available_years(self) -> list[int]:
        rows = self._conn.execute(
            """
            SELECT DISTINCT substr(entry_date, 1, 4) AS y
            FROM entries WHERE deleted = 0 AND word_count > 0
            ORDER BY y DESC
            """
        ).fetchall()
        return [int(r["y"]) for r in rows]

    def reindex_from_files(
        self,
        notes: list[tuple[str, str, str, EntryMeta]],
    ) -> None:
        """Bulk re-index after file migration. Each item: (id, date, body, meta)."""
        with self.cursor() as cur:
            cur.execute("DELETE FROM entries_fts")
            for _eid, _date, body, meta in notes:
                cur.execute(
                    """
                    INSERT INTO entries (
                        id, entry_date, title, word_count, created_at, updated_at,
                        writing_duration_sec, file_relpath, content_hash, synced_at, deleted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        entry_date=excluded.entry_date,
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
                cur.execute(
                    """
                    INSERT INTO entries_fts(entry_id, entry_date, title, body)
                    VALUES (?, ?, ?, ?)
                    """,
                    (meta.id, meta.entry_date, meta.title, body),
                )

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
