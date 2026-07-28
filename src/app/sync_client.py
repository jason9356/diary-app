"""
Minimal diary sync client (desktop) against server sync-protocol v1.
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
from storage.database import EntryMeta, utc_now_iso
from storage.markdown_store import MarkdownStore
from utils.paths import diary_md_relpath

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
            "User-Agent": "diary-app-desktop/0.3",
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

    def sync_today_and_changes(self, entry_date: Optional[str] = None) -> SyncResult:
        if not self.enabled:
            return SyncResult(message="未配置 sync_endpoint / sync_token")
        day = entry_date or self.service.today()
        result = SyncResult()
        try:
            # 1) Pull other changed days first (incremental).
            since = int(self.config.sync_cursor or 0)
            status, raw = self._request("GET", f"/changes?since={since}")
            if status != 200:
                result.message = f"changes 失败 HTTP {status}"
                return result
            payload = json.loads(raw.decode("utf-8"))
            new_rev = int(payload.get("revision", since))
            for ch in payload.get("changes", []):
                d = ch.get("entry_date")
                if not d or d == day or ch.get("deleted"):
                    continue
                if self._pull_entry(d):
                    result.pulled += 1
                    result.assets_down += self._download_assets(d)

            # 2) Reconcile today: pull-if-remote-newer, else push local.
            #    Never bump local updated_at just for syncing (that made local always win).
            local = self.service.get_or_create(day)
            remote = self._fetch_entry(day)
            if remote and not remote.get("deleted"):
                remote_updated = str(remote.get("updated_at") or "")
                local_updated = str(local.updated_at or "")
                if remote_updated > local_updated or not (local.body or "").strip():
                    self._apply_server_entry(day, remote)
                    self.service.db.mark_synced(day, utc_now_iso())
                    result.pulled += 1
                    result.assets_down += self._download_assets(day)
                elif remote_updated < local_updated:
                    result.assets_up += self._upload_assets(day)
                    if self._push_entry(day, self.service.get_or_create(day)):
                        result.pushed += 1
                    result.assets_down += self._download_assets(day)
                else:
                    # Same timestamp — already aligned; only fill missing assets.
                    result.assets_down += self._download_assets(day)
                    result.assets_up += self._upload_assets(day)
            else:
                # Nothing on server yet — push local if any content / context.
                if (local.body or "").strip() or local.location or local.weather:
                    result.assets_up += self._upload_assets(day)
                    if self._push_entry(day, local):
                        result.pushed += 1

            self.config.sync_cursor = new_rev
            self._persist_sync_cursor(new_rev)
            # Refresh revision after possible push.
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

    def _fetch_entry(self, entry_date: str) -> Optional[dict]:
        status, raw = self._request("GET", f"/entries/{entry_date}")
        if status == 404:
            return None
        if status != 200:
            logger.warning("fetch %s failed: %s", entry_date, status)
            return None
        return json.loads(raw.decode("utf-8"))

    def _persist_sync_cursor(self, revision: int) -> None:
        """Update only sync_cursor in the project config (never rewrite data_dir)."""
        try:
            cfg = load_config()
            cfg.sync_cursor = int(revision)
            # Keep endpoint/token from current client if project ones are empty.
            if self.config.sync_endpoint.strip() and not cfg.sync_endpoint.strip():
                cfg.sync_endpoint = self.config.sync_endpoint.strip()
            if self.config.sync_token.strip() and not cfg.sync_token.strip():
                cfg.sync_token = self.config.sync_token.strip()
            save_config(cfg)
        except Exception:  # noqa: BLE001
            logger.exception("failed to persist sync_cursor")

    def _upload_assets(self, entry_date: str) -> int:
        n = 0
        for path in self.service.assets.list_for_date(entry_date):
            data = path.read_bytes()
            digest = hashlib.sha256(data).hexdigest()
            status, _ = self._request(
                "PUT",
                f"/assets/{entry_date}/{urllib.parse.quote(path.name)}",
                data=data,
                headers={"X-Content-SHA256": digest, "Content-Type": "application/octet-stream"},
            )
            if status in (200, 201):
                n += 1
        return n

    def _download_assets(self, entry_date: str) -> int:
        status, raw = self._request("GET", f"/entries/{entry_date}")
        if status != 200:
            return 0
        payload = json.loads(raw.decode("utf-8"))
        n = 0
        for asset in payload.get("assets", []):
            name = asset.get("name")
            if not name:
                continue
            dest = self.service.config.assets_root / entry_date / name
            if dest.exists():
                continue
            st, data = self._request("GET", f"/assets/{entry_date}/{urllib.parse.quote(name)}")
            if st != 200:
                continue
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
            n += 1
        return n

    def _push_entry(self, entry_date: str, entry) -> bool:
        markdown = self.service.md.read_raw(entry_date)
        if not markdown.strip():
            # synthesize from fields
            markdown = ""
        body = {
            "id": entry.id,
            "updated_at": entry.updated_at,
            "created_at": entry.created_at,
            "writing_duration_sec": entry.writing_duration_sec,
            "deleted": False,
            "markdown": markdown or self._build_md(entry),
            "assets": [
                {
                    "name": p.name,
                    "sha256": hashlib.sha256(p.read_bytes()).hexdigest(),
                }
                for p in self.service.assets.list_for_date(entry_date)
            ],
        }
        status, raw = self._request("PUT", f"/entries/{entry_date}", json_body=body)
        if status != 200:
            logger.warning("push %s failed: %s %s", entry_date, status, raw[:200])
            return False
        payload = json.loads(raw.decode("utf-8"))
        self._apply_server_entry(entry_date, payload)
        self.service.db.mark_synced(entry_date, utc_now_iso())
        return True

    def _pull_entry(self, entry_date: str) -> bool:
        status, raw = self._request("GET", f"/entries/{entry_date}")
        if status == 404:
            return False
        if status != 200:
            logger.warning("pull %s failed: %s", entry_date, status)
            return False
        payload = json.loads(raw.decode("utf-8"))
        if payload.get("deleted"):
            return False
        self._apply_server_entry(entry_date, payload)
        self.service.db.mark_synced(entry_date, utc_now_iso())
        return True

    def _apply_server_entry(self, entry_date: str, payload: dict) -> None:
        markdown = payload.get("markdown") or ""
        if not markdown.strip():
            return
        self.service.md.write_raw(entry_date, markdown)
        body, fm = _parse_markdown(markdown)
        title = fm.title or extract_title(body, entry_date)
        meta = EntryMeta(
            id=payload.get("id") or fm.id,
            entry_date=entry_date,
            title=title,
            word_count=count_words(body),
            created_at=payload.get("created_at") or fm.created_at or utc_now_iso(),
            updated_at=payload.get("updated_at") or fm.updated_at or utc_now_iso(),
            writing_duration_sec=int(
                payload.get("writing_duration_sec") or fm.writing_duration_sec or 0
            ),
            file_relpath=diary_md_relpath(entry_date),
            content_hash=content_hash(body),
            synced_at=utc_now_iso(),
            deleted=0,
            location=fm.location,
            weather=fm.weather,
            temp_c=fm.temp_c,
            context_source=fm.context_source,
            context_updated_at=fm.context_updated_at,
        )
        self.service.db.upsert_entry(meta, body=body)

    @staticmethod
    def _build_md(entry) -> str:
        from storage.markdown_store import MarkdownStore

        tmp = MarkdownStore.__new__(MarkdownStore)
        # Use write via a throwaway path — instead render with public write params
        lines = [
            "---",
            f"date: {entry.entry_date}",
            f"title: {entry.title}",
            f"id: {entry.id}",
            f"created_at: {entry.created_at}",
            f"updated_at: {entry.updated_at}",
        ]
        if entry.location:
            lines.append(f"location: {entry.location}")
        if entry.weather:
            lines.append(f"weather: {entry.weather}")
        if entry.temp_c is not None:
            lines.append(f"temp_c: {entry.temp_c:g}")
        if entry.context_source:
            lines.append(f"context_source: {entry.context_source}")
        if entry.context_updated_at:
            lines.append(f"context_updated_at: {entry.context_updated_at}")
        if entry.writing_duration_sec:
            lines.append(f"writing_duration_sec: {entry.writing_duration_sec}")
        lines.append("---")
        return "\n".join(lines) + "\n\n" + (entry.body or "").rstrip() + "\n"
