from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from .auth import require_token
from .store import ServerStore

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
NAME_RE = re.compile(r"^[A-Za-z0-9._-]+$")


def _data_root() -> Path:
    raw = os.environ.get("DIARY_SYNC_DATA", "").strip()
    if raw:
        return Path(raw)
    return Path(__file__).resolve().parents[1] / "data"


store = ServerStore(_data_root())
app = FastAPI(title="Diary Sync API", version="1.0.0")


class AssetMeta(BaseModel):
    name: str
    sha256: str = ""
    size: int = 0


class EntryPut(BaseModel):
    id: str = ""
    updated_at: str = ""
    created_at: str = ""
    writing_duration_sec: int = 0
    deleted: bool = False
    deleted_at: Optional[str] = None
    markdown: str = ""
    assets: list[AssetMeta] = Field(default_factory=list)


def _check_date(entry_date: str) -> None:
    if not DATE_RE.match(entry_date):
        raise HTTPException(status_code=400, detail="invalid date")


def _check_name(name: str) -> None:
    if not NAME_RE.match(name) or ".." in name:
        raise HTTPException(status_code=400, detail="invalid asset name")


def _entry_json(rec) -> dict:
    return {
        "entry_date": rec.entry_date,
        "id": rec.id,
        "updated_at": rec.updated_at,
        "created_at": rec.created_at,
        "deleted": rec.deleted,
        "deleted_at": rec.deleted_at or None,
        "writing_duration_sec": rec.writing_duration_sec,
        "markdown": rec.markdown,
        "assets": [
            {"name": a.name, "sha256": a.sha256, "size": a.size} for a in rec.assets
        ],
        "revision": rec.revision,
        "content_hash": rec.content_hash,
    }


@app.get("/v1/health")
def health() -> dict:
    return {"ok": True, "revision": store.current_revision()}


@app.get("/v1/changes", dependencies=[Depends(require_token)])
def changes(since: int = 0) -> dict:
    revision, items = store.changes_since(since)
    return {"revision": revision, "changes": items}


@app.get("/v1/entries/{entry_date}", dependencies=[Depends(require_token)])
def get_entry(entry_date: str) -> dict:
    _check_date(entry_date)
    rec = store.get_entry(entry_date)
    if rec is None:
        raise HTTPException(status_code=404, detail="not found")
    return _entry_json(rec)


@app.put("/v1/entries/{entry_date}", dependencies=[Depends(require_token)])
def put_entry(entry_date: str, body: EntryPut) -> dict:
    _check_date(entry_date)
    if body.deleted and not body.markdown and not body.deleted_at:
        body.deleted_at = body.updated_at
    rec = store.put_entry(
        entry_date,
        markdown=body.markdown,
        entry_id=body.id,
        updated_at=body.updated_at,
        created_at=body.created_at,
        writing_duration_sec=body.writing_duration_sec,
        deleted=body.deleted,
        deleted_at=body.deleted_at or "",
    )
    return _entry_json(rec)


@app.put("/v1/assets/{entry_date}/{name}", dependencies=[Depends(require_token)])
async def put_asset(
    entry_date: str,
    name: str,
    request: Request,
    x_content_sha256: Optional[str] = Header(default=None),
) -> dict:
    _check_date(entry_date)
    _check_name(name)
    data = await request.body()
    if not data:
        raise HTTPException(status_code=400, detail="empty body")
    try:
        info = store.put_asset(entry_date, name, data, expect_sha=x_content_sha256 or "")
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"name": info.name, "sha256": info.sha256, "size": info.size}


@app.get("/v1/assets/{entry_date}/{name}", dependencies=[Depends(require_token)])
def get_asset(entry_date: str, name: str) -> FileResponse:
    _check_date(entry_date)
    _check_name(name)
    path = store.get_asset(entry_date, name)
    if path is None:
        raise HTTPException(status_code=404, detail="not found")
    return FileResponse(path)
