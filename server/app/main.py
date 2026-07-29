from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any, Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from .auth import require_token
from .merge import DayContext
from .store import ServerStore

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
NAME_RE = re.compile(r"^[A-Za-z0-9._-]+$")
UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)


def _data_root() -> Path:
    raw = os.environ.get("DIARY_SYNC_DATA", "").strip()
    if raw:
        return Path(raw)
    return Path(__file__).resolve().parents[1] / "data"


store = ServerStore(_data_root())
app = FastAPI(title="灵感匣 Sync API", version="3.0.0")


class AssetMeta(BaseModel):
    name: str
    sha256: str = ""
    size: int = 0


class EntryPut(BaseModel):
    date: str = ""
    updated_at: str = ""
    created_at: str = ""
    writing_duration_sec: int = 0
    deleted: bool = False
    deleted_at: Optional[str] = None
    markdown: str = ""
    assets: list[AssetMeta] = Field(default_factory=list)


class DayPut(BaseModel):
    location: str = ""
    weather: str = ""
    temp_c: Optional[float] = None
    context_source: str = ""
    context_updated_at: str = ""
    updated_at: str = ""


def _check_date(entry_date: str) -> None:
    if not DATE_RE.match(entry_date):
        raise HTTPException(status_code=400, detail="invalid date")


def _check_id(entry_id: str) -> None:
    if not UUID_RE.match(entry_id):
        raise HTTPException(status_code=400, detail="invalid id")


def _check_name(name: str) -> None:
    if not NAME_RE.match(name) or ".." in name:
        raise HTTPException(status_code=400, detail="invalid asset name")


def _entry_json(rec) -> dict:
    return {
        "id": rec.id,
        "date": rec.date,
        "entry_date": rec.date,  # alias for older clients during transition
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


def _day_json(rec) -> dict:
    return {
        "date": rec.date,
        "location": rec.location,
        "weather": rec.weather,
        "temp_c": rec.temp_c,
        "context_source": rec.context_source,
        "context_updated_at": rec.context_updated_at,
        "updated_at": rec.updated_at,
        "revision": rec.revision,
    }


@app.get("/v1/health")
def health() -> dict:
    return {
        "ok": True,
        "revision": store.current_revision(),
        "protocol": 3,
        "product": "sparkbox",
    }


class TodosPut(BaseModel):
    """Accepts enhanced Vault todos document (string or object) in `json`."""

    updated_at: str = ""
    items_json: Any = Field(default="[]", alias="json")

    model_config = {"populate_by_name": True}


def _todos_json_string(value: Any) -> str:
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False)
    if value is None:
        return "[]"
    return str(value)


@app.get("/v1/todos", dependencies=[Depends(require_token)])
def get_todos() -> dict:
    path = store.root / "todos.json"
    if not path.is_file():
        raise HTTPException(status_code=404, detail="not found")
    raw = path.read_text(encoding="utf-8")
    # File format: {"updated_at":"...","json":"<document or legacy array>"}
    # Document may be Vault v1 {updated_at, items:[...]} or a bare array.
    try:
        obj = json.loads(raw)
        if isinstance(obj, dict) and "json" in obj:
            inner = obj["json"]
            return {
                "updated_at": obj.get("updated_at", ""),
                "json": inner if isinstance(inner, str) else json.dumps(inner, ensure_ascii=False),
            }
        return {"updated_at": "", "json": raw}
    except Exception:
        return {"updated_at": "", "json": raw}


@app.put("/v1/todos", dependencies=[Depends(require_token)])
def put_todos(body: TodosPut) -> dict:
    path = store.root / "todos.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "updated_at": body.updated_at,
        "json": _todos_json_string(body.items_json),
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return payload


@app.get("/v1/changes", dependencies=[Depends(require_token)])
def changes(since: int = 0) -> dict:
    revision, items = store.changes_since(since)
    return {"revision": revision, "changes": items}


@app.get("/v1/entries/{entry_id}", dependencies=[Depends(require_token)])
def get_entry(entry_id: str) -> dict:
    _check_id(entry_id)
    rec = store.get_entry(entry_id)
    if rec is None:
        raise HTTPException(status_code=404, detail="not found")
    return _entry_json(rec)


@app.put("/v1/entries/{entry_id}", dependencies=[Depends(require_token)])
def put_entry(entry_id: str, body: EntryPut) -> dict:
    _check_id(entry_id)
    if body.date:
        _check_date(body.date)
    if body.deleted and not body.markdown and not body.deleted_at:
        body.deleted_at = body.updated_at
    try:
        rec = store.put_entry(
            entry_id,
            date=body.date,
            markdown=body.markdown,
            updated_at=body.updated_at,
            created_at=body.created_at,
            writing_duration_sec=body.writing_duration_sec,
            deleted=body.deleted,
            deleted_at=body.deleted_at or "",
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return _entry_json(rec)


@app.get("/v1/days/{entry_date}", dependencies=[Depends(require_token)])
def get_day(entry_date: str) -> dict:
    _check_date(entry_date)
    rec = store.get_day(entry_date)
    if rec is None:
        raise HTTPException(status_code=404, detail="not found")
    return _day_json(rec)


@app.put("/v1/days/{entry_date}", dependencies=[Depends(require_token)])
def put_day(entry_date: str, body: DayPut) -> dict:
    _check_date(entry_date)
    rec = store.put_day(
        DayContext(
            date=entry_date,
            location=body.location,
            weather=body.weather,
            temp_c=body.temp_c,
            context_source=body.context_source,
            context_updated_at=body.context_updated_at,
            updated_at=body.updated_at or body.context_updated_at,
        )
    )
    return _day_json(rec)


@app.put("/v1/assets/{entry_id}/{name}", dependencies=[Depends(require_token)])
async def put_asset(
    entry_id: str,
    name: str,
    request: Request,
    x_content_sha256: Optional[str] = Header(default=None),
) -> dict:
    _check_id(entry_id)
    _check_name(name)
    data = await request.body()
    if not data:
        raise HTTPException(status_code=400, detail="empty body")
    try:
        info = store.put_asset(entry_id, name, data, expect_sha=x_content_sha256 or "")
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"name": info.name, "sha256": info.sha256, "size": info.size}


@app.get("/v1/assets/{entry_id}/{name}", dependencies=[Depends(require_token)])
def get_asset(entry_id: str, name: str) -> FileResponse:
    _check_id(entry_id)
    _check_name(name)
    path = store.get_asset(entry_id, name)
    if path is None:
        raise HTTPException(status_code=404, detail="not found")
    return FileResponse(path)
