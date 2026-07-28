from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from typing import Optional

FRONT_MATTER_RE = re.compile(
    r"^---\s*\n(.*?)\n---\s*\n?(.*)$",
    re.DOTALL,
)

CONTEXT_RANK = {"phone": 3, "desktop": 2, "manual": 1, "": 0}


@dataclass
class FrontMatter:
    date: str = ""
    title: str = ""
    id: str = ""
    created_at: str = ""
    updated_at: str = ""
    writing_duration_sec: int = 0


@dataclass
class DayContext:
    date: str = ""
    location: str = ""
    weather: str = ""
    temp_c: Optional[float] = None
    context_source: str = ""
    context_updated_at: str = ""
    updated_at: str = ""


def parse_markdown(text: str) -> tuple[str, FrontMatter]:
    m = FRONT_MATTER_RE.match(text)
    if not m:
        return text, FrontMatter()
    raw, body = m.group(1), m.group(2).lstrip("\n")
    data: dict[str, str] = {}
    for line in raw.splitlines():
        if ":" not in line:
            continue
        key, val = line.split(":", 1)
        data[key.strip()] = val.strip().strip('"')
    duration = 0
    if data.get("writing_duration_sec"):
        try:
            duration = int(float(data["writing_duration_sec"]))
        except ValueError:
            duration = 0
    fm = FrontMatter(
        date=data.get("date", ""),
        title=data.get("title", ""),
        id=data.get("id", ""),
        created_at=data.get("created_at", ""),
        updated_at=data.get("updated_at", ""),
        writing_duration_sec=duration,
    )
    return body, fm


def yaml_escape(value: str) -> str:
    if any(c in value for c in ":#{}[],&*?|>!%@`'\"\\"):
        return f'"{value.replace(chr(34), chr(92) + chr(34))}"'
    return value


def render_markdown(body: str, fm: FrontMatter) -> str:
    title = fm.title or fm.date or "untitled"
    lines = [
        "---",
        f"date: {fm.date}",
        f"title: {yaml_escape(title)}",
    ]
    if fm.id:
        lines.append(f"id: {fm.id}")
    if fm.created_at:
        lines.append(f"created_at: {fm.created_at}")
    if fm.updated_at:
        lines.append(f"updated_at: {fm.updated_at}")
    if fm.writing_duration_sec:
        lines.append(f"writing_duration_sec: {fm.writing_duration_sec}")
    lines.append("---")
    return "\n".join(lines) + "\n\n" + body.rstrip() + "\n"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_text(text: str) -> str:
    return sha256_bytes(text.encode("utf-8"))


def _newer(a: str, b: str) -> bool:
    if not a:
        return False
    if not b:
        return True
    return a > b


def merge_entries(
    *,
    server_md: Optional[str],
    incoming_md: str,
    entry_id: str,
    server_updated_at: str,
    incoming_updated_at: str,
    server_created_at: str,
    incoming_created_at: str,
    server_duration: int,
    incoming_duration: int,
    server_deleted: bool,
    incoming_deleted: bool,
    server_deleted_at: str,
    incoming_deleted_at: str,
    fallback_date: str = "",
) -> tuple[str, FrontMatter, bool, str]:
    """Merge incoming into server state for one note id. Returns (md, fm, deleted, deleted_at)."""
    in_body, in_fm = parse_markdown(incoming_md)
    if server_md is None:
        fm = in_fm
        fm.id = entry_id
        if not fm.date:
            fm.date = fallback_date
        if not fm.updated_at:
            fm.updated_at = incoming_updated_at
        if not fm.created_at:
            fm.created_at = incoming_created_at or fm.updated_at
        fm.writing_duration_sec = max(incoming_duration, fm.writing_duration_sec)
        deleted = incoming_deleted
        deleted_at = incoming_deleted_at if deleted else ""
        return render_markdown(in_body, fm), fm, deleted, deleted_at

    srv_body, srv_fm = parse_markdown(server_md)

    if incoming_deleted and _newer(incoming_deleted_at, server_updated_at or srv_fm.updated_at):
        fm = srv_fm
        fm.id = entry_id
        return server_md, fm, True, incoming_deleted_at
    if server_deleted and _newer(server_deleted_at, incoming_updated_at or in_fm.updated_at):
        fm = srv_fm
        fm.id = entry_id
        return server_md, fm, True, server_deleted_at

    in_upd = incoming_updated_at or in_fm.updated_at
    srv_upd = server_updated_at or srv_fm.updated_at
    if _newer(in_upd, srv_upd):
        body, title, updated_at = in_body, in_fm.title or srv_fm.title, in_upd
    else:
        body, title, updated_at = srv_body, srv_fm.title or in_fm.title, srv_upd

    created = server_created_at or srv_fm.created_at or incoming_created_at or in_fm.created_at
    duration = max(
        server_duration,
        incoming_duration,
        srv_fm.writing_duration_sec,
        in_fm.writing_duration_sec,
    )
    date = srv_fm.date or in_fm.date or fallback_date
    fm = FrontMatter(
        date=date,
        title=title,
        id=entry_id,
        created_at=created,
        updated_at=updated_at,
        writing_duration_sec=duration,
    )
    return render_markdown(body, fm), fm, False, ""


def merge_day_context(server: Optional[DayContext], incoming: DayContext) -> DayContext:
    if server is None:
        out = incoming
        if not out.updated_at:
            out.updated_at = out.context_updated_at
        return out
    in_rank = CONTEXT_RANK.get(incoming.context_source or "", 0)
    srv_rank = CONTEXT_RANK.get(server.context_source or "", 0)
    if in_rank > srv_rank or (
        in_rank == srv_rank
        and _newer(incoming.context_updated_at, server.context_updated_at)
    ):
        winner = incoming
    else:
        winner = server
    updated = winner.updated_at or winner.context_updated_at
    if _newer(incoming.updated_at, winner.updated_at):
        # keep content from context winner; bump updated_at tracking if needed
        pass
    return DayContext(
        date=winner.date or incoming.date or server.date,
        location=winner.location,
        weather=winner.weather,
        temp_c=winner.temp_c,
        context_source=winner.context_source,
        context_updated_at=winner.context_updated_at,
        updated_at=updated or incoming.updated_at or server.updated_at,
    )
