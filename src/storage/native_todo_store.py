"""Native todos — vault todos/todos.json (aligned with Android NativeTodoStore)."""
from __future__ import annotations

import json
import uuid
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


@dataclass
class NativeTodo:
    id: str
    text: str
    detail: str = ""
    done: bool = False
    kind: str = "task"  # task | note | errand | other
    due_at: str = ""
    priority: int = 0
    urgency: int = 0
    created_at: str = ""
    updated_at: str = ""


@dataclass
class TodoDocument:
    updated_at: str = ""
    items: list[NativeTodo] = field(default_factory=list)


class NativeTodoStore:
    def __init__(self, data_root: Path) -> None:
        self.file = Path(data_root) / "todos" / "todos.json"

    def list(self) -> list[NativeTodo]:
        items = sorted(self._read().items, key=lambda t: t.updated_at, reverse=True)
        return sorted(items, key=lambda t: t.done)

    def add(self, text: str) -> NativeTodo:
        now = _now()
        todo = NativeTodo(
            id=str(uuid.uuid4()),
            text=text.strip(),
            created_at=now,
            updated_at=now,
        )
        doc = self._read()
        self._write(TodoDocument(updated_at=now, items=doc.items + [todo]))
        return todo

    def upsert(self, todo: NativeTodo) -> NativeTodo:
        now = _now()
        saved = NativeTodo(**{**asdict(todo), "updated_at": now})
        doc = self._read()
        items = list(doc.items)
        idx = next((i for i, t in enumerate(items) if t.id == saved.id), -1)
        if idx >= 0:
            items[idx] = saved
        else:
            items.append(saved)
        self._write(TodoDocument(updated_at=now, items=items))
        return saved

    def set_done(self, todo_id: str, done: bool) -> Optional[NativeTodo]:
        doc = self._read()
        items = list(doc.items)
        idx = next((i for i, t in enumerate(items) if t.id == todo_id), -1)
        if idx < 0:
            return None
        now = _now()
        updated = NativeTodo(**{**asdict(items[idx]), "done": done, "updated_at": now})
        items[idx] = updated
        self._write(TodoDocument(updated_at=now, items=items))
        return updated

    def delete(self, todo_id: str) -> None:
        doc = self._read()
        now = _now()
        self._write(
            TodoDocument(
                updated_at=now,
                items=[t for t in doc.items if t.id != todo_id],
            )
        )

    def _read(self) -> TodoDocument:
        if not self.file.is_file():
            return TodoDocument()
        try:
            return self._parse(self.file.read_text(encoding="utf-8"))
        except Exception:  # noqa: BLE001
            return TodoDocument()

    def _write(self, doc: TodoDocument) -> None:
        self.file.parent.mkdir(parents=True, exist_ok=True)
        self.file.write_text(self._encode(doc), encoding="utf-8")

    @staticmethod
    def _parse(raw: str) -> TodoDocument:
        trimmed = raw.strip()
        if not trimmed:
            return TodoDocument()
        data: Any = json.loads(trimmed)
        if isinstance(data, list):
            items_raw = data
            updated_at = ""
        else:
            items_raw = data.get("items") or []
            updated_at = str(data.get("updated_at") or "")
        items: list[NativeTodo] = []
        for obj in items_raw:
            if not isinstance(obj, dict):
                continue
            items.append(
                NativeTodo(
                    id=str(obj.get("id") or uuid.uuid4()),
                    text=str(obj.get("text") or ""),
                    detail=str(obj.get("detail") or ""),
                    done=bool(obj.get("done")),
                    kind=str(obj.get("kind") or "task"),
                    due_at=str(obj.get("due_at") or "") if obj.get("due_at") is not None else "",
                    priority=int(obj.get("priority") or 0),
                    urgency=int(obj.get("urgency") or 0),
                    created_at=str(obj.get("created_at") or ""),
                    updated_at=str(obj.get("updated_at") or ""),
                )
            )
        return TodoDocument(updated_at=updated_at, items=items)

    @staticmethod
    def _encode(doc: TodoDocument) -> str:
        payload = {
            "updated_at": doc.updated_at,
            "items": [
                {
                    "id": t.id,
                    "text": t.text,
                    "detail": t.detail,
                    "done": t.done,
                    "kind": t.kind,
                    "due_at": t.due_at or None,
                    "priority": t.priority,
                    "urgency": t.urgency,
                    "created_at": t.created_at,
                    "updated_at": t.updated_at,
                }
                for t in doc.items
            ],
        }
        return json.dumps(payload, ensure_ascii=False, indent=2)
