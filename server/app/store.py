from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from .merge import merge_entries, parse_markdown, render_markdown, sha256_bytes, sha256_text


@dataclass
class AssetInfo:
    name: str
    sha256: str
    size: int


@dataclass
class EntryRecord:
    entry_date: str
    id: str
    updated_at: str
    created_at: str
    writing_duration_sec: int
    deleted: bool
    deleted_at: str
    content_hash: str
    revision: int
    markdown: str
    assets: list[AssetInfo]


class ServerStore:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.diary = root / "diary"
        self.assets = root / "assets"
        self.db_path = root / "sync.db"
        self.root.mkdir(parents=True, exist_ok=True)
        self.diary.mkdir(parents=True, exist_ok=True)
        self.assets.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS meta (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS entries (
                    entry_date TEXT PRIMARY KEY,
                    id TEXT NOT NULL UNIQUE,
                    updated_at TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL DEFAULT '',
                    writing_duration_sec INTEGER NOT NULL DEFAULT 0,
                    deleted INTEGER NOT NULL DEFAULT 0,
                    deleted_at TEXT NOT NULL DEFAULT '',
                    content_hash TEXT NOT NULL DEFAULT '',
                    revision INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_entries_revision ON entries(revision);
                """
            )
            row = conn.execute("SELECT value FROM meta WHERE key='revision'").fetchone()
            if row is None:
                conn.execute("INSERT INTO meta(key, value) VALUES('revision', '0')")

    def current_revision(self) -> int:
        with self._connect() as conn:
            row = conn.execute("SELECT value FROM meta WHERE key='revision'").fetchone()
            return int(row["value"]) if row else 0

    def _bump_revision(self, conn: sqlite3.Connection) -> int:
        cur = int(conn.execute("SELECT value FROM meta WHERE key='revision'").fetchone()["value"])
        cur += 1
        conn.execute("UPDATE meta SET value=? WHERE key='revision'", (str(cur),))
        return cur

    def _md_path(self, entry_date: str) -> Path:
        y, m, _ = entry_date.split("-")
        return self.diary / y / m / f"{entry_date}.md"

    def _asset_dir(self, entry_date: str) -> Path:
        return self.assets / entry_date

    def list_assets(self, entry_date: str) -> list[AssetInfo]:
        d = self._asset_dir(entry_date)
        if not d.is_dir():
            return []
        out: list[AssetInfo] = []
        for p in sorted(d.iterdir()):
            if not p.is_file():
                continue
            data = p.read_bytes()
            out.append(AssetInfo(name=p.name, sha256=sha256_bytes(data), size=len(data)))
        return out

    def get_entry(self, entry_date: str) -> Optional[EntryRecord]:
        with self._connect() as conn:
            row = conn.execute(
                "SELECT * FROM entries WHERE entry_date=?", (entry_date,)
            ).fetchone()
        if row is None:
            return None
        path = self._md_path(entry_date)
        markdown = ""
        if not row["deleted"] and path.exists():
            markdown = path.read_text(encoding="utf-8")
        return EntryRecord(
            entry_date=row["entry_date"],
            id=row["id"],
            updated_at=row["updated_at"],
            created_at=row["created_at"],
            writing_duration_sec=int(row["writing_duration_sec"]),
            deleted=bool(row["deleted"]),
            deleted_at=row["deleted_at"] or "",
            content_hash=row["content_hash"],
            revision=int(row["revision"]),
            markdown=markdown,
            assets=[] if row["deleted"] else self.list_assets(entry_date),
        )

    def changes_since(self, since: int) -> tuple[int, list[dict]]:
        rev = self.current_revision()
        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT entry_date, id, updated_at, deleted, revision
                FROM entries WHERE revision > ?
                ORDER BY revision ASC
                """,
                (since,),
            ).fetchall()
        changes = [
            {
                "entry_date": r["entry_date"],
                "id": r["id"],
                "updated_at": r["updated_at"],
                "deleted": bool(r["deleted"]),
                "revision": int(r["revision"]),
            }
            for r in rows
        ]
        return rev, changes

    def put_entry(
        self,
        entry_date: str,
        *,
        markdown: str,
        entry_id: str,
        updated_at: str,
        created_at: str,
        writing_duration_sec: int,
        deleted: bool,
        deleted_at: str,
    ) -> EntryRecord:
        existing = self.get_entry(entry_date)
        path = self._md_path(entry_date)
        server_md: Optional[str] = None
        if existing and not existing.deleted and path.exists():
            server_md = path.read_text(encoding="utf-8")

        _, in_fm = parse_markdown(markdown)
        incoming_id = entry_id or in_fm.id

        merged_md, fm, is_deleted, del_at = merge_entries(
            server_md=server_md,
            incoming_md=markdown,
            server_id=existing.id if existing else "",
            incoming_id=incoming_id,
            server_updated_at=existing.updated_at if existing else "",
            incoming_updated_at=updated_at or in_fm.updated_at,
            server_created_at=existing.created_at if existing else "",
            incoming_created_at=created_at or in_fm.created_at,
            server_duration=existing.writing_duration_sec if existing else 0,
            incoming_duration=writing_duration_sec or in_fm.writing_duration_sec,
            server_deleted=existing.deleted if existing else False,
            incoming_deleted=deleted,
            server_deleted_at=existing.deleted_at if existing else "",
            incoming_deleted_at=deleted_at,
        )
        if not fm.date:
            fm.date = entry_date
        body, _ = parse_markdown(merged_md)
        fm = parse_markdown(merged_md)[1]
        if not fm.date:
            fm.date = entry_date
        content_hash = sha256_text(body)
        winner_id = fm.id or incoming_id
        fm.id = winner_id
        final_md = render_markdown(body, fm)

        with self._connect() as conn:
            rev = self._bump_revision(conn)
            if is_deleted:
                if path.exists():
                    path.unlink()
                conn.execute(
                    """
                    INSERT INTO entries(
                        entry_date, id, updated_at, created_at, writing_duration_sec,
                        deleted, deleted_at, content_hash, revision
                    ) VALUES (?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(entry_date) DO UPDATE SET
                        id=excluded.id,
                        updated_at=excluded.updated_at,
                        created_at=excluded.created_at,
                        writing_duration_sec=excluded.writing_duration_sec,
                        deleted=1,
                        deleted_at=excluded.deleted_at,
                        content_hash=excluded.content_hash,
                        revision=excluded.revision
                    """,
                    (
                        entry_date,
                        winner_id,
                        fm.updated_at or updated_at or del_at,
                        fm.created_at or created_at,
                        fm.writing_duration_sec,
                        1,
                        del_at,
                        content_hash,
                        rev,
                    ),
                )
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(final_md, encoding="utf-8")
                conn.execute(
                    """
                    INSERT INTO entries(
                        entry_date, id, updated_at, created_at, writing_duration_sec,
                        deleted, deleted_at, content_hash, revision
                    ) VALUES (?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(entry_date) DO UPDATE SET
                        id=excluded.id,
                        updated_at=excluded.updated_at,
                        created_at=excluded.created_at,
                        writing_duration_sec=excluded.writing_duration_sec,
                        deleted=0,
                        deleted_at='',
                        content_hash=excluded.content_hash,
                        revision=excluded.revision
                    """,
                    (
                        entry_date,
                        winner_id,
                        fm.updated_at or updated_at,
                        fm.created_at or created_at,
                        fm.writing_duration_sec,
                        0,
                        "",
                        content_hash,
                        rev,
                    ),
                )

        record = self.get_entry(entry_date)
        assert record is not None
        return record

    def put_asset(self, entry_date: str, name: str, data: bytes, expect_sha: str = "") -> AssetInfo:
        digest = sha256_bytes(data)
        if expect_sha and expect_sha != digest:
            raise ValueError("sha256 mismatch")
        d = self._asset_dir(entry_date)
        d.mkdir(parents=True, exist_ok=True)
        path = d / name
        if path.exists() and sha256_bytes(path.read_bytes()) == digest:
            return AssetInfo(name=name, sha256=digest, size=len(data))
        path.write_bytes(data)
        return AssetInfo(name=name, sha256=digest, size=len(data))

    def get_asset(self, entry_date: str, name: str) -> Optional[Path]:
        path = self._asset_dir(entry_date) / name
        return path if path.is_file() else None
