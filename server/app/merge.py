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
    location: str = ""
    weather: str = ""
    temp_c: Optional[float] = None
    context_source: str = ""
    context_updated_at: str = ""
    writing_duration_sec: int = 0


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
    temp: Optional[float] = None
    if data.get("temp_c"):
        try:
            temp = float(data["temp_c"])
        except ValueError:
            temp = None
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
        location=data.get("location", ""),
        weather=data.get("weather", ""),
        temp_c=temp,
        context_source=data.get("context_source", ""),
        context_updated_at=data.get("context_updated_at", ""),
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
    if fm.location:
        lines.append(f"location: {yaml_escape(fm.location)}")
    if fm.weather:
        lines.append(f"weather: {yaml_escape(fm.weather)}")
    if fm.temp_c is not None:
        lines.append(f"temp_c: {fm.temp_c:g}")
    if fm.context_source:
        lines.append(f"context_source: {fm.context_source}")
    if fm.context_updated_at:
        lines.append(f"context_updated_at: {fm.context_updated_at}")
    if fm.writing_duration_sec:
        lines.append(f"writing_duration_sec: {fm.writing_duration_sec}")
    lines.append("---")
    return "\n".join(lines) + "\n\n" + body.rstrip() + "\n"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_text(text: str) -> str:
    return sha256_bytes(text.encode("utf-8"))


def _newer(a: str, b: str) -> bool:
    """True if a is strictly newer than b (lexicographic ISO works for UTC offsets of same form)."""
    if not a:
        return False
    if not b:
        return True
    return a > b


def merge_entries(
    *,
    server_md: Optional[str],
    incoming_md: str,
    server_id: str,
    incoming_id: str,
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
) -> tuple[str, FrontMatter, bool, str]:
    """
    Merge incoming into server state.
    Returns (markdown, front_matter, deleted, deleted_at).
    First-arriver wins for id when both exist with different ids.
    """
    in_body, in_fm = parse_markdown(incoming_md)
    if server_md is None:
        # First write wins for id.
        fm = in_fm
        if not fm.id:
            fm.id = incoming_id or server_id
        if not fm.updated_at:
            fm.updated_at = incoming_updated_at
        if not fm.created_at:
            fm.created_at = incoming_created_at or fm.updated_at
        fm.writing_duration_sec = max(incoming_duration, fm.writing_duration_sec)
        if not fm.date and incoming_md:
            pass
        deleted = incoming_deleted
        deleted_at = incoming_deleted_at if deleted else ""
        return render_markdown(in_body, fm), fm, deleted, deleted_at

    srv_body, srv_fm = parse_markdown(server_md)

    # id: server keeps first-arriver
    winner_id = server_id or srv_fm.id or incoming_id or in_fm.id

    # deletion vs content
    if incoming_deleted and _newer(incoming_deleted_at, server_updated_at or srv_fm.updated_at):
        fm = srv_fm
        fm.id = winner_id
        return server_md, fm, True, incoming_deleted_at
    if server_deleted and _newer(server_deleted_at, incoming_updated_at or in_fm.updated_at):
        fm = srv_fm
        fm.id = winner_id
        return server_md, fm, True, server_deleted_at

    # body/title LWW by updated_at
    in_upd = incoming_updated_at or in_fm.updated_at
    srv_upd = server_updated_at or srv_fm.updated_at
    if _newer(in_upd, srv_upd):
        body, title, updated_at = in_body, in_fm.title or srv_fm.title, in_upd
    else:
        body, title, updated_at = srv_body, srv_fm.title or in_fm.title, srv_upd

    # context authority
    in_rank = CONTEXT_RANK.get(in_fm.context_source or "", 0)
    srv_rank = CONTEXT_RANK.get(srv_fm.context_source or "", 0)
    if in_rank > srv_rank or (
        in_rank == srv_rank and _newer(in_fm.context_updated_at, srv_fm.context_updated_at)
    ):
        loc, weather, temp, src, ctx_at = (
            in_fm.location,
            in_fm.weather,
            in_fm.temp_c,
            in_fm.context_source,
            in_fm.context_updated_at,
        )
    else:
        loc, weather, temp, src, ctx_at = (
            srv_fm.location,
            srv_fm.weather,
            srv_fm.temp_c,
            srv_fm.context_source,
            srv_fm.context_updated_at,
        )

    created = server_created_at or srv_fm.created_at or incoming_created_at or in_fm.created_at
    duration = max(
        server_duration,
        incoming_duration,
        srv_fm.writing_duration_sec,
        in_fm.writing_duration_sec,
    )
    date = srv_fm.date or in_fm.date
    fm = FrontMatter(
        date=date,
        title=title,
        id=winner_id,
        created_at=created,
        updated_at=updated_at,
        location=loc,
        weather=weather,
        temp_c=temp,
        context_source=src,
        context_updated_at=ctx_at,
        writing_duration_sec=duration,
    )
    return render_markdown(body, fm), fm, False, ""
