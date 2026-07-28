"""
Diary sync client (desktop) against sync-protocol v2.
"""
from __future__ import annotations

import hashlib
import json
import logging
import socket
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from app.config import AppConfig, load_config, save_config
from app.diary_service import DiaryService, content_hash, count_words, extract_title
from storage.database import DayMeta, EntryMeta, utc_now_iso
from storage.day_store import DayContext
from storage.markdown_store import MarkdownStore
from utils.paths import diary_md_relpath_for_id

logger = logging.getLogger("diary.sync")


@dataclass
class SyncResult:
    pushed: int = 0
    pulled: int = 0
    assets_up: int = 0
    assets_down: int = 0
    message: str = ""


def _parse_markdown(text: str):
    store = MarkdownStore(Path("."))
    return store._parse(text)  # noqa: SLF001


class SyncClient:
    def __init__(self, config: AppConfig, service: DiaryService) -> None:
        self.config = config
        self.service = service

    @property
    def enabled(self) -> bool:
        return bool(self.config.sync_endpoint.strip() and self.config.sync_token.strip())

    def _base(self) -> str:
        base = self.config.sync_endpoint.strip().rstrip("/")
        if base.endswith("/v1"):
            return base
        return base + "/v1"

    def _request(
        self,
        method: str,
        path: str,
        *,
        data: Optional[bytes] = None,
        json_body: Optional[dict] = None,
        headers: Optional[dict[str, str]] = None,
    ) -> tuple[int, bytes]:
        url = self._base() + path
        hdrs = {
            "Authorization": f"Bearer {self.config.sync_token.strip()}",
            "User-Agent": "diary-app-desktop/0.4",
        }
        body = data
        if json_body is not None:
            body = json.dumps(json_body).encode("utf-8")
            hdrs["Content-Type"] = "application/json"
        if headers:
            hdrs.update(headers)
        req = urllib.request.Request(url, data=body, headers=hdrs, method=method)
        try:
            with urllib.request.urlopen(req, timeout=8) as resp:
                return resp.status, resp.read()
        except urllib.error.HTTPError as exc:
            return exc.code, exc.read() if exc.fp else b""
        except urllib.error.URLError as exc:
            raise ConnectionError(f"无法连接同步服务 {url}: {exc.reason}") from exc
        except (TimeoutError, socket.timeout) as exc:
            raise ConnectionError(f"同步服务超时 {url}") from exc

    def _check_protocol(self) -> Optional[str]:
        status, raw = self._request("GET", "/health")
        if status != 200:
            return f"health 失败 HTTP {status}"
        payload = json.loads(raw.decode("utf-8"))
        if int(payload.get("protocol", 0)) != 2:
            return "同步服务不是 protocol v2，请升级服务端"
        return None

    def sync_today_and_changes(self, entry_date: Optional[str] = None) -> SyncResult:
        if not self.enabled:
            return SyncResult(message="未配置 sync_endpoint / sync_token")
        day = entry_date or self.service.today()
        result = SyncResult()
        try:
            proto_err = self._check_protocol()
            if proto_err:
                result.message = proto_err
                return result

            since = int(self.config.sync_cursor or 0)
            status, raw = self._request("GET", f"/changes?since={since}")
            if status != 200:
                result.message = f"changes 失败 HTTP {status}"
                return result
            payload = json.loads(raw.decode("utf-8"))
            new_rev = int(payload.get("revision", since))
            today_entry_ids = {n.id for n in self.service.list_for_date(day)}

            for ch in payload.get("changes", []):
                kind = ch.get("kind", "entry")
                if kind == "day":
                    d = ch.get("date")
                    if not d or d == day:
                        continue
                    if self._pull_day(d):
                        result.pulled += 1
                    continue
                entry_id = ch.get("id")
                ch_date = ch.get("date", "")
                if not entry_id:
                    continue
                if ch_date == day or entry_id in today_entry_ids:
                    continue
                if ch.get("deleted"):
                    continue
                if self._pull_entry(entry_id):
                    result.pulled += 1
                    result.assets_down += self._download_assets(entry_id)

            # Reconcile today: all notes + day context
            self._sync_day(day, result)

            self.config.sync_cursor = new_rev
            self._persist_sync_cursor(new_rev)
            st2, raw2 = self._request("GET", "/changes?since=0")
            if st2 == 200:
                rev2 = int(json.loads(raw2.decode("utf-8")).get("revision", new_rev))
                self.config.sync_cursor = rev2
                self._persist_sync_cursor(rev2)

            result.message = (
                f"同步完成：推送 {result.pushed}，拉取 {result.pulled}，"
                f"上传图 {result.assets_up}，下载图 {result.assets_down}"
            )
        except Exception as exc:  # noqa: BLE001
            logger.exception("sync failed")
            result.message = f"同步失败：{exc}"
        return result

    def _sync_day(self, day: str, result: SyncResult) -> None:
        local_notes = self.service.list_for_date(day)
        remote_ids: set[str] = set()

        for ch in self._all_entry_changes_for_day(day):
            eid = ch.get("id")
            if eid:
                remote_ids.add(eid)

        for entry_id in remote_ids:
            remote = self._fetch_entry(entry_id)
            if not remote or remote.get("deleted"):
                continue
            local = self.service.get_by_id(entry_id)
            remote_updated = str(remote.get("updated_at") or "")
            local_updated = str(local.updated_at or "") if local else ""
            if local is None or remote_updated > local_updated:
                self._apply_server_entry(remote)
                self.service.db.mark_synced(entry_id, utc_now_iso())
                result.pulled += 1
                result.assets_down += self._download_assets(entry_id)
            elif local and remote_updated < local_updated:
                result.assets_up += self._upload_assets(entry_id)
                if self._push_entry(local):
                    result.pushed += 1
                result.assets_down += self._download_assets(entry_id)
            else:
                result.assets_down += self._download_assets(entry_id)
                result.assets_up += self._upload_assets(entry_id)

        for local in local_notes:
            if local.id not in remote_ids:
                if (local.body or "").strip():
                    result.assets_up += self._upload_assets(local.id)
                    if self._push_entry(local):
                        result.pushed += 1

        self._sync_day_context(day, result)

    def _all_entry_changes_for_day(self, day: str) -> list[dict]:
        status, raw = self._request("GET", "/changes?since=0")
        if status != 200:
            return []
        payload = json.loads(raw.decode("utf-8"))
        return [
            ch
            for ch in payload.get("changes", [])
            if ch.get("kind", "entry") == "entry"
            and ch.get("date") == day
            and not ch.get("deleted")
        ]

    def _sync_day_context(self, day: str, result: SyncResult) -> None:
        remote = self._fetch_day(day)
        local = self.service.get_day_context(day)
        if remote:
            remote_updated = str(remote.get("updated_at") or "")
            local_updated = str(local.updated_at or "")
            if remote_updated > local_updated:
                self._apply_server_day(day, remote)
                self.service.db.mark_day_synced(day, utc_now_iso())
                result.pulled += 1
            elif remote_updated < local_updated and (
                local.location or local.weather or local.temp_c is not None
            ):
                if self._push_day(local):
                    result.pushed += 1
        elif local.location or local.weather or local.temp_c is not None:
            if self._push_day(local):
                result.pushed += 1

    def _fetch_entry(self, entry_id: str) -> Optional[dict]:
        status, raw = self._request("GET", f"/entries/{entry_id}")
        if status == 404:
            return None
        if status != 200:
            logger.warning("fetch entry %s failed: %s", entry_id, status)
            return None
        return json.loads(raw.decode("utf-8"))

    def _fetch_day(self, entry_date: str) -> Optional[dict]:
        status, raw = self._request("GET", f"/days/{entry_date}")
        if status == 404:
            return None
        if status != 200:
            logger.warning("fetch day %s failed: %s", entry_date, status)
            return None
        return json.loads(raw.decode("utf-8"))

    def _persist_sync_cursor(self, revision: int) -> None:
        try:
            cfg = load_config()
            cfg.sync_cursor = int(revision)
            if self.config.sync_endpoint.strip() and not cfg.sync_endpoint.strip():
                cfg.sync_endpoint = self.config.sync_endpoint.strip()
            if self.config.sync_token.strip() and not cfg.sync_token.strip():
                cfg.sync_token = self.config.sync_token.strip()
            save_config(cfg)
        except Exception:  # noqa: BLE001
            logger.exception("failed to persist sync_cursor")

    def _upload_assets(self, entry_id: str) -> int:
        n = 0
        for path in self.service.assets.list_for_entry(entry_id):
            data = path.read_bytes()
            digest = hashlib.sha256(data).hexdigest()
            status, _ = self._request(
                "PUT",
                f"/assets/{entry_id}/{urllib.parse.quote(path.name)}",
                data=data,
                headers={"X-Content-SHA256": digest, "Content-Type": "application/octet-stream"},
            )
            if status in (200, 201):
                n += 1
        return n

    def _download_assets(self, entry_id: str) -> int:
        status, raw = self._request("GET", f"/entries/{entry_id}")
        if status != 200:
            return 0
        payload = json.loads(raw.decode("utf-8"))
        n = 0
        for asset in payload.get("assets", []):
            name = asset.get("name")
            if not name:
                continue
            dest = self.service.config.assets_root / entry_id / name
            if dest.exists():
                continue
            st, data = self._request(
                "GET", f"/assets/{entry_id}/{urllib.parse.quote(name)}"
            )
            if st != 200:
                continue
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
            n += 1
        return n

    def _push_entry(self, entry) -> bool:
        markdown = self.service.md.read_raw(entry.id, entry.entry_date)
        if not markdown.strip():
            markdown = self._build_md(entry)
        body = {
            "date": entry.entry_date,
            "updated_at": entry.updated_at,
            "created_at": entry.created_at,
            "writing_duration_sec": entry.writing_duration_sec,
            "deleted": False,
            "deleted_at": None,
            "markdown": markdown,
            "assets": [
                {
                    "name": p.name,
                    "sha256": hashlib.sha256(p.read_bytes()).hexdigest(),
                }
                for p in self.service.assets.list_for_entry(entry.id)
            ],
        }
        status, raw = self._request("PUT", f"/entries/{entry.id}", json_body=body)
        if status != 200:
            logger.warning("push entry %s failed: %s %s", entry.id, status, raw[:200])
            return False
        payload = json.loads(raw.decode("utf-8"))
        self._apply_server_entry(payload)
        self.service.db.mark_synced(entry.id, utc_now_iso())
        return True

    def _push_day(self, ctx: DayContext) -> bool:
        body = {
            "date": ctx.date,
            "location": ctx.location,
            "weather": ctx.weather,
            "temp_c": ctx.temp_c,
            "context_source": ctx.context_source,
            "context_updated_at": ctx.context_updated_at,
            "updated_at": ctx.updated_at or ctx.context_updated_at,
        }
        status, raw = self._request("PUT", f"/days/{ctx.date}", json_body=body)
        if status != 200:
            logger.warning("push day %s failed: %s", ctx.date, status)
            return False
        payload = json.loads(raw.decode("utf-8"))
        self._apply_server_day(ctx.date, payload)
        self.service.db.mark_day_synced(ctx.date, utc_now_iso())
        return True

    def _pull_entry(self, entry_id: str) -> bool:
        status, raw = self._request("GET", f"/entries/{entry_id}")
        if status == 404:
            return False
        if status != 200:
            logger.warning("pull entry %s failed: %s", entry_id, status)
            return False
        payload = json.loads(raw.decode("utf-8"))
        if payload.get("deleted"):
            return False
        self._apply_server_entry(payload)
        self.service.db.mark_synced(entry_id, utc_now_iso())
        return True

    def _pull_day(self, entry_date: str) -> bool:
        payload = self._fetch_day(entry_date)
        if not payload:
            return False
        self._apply_server_day(entry_date, payload)
        self.service.db.mark_day_synced(entry_date, utc_now_iso())
        return True

    def _apply_server_entry(self, payload: dict) -> None:
        markdown = payload.get("markdown") or ""
        if not markdown.strip():
            return
        entry_id = payload.get("id") or ""
        entry_date = payload.get("date") or ""
        body, fm = _parse_markdown(markdown)
        if not entry_id:
            entry_id = fm.id
        if not entry_date:
            entry_date = fm.date
        if not entry_id or not entry_date:
            return
        self.service.md.write_raw(entry_id, entry_date, markdown)
        title = fm.title or extract_title(body, entry_date)
        meta = EntryMeta(
            id=entry_id,
            entry_date=entry_date,
            title=title,
            word_count=count_words(body),
            created_at=payload.get("created_at") or fm.created_at or utc_now_iso(),
            updated_at=payload.get("updated_at") or fm.updated_at or utc_now_iso(),
            writing_duration_sec=int(
                payload.get("writing_duration_sec") or fm.writing_duration_sec or 0
            ),
            file_relpath=diary_md_relpath_for_id(entry_date, entry_id),
            content_hash=content_hash(body),
            synced_at=utc_now_iso(),
            deleted=0,
        )
        self.service.db.upsert_entry(meta, body=body)

    def _apply_server_day(self, entry_date: str, payload: dict) -> None:
        incoming = DayContext(
            date=entry_date,
            location=payload.get("location", ""),
            weather=payload.get("weather", ""),
            temp_c=payload.get("temp_c"),
            context_source=payload.get("context_source", ""),
            context_updated_at=payload.get("context_updated_at", ""),
            updated_at=payload.get("updated_at", ""),
        )
        merged = self.service.days.merge_write(incoming)
        self.service.db.upsert_day(
            DayMeta(
                date=merged.date,
                location=merged.location,
                weather=merged.weather,
                temp_c=merged.temp_c,
                context_source=merged.context_source,
                context_updated_at=merged.context_updated_at,
                updated_at=merged.updated_at,
                synced_at=utc_now_iso(),
            )
        )

    @staticmethod
    def _build_md(entry) -> str:
        lines = [
            "---",
            f"date: {entry.entry_date}",
            f"title: {entry.title}",
            f"id: {entry.id}",
            f"created_at: {entry.created_at}",
            f"updated_at: {entry.updated_at}",
        ]
        if entry.writing_duration_sec:
            lines.append(f"writing_duration_sec: {entry.writing_duration_sec}")
        lines.append("---")
        return "\n".join(lines) + "\n\n" + (entry.body or "").rstrip() + "\n"
