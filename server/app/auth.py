from __future__ import annotations

import os
import secrets

from typing import Optional

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

_bearer = HTTPBearer(auto_error=False)


def expected_token() -> str:
    token = os.environ.get("DIARY_SYNC_TOKEN", "").strip()
    if not token:
        # Dev fallback so local `uvicorn` works; production must set env.
        token = os.environ.get("DIARY_SYNC_DEV_TOKEN", "dev-change-me")
    return token


def require_token(
    creds: Optional[HTTPAuthorizationCredentials] = Depends(_bearer),
) -> None:
    expected = expected_token()
    if creds is None or not secrets.compare_digest(creds.credentials, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        )
