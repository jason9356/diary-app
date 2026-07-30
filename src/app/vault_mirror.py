"""Incremental vault mirror over WebDAV (diary / assets / todos)."""
from __future__ import annotations

import hashlib
import json
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from app.webdav_client import WebDavClient

logger = logging.getLogger("diary.vault_mirror")


@dataclass
class VaultMirrorResult:
    uploaded: int = 0
    downloaded: int = 0
    skipped: int = 0
    removed: int = 0
    message: str = ""


class VaultMirror:
    def __init__(self, data_root: Path, client: WebDavClient) -> None:
        self.data_root = Path(data_root)
        self.client = client
        self.state_file = self.data_root / ".webdav-state.json"
        self.skew_ms = 2000

    def queue_remote_deletes(self, paths: list[str]) -> None:
        if not paths:
            return
        state, deleted = self._load_state()
        deleted.update(paths)
        self._save_state(state, deleted)

    def sync(self) -> VaultMirrorResult:
        try:
            self.client.ensure_root()
            up = down = skipped = removed = 0
            state, pending = self._load_state()
            remote = {
                r.path: r
                for r in self.client.list_resources()
                if self._is_vault_path(r.path)
            }
            local_paths = self._list_local()
            seen: set[str] = set()
            still: set[str] = set()

            for rel in list(pending):
                try:
                    self.client.delete_file(rel)
                    state.pop(rel, None)
                    removed += 1
                except Exception:  # noqa: BLE001
                    still.add(rel)

            for rel in local_paths:
                seen.add(rel)
                still.discard(rel)
                path = self.data_root / rel
                if not path.is_file():
                    continue
                local_mtime = int(path.stat().st_mtime * 1000)
                local_size = path.stat().st_size
                rem = remote.get(rel)
                prev = state.get(rel)
                needs_upload = self._needs_upload(local_mtime, local_size, rem, prev, path)
                needs_download = (
                    rem is not None
                    and rem.last_modified_ms > local_mtime + self.skew_ms
                    and not needs_upload
                )
                if needs_download:
                    raw = self.client.get_file(rel)
                    if raw is None:
                        continue
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(raw)
                    state[rel] = self._fp(path)
                    down += 1
                elif needs_upload:
                    self.client.put_file(rel, path.read_bytes(), self._guess_type(rel))
                    state[rel] = self._fp(path)
                    up += 1
                else:
                    state[rel] = prev or self._fp(path)
                    skipped += 1

            for rel, rem in remote.items():
                if rel in seen or not self._is_vault_path(rel):
                    continue
                if rel in still or rel in pending:
                    try:
                        self.client.delete_file(rel)
                        state.pop(rel, None)
                        removed += 1
                        still.discard(rel)
                    except Exception:  # noqa: BLE001
                        still.add(rel)
                    continue
                local = self.data_root / rel
                if local.is_file():
                    continue
                raw = self.client.get_file(rel)
                if raw is None:
                    continue
                local.parent.mkdir(parents=True, exist_ok=True)
                local.write_bytes(raw)
                state[rel] = self._fp(local)
                down += 1

            self._write_manifest()
            manifest = self.data_root / "manifest.json"
            if manifest.is_file():
                self.client.put_file("manifest.json", manifest.read_bytes(), "application/json")
                state["manifest.json"] = self._fp(manifest)
                up += 1

            self._save_state(state, still)
            msg = f"云盘增量同步：上传 {up}，下载 {down}，跳过 {skipped}"
            if removed:
                msg += f"，删除 {removed}"
            return VaultMirrorResult(up, down, skipped, removed, msg)
        except Exception as exc:  # noqa: BLE001
            logger.exception("vault mirror failed")
            return VaultMirrorResult(message=f"云盘同步失败：{exc}")

    def _needs_upload(self, local_mtime, local_size, rem, prev, path: Path) -> bool:
        if rem is None:
            return True
        if rem.last_modified_ms > 0 and local_mtime > rem.last_modified_ms + self.skew_ms:
            return True
        if rem.last_modified_ms > 0 and rem.last_modified_ms > local_mtime + self.skew_ms:
            return False
        if prev and prev.get("size") == local_size and prev.get("mtime") == local_mtime:
            return False
        if (
            prev
            and prev.get("hash")
            and prev.get("hash") == self._sha1(path)
            and 0 < rem.last_modified_ms <= local_mtime + self.skew_ms
        ):
            return False
        if rem.last_modified_ms <= 0 and prev and prev.get("size") == local_size and prev.get("mtime") == local_mtime:
            return False
        return True

    def _is_vault_path(self, rel: str) -> bool:
        name = rel.rsplit("/", 1)[-1]
        if name.startswith("."):
            return False
        return (
            rel.startswith("diary/")
            or rel.startswith("assets/")
            or rel.startswith("todos/")
            or rel == "manifest.json"
        )

    def _list_local(self) -> list[str]:
        out: list[str] = []
        for name in ("diary", "assets", "todos"):
            root = self.data_root / name
            if not root.exists():
                continue
            for f in root.rglob("*"):
                if f.is_file() and not f.name.startswith("."):
                    out.append(f.relative_to(self.data_root).as_posix())
        return out

    def _write_manifest(self) -> None:
        now = datetime.now(timezone.utc).isoformat(timespec="seconds")
        payload = {
            "schema_version": 1,
            "exported_at": now,
            "device": "desktop",
            "revision": 0,
        }
        (self.data_root / "manifest.json").write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    @staticmethod
    def _guess_type(rel: str) -> str:
        lower = rel.lower()
        if lower.endswith(".json"):
            return "application/json"
        if lower.endswith(".md"):
            return "text/markdown"
        if lower.endswith(".png"):
            return "image/png"
        if lower.endswith((".jpg", ".jpeg")):
            return "image/jpeg"
        if lower.endswith(".webp"):
            return "image/webp"
        return "application/octet-stream"

    def _fp(self, path: Path) -> dict:
        return {
            "mtime": int(path.stat().st_mtime * 1000),
            "size": path.stat().st_size,
            "hash": self._sha1(path),
        }

    def _load_state(self) -> tuple[dict, set[str]]:
        if not self.state_file.is_file():
            return {}, set()
        try:
            root = json.loads(self.state_file.read_text(encoding="utf-8"))
            files = root.get("files") or {}
            deleted = {p for p in (root.get("deleted") or []) if p}
            return dict(files), deleted
        except Exception:  # noqa: BLE001
            return {}, set()

    def _save_state(self, state: dict, deleted: set[str]) -> None:
        try:
            payload = {"files": state, "deleted": sorted(deleted)}
            self.state_file.write_text(json.dumps(payload), encoding="utf-8")
        except Exception:  # noqa: BLE001
            pass

    @staticmethod
    def _sha1(path: Path) -> str:
        try:
            h = hashlib.sha1()
            with path.open("rb") as f:
                while True:
                    chunk = f.read(8192)
                    if not chunk:
                        break
                    h.update(chunk)
            return h.hexdigest()
        except Exception:  # noqa: BLE001
            return ""
