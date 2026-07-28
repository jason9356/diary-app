from __future__ import annotations

import json
import re
import shutil
import sqlite3
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from .merge import (
    DayContext,
    merge_day_context,
    merge_entries,
    parse_markdown,
    render_markdown,
    sha256_bytes,
    sha256_text,
)

DATE_FILE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}\.md$")
UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)


@dataclass
class AssetInfo:
    name: str
    sha256: str
    size: int


@dataclass
class EntryRecord:
    id: str
    date: str
    updated_at: str
    created_at: str
    writing_duration_sec: int
    deleted: bool
    deleted_at: str
    content_hash: str
    revision: int
    markdown: str
    assets: list[AssetInfo]


@dataclass
class DayRecord:
    date: str
    location: str
    weather: str
    temp_c: Optional[float]
    context_source: str
    context_updated_at: str
    updated_at: str
    revision: int


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
        self.migrate_v1_layout()

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
                """
            )
            # Detect legacy v1 table (entry_date PK) before creating v2 indexes.
            row = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='entries'"
            ).fetchone()
            if row is not None:
                cols = {r[1] for r in conn.execute("PRAGMA table_info(entries)").fetchall()}
                if "entry_date" in cols and "date" not in cols:
                    conn.executescript(
                        """
                        CREATE TABLE entries_v2 (
                            id TEXT PRIMARY KEY,
                            date TEXT NOT NULL,
                            updated_at TEXT NOT NULL DEFAULT '',
                            created_at TEXT NOT NULL DEFAULT '',
                            writing_duration_sec INTEGER NOT NULL DEFAULT 0,
                            deleted INTEGER NOT NULL DEFAULT 0,
                            deleted_at TEXT NOT NULL DEFAULT '',
                            content_hash TEXT NOT NULL DEFAULT '',
                            revision INTEGER NOT NULL
                        );
                        INSERT OR IGNORE INTO entries_v2(
                            id, date, updated_at, created_at, writing_duration_sec,
                            deleted, deleted_at, content_hash, revision
                        )
                        SELECT id, entry_date, updated_at, created_at, writing_duration_sec,
                               deleted, deleted_at, content_hash, revision
                        FROM entries;
                        DROP TABLE entries;
                        ALTER TABLE entries_v2 RENAME TO entries;
                        """
                    )

            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS entries (
                    id TEXT PRIMARY KEY,
                    date TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL DEFAULT '',
                    writing_duration_sec INTEGER NOT NULL DEFAULT 0,
                    deleted INTEGER NOT NULL DEFAULT 0,
                    deleted_at TEXT NOT NULL DEFAULT '',
                    content_hash TEXT NOT NULL DEFAULT '',
                    revision INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_entries_revision ON entries(revision);
                CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(date);
                CREATE TABLE IF NOT EXISTS days (
                    date TEXT PRIMARY KEY,
                    location TEXT NOT NULL DEFAULT '',
                    weather TEXT NOT NULL DEFAULT '',
                    temp_c REAL,
                    context_source TEXT NOT NULL DEFAULT '',
                    context_updated_at TEXT NOT NULL DEFAULT '',
                    updated_at TEXT NOT NULL DEFAULT '',
                    revision INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_days_revision ON days(revision);
                """
            )
            row = conn.execute("SELECT value FROM meta WHERE key='revision'").fetchone()
            if row is None:
                conn.execute("INSERT INTO meta(key, value) VALUES('revision', '0')")
            conn.execute(
                "INSERT OR IGNORE INTO meta(key, value) VALUES('protocol', '2')"
            )

    def current_revision(self) -> int:
        with self._connect() as conn:
            row = conn.execute("SELECT value FROM meta WHERE key='revision'").fetchone()
            return int(row["value"]) if row else 0

    def _bump_revision(self, conn: sqlite3.Connection) -> int:
        cur = int(conn.execute("SELECT value FROM meta WHERE key='revision'").fetchone()["value"])
        cur += 1
        conn.execute("UPDATE meta SET value=? WHERE key='revision'", (str(cur),))
        return cur

    def _md_path(self, entry_id: str, date: str) -> Path:
        y, m, _ = date.split("-")
        return self.diary / y / m / f"{entry_id}.md"

    def _day_path(self, date: str) -> Path:
        y, m, _ = date.split("-")
        return self.diary / y / m / f"{date}.day.json"

    def _asset_dir(self, entry_id: str) -> Path:
        return self.assets / entry_id

    def list_assets(self, entry_id: str) -> list[AssetInfo]:
        d = self._asset_dir(entry_id)
        if not d.is_dir():
            return []
        out: list[AssetInfo] = []
        for p in sorted(d.iterdir()):
            if not p.is_file():
                continue
            data = p.read_bytes()
            out.append(AssetInfo(name=p.name, sha256=sha256_bytes(data), size=len(data)))
        return out

    def migrate_v1_layout(self) -> None:
        """Convert date-named markdown + assets into id-keyed layout."""
        if not self.diary.exists():
            return
        for path in sorted(self.diary.rglob("*.md")):
            if not DATE_FILE_RE.match(path.name):
                continue
            entry_date = path.stem
            text = path.read_text(encoding="utf-8")
            body, fm = parse_markdown(text)
            entry_id = fm.id or str(uuid.uuid4())
            fm.id = entry_id
            fm.date = fm.date or entry_date
            # Strip legacy weather into day.json
            # Re-parse raw for weather keys from original text
            day_ctx = self._extract_legacy_context(text, entry_date)
            new_body = body.replace(f"assets/{entry_date}/", f"assets/{entry_id}/")
            new_md = render_markdown(new_body, fm)
            dest = self._md_path(entry_id, fm.date)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(new_md, encoding="utf-8")
            old_assets = self.assets / entry_date
            if old_assets.is_dir():
                new_assets = self._asset_dir(entry_id)
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
            if day_ctx and (day_ctx.location or day_ctx.weather or day_ctx.temp_c is not None):
                existing = self.get_day(entry_date)
                merged = merge_day_context(
                    DayContext(
                        date=existing.date,
                        location=existing.location,
                        weather=existing.weather,
                        temp_c=existing.temp_c,
                        context_source=existing.context_source,
                        context_updated_at=existing.context_updated_at,
                        updated_at=existing.updated_at,
                    )
                    if existing
                    else None,
                    day_ctx,
                )
                self.put_day(merged, bump=True)
            # Index entry
            with self._connect() as conn:
                rev = self._bump_revision(conn)
                conn.execute(
                    """
                    INSERT INTO entries(
                        id, date, updated_at, created_at, writing_duration_sec,
                        deleted, deleted_at, content_hash, revision
                    ) VALUES (?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(id) DO UPDATE SET
                        date=excluded.date,
                        updated_at=excluded.updated_at,
                        created_at=excluded.created_at,
                        writing_duration_sec=excluded.writing_duration_sec,
                        content_hash=excluded.content_hash,
                        revision=excluded.revision
                    """,
                    (
                        entry_id,
                        fm.date,
                        fm.updated_at,
                        fm.created_at,
                        fm.writing_duration_sec,
                        0,
                        "",
                        sha256_text(new_body),
                        rev,
                    ),
                )
            path.unlink(missing_ok=True)

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
            context_updated_at=data.get("context_updated_at", "") or data.get("updated_at", ""),
            updated_at=data.get("context_updated_at", "") or data.get("updated_at", ""),
        )

    def get_entry(self, entry_id: str) -> Optional[EntryRecord]:
        with self._connect() as conn:
            row = conn.execute("SELECT * FROM entries WHERE id=?", (entry_id,)).fetchone()
        if row is None:
            # Try filesystem orphan
            for path in self.diary.rglob(f"{entry_id}.md"):
                text = path.read_text(encoding="utf-8")
                body, fm = parse_markdown(text)
                return EntryRecord(
                    id=entry_id,
                    date=fm.date or path.parent.name,
                    updated_at=fm.updated_at,
                    created_at=fm.created_at,
                    writing_duration_sec=fm.writing_duration_sec,
                    deleted=False,
                    deleted_at="",
                    content_hash=sha256_text(body),
                    revision=0,
                    markdown=text,
                    assets=self.list_assets(entry_id),
                )
            return None
        path = self._md_path(entry_id, row["date"])
        markdown = ""
        if not row["deleted"] and path.exists():
            markdown = path.read_text(encoding="utf-8")
        return EntryRecord(
            id=row["id"],
            date=row["date"],
            updated_at=row["updated_at"],
            created_at=row["created_at"],
            writing_duration_sec=int(row["writing_duration_sec"]),
            deleted=bool(row["deleted"]),
            deleted_at=row["deleted_at"] or "",
            content_hash=row["content_hash"],
            revision=int(row["revision"]),
            markdown=markdown,
            assets=[] if row["deleted"] else self.list_assets(entry_id),
        )

    def changes_since(self, since: int) -> tuple[int, list[dict]]:
        rev = self.current_revision()
        changes: list[dict] = []
        with self._connect() as conn:
            for r in conn.execute(
                """
                SELECT id, date, updated_at, deleted, revision
                FROM entries WHERE revision > ?
                ORDER BY revision ASC
                """,
                (since,),
            ).fetchall():
                changes.append(
                    {
                        "kind": "entry",
                        "id": r["id"],
                        "date": r["date"],
                        "updated_at": r["updated_at"],
                        "deleted": bool(r["deleted"]),
                        "revision": int(r["revision"]),
                    }
                )
            for r in conn.execute(
                """
                SELECT date, updated_at, revision FROM days WHERE revision > ?
                ORDER BY revision ASC
                """,
                (since,),
            ).fetchall():
                changes.append(
                    {
                        "kind": "day",
                        "date": r["date"],
                        "updated_at": r["updated_at"],
                        "revision": int(r["revision"]),
                    }
                )
        changes.sort(key=lambda c: c["revision"])
        return rev, changes

    def put_entry(
        self,
        entry_id: str,
        *,
        date: str,
        markdown: str,
        updated_at: str,
        created_at: str,
        writing_duration_sec: int,
        deleted: bool,
        deleted_at: str,
    ) -> EntryRecord:
        if not UUID_RE.match(entry_id):
            raise ValueError("invalid id")
        existing = self.get_entry(entry_id)
        path = self._md_path(entry_id, (existing.date if existing else date) or date)
        server_md: Optional[str] = None
        if existing and not existing.deleted and path.exists():
            server_md = path.read_text(encoding="utf-8")
        elif existing and not existing.deleted:
            # date may have changed — try existing date path
            alt = self._md_path(entry_id, existing.date)
            if alt.exists():
                server_md = alt.read_text(encoding="utf-8")
                path = alt

        merged_md, fm, is_deleted, del_at = merge_entries(
            server_md=server_md,
            incoming_md=markdown,
            entry_id=entry_id,
            server_updated_at=existing.updated_at if existing else "",
            incoming_updated_at=updated_at,
            server_created_at=existing.created_at if existing else "",
            incoming_created_at=created_at,
            server_duration=existing.writing_duration_sec if existing else 0,
            incoming_duration=writing_duration_sec,
            server_deleted=existing.deleted if existing else False,
            incoming_deleted=deleted,
            server_deleted_at=existing.deleted_at if existing else "",
            incoming_deleted_at=deleted_at,
            fallback_date=date or (existing.date if existing else ""),
        )
        body, fm = parse_markdown(merged_md)
        fm.id = entry_id
        if not fm.date:
            fm.date = date or (existing.date if existing else "")
        final_md = render_markdown(body, fm)
        content_hash = sha256_text(body)
        final_path = self._md_path(entry_id, fm.date)

        with self._connect() as conn:
            rev = self._bump_revision(conn)
            if is_deleted:
                if path.exists():
                    path.unlink()
                if final_path.exists() and final_path != path:
                    final_path.unlink()
                conn.execute(
                    """
                    INSERT INTO entries(
                        id, date, updated_at, created_at, writing_duration_sec,
                        deleted, deleted_at, content_hash, revision
                    ) VALUES (?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(id) DO UPDATE SET
                        date=excluded.date,
                        updated_at=excluded.updated_at,
                        created_at=excluded.created_at,
                        writing_duration_sec=excluded.writing_duration_sec,
                        deleted=1,
                        deleted_at=excluded.deleted_at,
                        content_hash=excluded.content_hash,
                        revision=excluded.revision
                    """,
                    (
                        entry_id,
                        fm.date,
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
                if path.exists() and path != final_path:
                    path.unlink()
                final_path.parent.mkdir(parents=True, exist_ok=True)
                final_path.write_text(final_md, encoding="utf-8")
                conn.execute(
                    """
                    INSERT INTO entries(
                        id, date, updated_at, created_at, writing_duration_sec,
                        deleted, deleted_at, content_hash, revision
                    ) VALUES (?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(id) DO UPDATE SET
                        date=excluded.date,
                        updated_at=excluded.updated_at,
                        created_at=excluded.created_at,
                        writing_duration_sec=excluded.writing_duration_sec,
                        deleted=0,
                        deleted_at='',
                        content_hash=excluded.content_hash,
                        revision=excluded.revision
                    """,
                    (
                        entry_id,
                        fm.date,
                        fm.updated_at or updated_at,
                        fm.created_at or created_at,
                        fm.writing_duration_sec,
                        0,
                        "",
                        content_hash,
                        rev,
                    ),
                )

        record = self.get_entry(entry_id)
        assert record is not None
        return record

    def get_day(self, date: str) -> Optional[DayRecord]:
        with self._connect() as conn:
            row = conn.execute("SELECT * FROM days WHERE date=?", (date,)).fetchone()
        path = self._day_path(date)
        if row is None and not path.exists():
            return None
        data: dict = {}
        if path.exists():
            data = json.loads(path.read_text(encoding="utf-8"))
        if row is not None:
            return DayRecord(
                date=date,
                location=data.get("location", row["location"] or ""),
                weather=data.get("weather", row["weather"] or ""),
                temp_c=data.get("temp_c", row["temp_c"]),
                context_source=data.get("context_source", row["context_source"] or ""),
                context_updated_at=data.get(
                    "context_updated_at", row["context_updated_at"] or ""
                ),
                updated_at=data.get("updated_at", row["updated_at"] or ""),
                revision=int(row["revision"]),
            )
        return DayRecord(
            date=date,
            location=data.get("location", ""),
            weather=data.get("weather", ""),
            temp_c=data.get("temp_c"),
            context_source=data.get("context_source", ""),
            context_updated_at=data.get("context_updated_at", ""),
            updated_at=data.get("updated_at", ""),
            revision=0,
        )

    def put_day(self, incoming: DayContext, *, bump: bool = True) -> DayRecord:
        existing = self.get_day(incoming.date)
        server_ctx = None
        if existing:
            server_ctx = DayContext(
                date=existing.date,
                location=existing.location,
                weather=existing.weather,
                temp_c=existing.temp_c,
                context_source=existing.context_source,
                context_updated_at=existing.context_updated_at,
                updated_at=existing.updated_at,
            )
        merged = merge_day_context(server_ctx, incoming)
        path = self._day_path(merged.date)
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "date": merged.date,
            "location": merged.location,
            "weather": merged.weather,
            "temp_c": merged.temp_c,
            "context_source": merged.context_source,
            "context_updated_at": merged.context_updated_at,
            "updated_at": merged.updated_at,
        }
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        with self._connect() as conn:
            rev = self._bump_revision(conn) if bump else (existing.revision if existing else 0)
            if not bump and existing is None:
                rev = self._bump_revision(conn)
            conn.execute(
                """
                INSERT INTO days(
                    date, location, weather, temp_c, context_source,
                    context_updated_at, updated_at, revision
                ) VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(date) DO UPDATE SET
                    location=excluded.location,
                    weather=excluded.weather,
                    temp_c=excluded.temp_c,
                    context_source=excluded.context_source,
                    context_updated_at=excluded.context_updated_at,
                    updated_at=excluded.updated_at,
                    revision=excluded.revision
                """,
                (
                    merged.date,
                    merged.location,
                    merged.weather,
                    merged.temp_c,
                    merged.context_source,
                    merged.context_updated_at,
                    merged.updated_at,
                    rev,
                ),
            )
        record = self.get_day(merged.date)
        assert record is not None
        return record

    def put_asset(self, entry_id: str, name: str, data: bytes, expect_sha: str = "") -> AssetInfo:
        digest = sha256_bytes(data)
        if expect_sha and expect_sha != digest:
            raise ValueError("sha256 mismatch")
        d = self._asset_dir(entry_id)
        d.mkdir(parents=True, exist_ok=True)
        path = d / name
        if path.exists() and sha256_bytes(path.read_bytes()) == digest:
            return AssetInfo(name=name, sha256=digest, size=len(data))
        path.write_bytes(data)
        return AssetInfo(name=name, sha256=digest, size=len(data))

    def get_asset(self, entry_id: str, name: str) -> Optional[Path]:
        path = self._asset_dir(entry_id) / name
        return path if path.is_file() else None
