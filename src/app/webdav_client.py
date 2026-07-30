"""Minimal WebDAV client for vault mirror (stdlib only)."""
from __future__ import annotations

import re
import ssl
from dataclasses import dataclass
from datetime import datetime
from email.utils import parsedate_to_datetime
from typing import Optional
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from base64 import b64encode


@dataclass
class DavResource:
    path: str
    last_modified_ms: int = 0
    etag: str = ""


@dataclass
class WebDavConfig:
    base_url: str
    username: str
    password: str
    root_path: str = "/sparkbox"

    @property
    def enabled(self) -> bool:
        return bool(self.base_url.strip() and self.username.strip())


class WebDavClient:
    def __init__(self, config: WebDavConfig) -> None:
        self.config = config
        user = config.username.encode("utf-8")
        pwd = config.password.encode("utf-8")
        self._auth = "Basic " + b64encode(user + b":" + pwd).decode("ascii")
        self._ctx = ssl.create_default_context()

    def _root_url(self) -> str:
        base = self.config.base_url.strip().rstrip("/")
        root = "/" + self.config.root_path.strip().strip("/")
        return base + root

    def _url(self, rel: str) -> str:
        rel = rel.strip().lstrip("/")
        return self._root_url().rstrip("/") + "/" + rel

    def _request(
        self,
        method: str,
        url: str,
        *,
        data: bytes | None = None,
        headers: dict[str, str] | None = None,
    ):
        hdrs = {"Authorization": self._auth}
        if headers:
            hdrs.update(headers)
        req = Request(url, data=data, headers=hdrs, method=method)
        return urlopen(req, context=self._ctx, timeout=120)

    def ensure_root(self) -> None:
        self._mkcol_recursive(self.config.root_path.strip().strip("/"))

    def put_file(self, rel_path: str, raw: bytes, content_type: str = "application/octet-stream") -> None:
        self._ensure_parent(rel_path)
        try:
            with self._request(
                "PUT",
                self._url(rel_path),
                data=raw,
                headers={"Content-Type": content_type},
            ) as resp:
                if resp.status not in (200, 201, 204):
                    body = resp.read().decode("utf-8", errors="replace")
                    raise RuntimeError(f"WebDAV PUT {resp.status}: {body}")
        except HTTPError as exc:
            if exc.code not in (201, 204):
                raise RuntimeError(f"WebDAV PUT {exc.code}") from exc

    def get_file(self, rel_path: str) -> Optional[bytes]:
        try:
            with self._request("GET", self._url(rel_path)) as resp:
                return resp.read()
        except HTTPError as exc:
            if exc.code == 404:
                return None
            raise RuntimeError(f"WebDAV GET {exc.code}") from exc

    def delete_file(self, rel_path: str) -> None:
        try:
            with self._request("DELETE", self._url(rel_path)) as resp:
                if resp.status not in (200, 204):
                    raise RuntimeError(f"WebDAV DELETE {resp.status}")
        except HTTPError as exc:
            if exc.code == 404:
                return
            raise RuntimeError(f"WebDAV DELETE {exc.code}") from exc

    def list_resources(self, prefix: str = "") -> list[DavResource]:
        href = self._url(prefix)
        if prefix and not prefix.endswith("/"):
            href += "/"
        propfind = (
            '<?xml version="1.0" encoding="utf-8" ?>'
            '<d:propfind xmlns:d="DAV:">'
            "<d:prop><d:getlastmodified/><d:getetag/><d:resourcetype/></d:prop>"
            "</d:propfind>"
        ).encode("utf-8")
        try:
            with self._request(
                "PROPFIND",
                href,
                data=propfind,
                headers={
                    "Depth": "infinity",
                    "Content-Type": "application/xml",
                },
            ) as resp:
                xml = resp.read().decode("utf-8", errors="replace")
        except HTTPError as exc:
            if exc.code != 207:
                raise RuntimeError(f"WebDAV PROPFIND {exc.code}") from exc
            xml = exc.read().decode("utf-8", errors="replace")
        return self._parse_responses(xml)

    def _parse_responses(self, xml: str) -> list[DavResource]:
        root_norm = self._root_url().rstrip("/") + "/"
        blocks = re.findall(
            r"<(?:D:)?response\b[^>]*>([\s\S]*?)</(?:D:)?response>",
            xml,
            flags=re.I,
        )
        out: list[DavResource] = []
        for body in blocks:
            m = re.search(r"<(?:D:)?href>([^<]+)</(?:D:)?href>", body, flags=re.I)
            if not m:
                continue
            href = m.group(1).strip().replace("%20", " ")
            rel = self._relative_path(href, root_norm)
            if not rel:
                continue
            if re.search(r"<(?:D:)?collection\s*/>", body, flags=re.I) or rel.endswith("/"):
                continue
            mod_m = re.search(
                r"<(?:D:)?getlastmodified>([^<]+)</(?:D:)?getlastmodified>",
                body,
                flags=re.I,
            )
            etag_m = re.search(r"<(?:D:)?getetag>([^<]+)</(?:D:)?getetag>", body, flags=re.I)
            out.append(
                DavResource(
                    path=rel.rstrip("/"),
                    last_modified_ms=self._parse_http_date(mod_m.group(1).strip() if mod_m else ""),
                    etag=(etag_m.group(1).strip().strip('"') if etag_m else ""),
                )
            )
        # distinct by path
        seen: dict[str, DavResource] = {}
        for r in out:
            seen[r.path] = r
        return list(seen.values())

    def _relative_path(self, href: str, root_norm: str) -> Optional[str]:
        abs_url = href
        if href.startswith("/"):
            origin_m = re.match(r"^(https?://[^/]+)", self.config.base_url.strip())
            if not origin_m:
                return None
            abs_url = origin_m.group(1) + href
        if abs_url.startswith(root_norm) or abs_url.rstrip("/") == root_norm.rstrip("/"):
            return abs_url.removeprefix(root_norm).lstrip("/") or None
        root_path = "/" + self.config.root_path.strip().strip("/") + "/"
        idx = abs_url.find(root_path)
        if idx < 0:
            return None
        return abs_url[idx + len(root_path) :].lstrip("/") or None

    @staticmethod
    def _parse_http_date(raw: str) -> int:
        if not raw:
            return 0
        try:
            dt = parsedate_to_datetime(raw)
            return int(dt.timestamp() * 1000)
        except (TypeError, ValueError, IndexError):
            return 0

    def _ensure_parent(self, rel_path: str) -> None:
        parts = rel_path.strip("/").split("/")[:-1]
        if parts:
            self._mkcol_recursive("/".join(parts))

    def _mkcol_recursive(self, rel_dir: str) -> None:
        built = ""
        for seg in [s for s in rel_dir.strip("/").split("/") if s]:
            built = f"{built}/{seg}" if built else seg
            self._mkcol(built)

    def _mkcol(self, rel_dir: str) -> None:
        try:
            with self._request("MKCOL", self._url(rel_dir) + "/") as resp:
                _ = resp.status
        except HTTPError as exc:
            if exc.code in (201, 405, 409, 301, 302):
                return
            # some servers return 405 for existing
            if exc.code == 405:
                return
        except URLError:
            return
