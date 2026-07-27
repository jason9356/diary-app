"""
Tracks active writing time while the editor is focused and the user types.
Idle gaps longer than ``idle_timeout`` are not counted.
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field


@dataclass
class WritingTimer:
    idle_timeout: float = 60.0
    _accumulated: float = 0.0
    _session_start: float | None = None
    _last_activity: float | None = None
    _active: bool = False

    def start_session(self, already_seconds: int = 0) -> None:
        self._accumulated = float(already_seconds)
        self._session_start = None
        self._last_activity = None
        self._active = False

    def on_focus(self) -> None:
        self._active = True
        now = time.monotonic()
        self._last_activity = now
        if self._session_start is None:
            self._session_start = now

    def on_blur(self) -> None:
        self._flush()
        self._active = False
        self._session_start = None

    def on_activity(self) -> None:
        now = time.monotonic()
        if not self._active:
            self.on_focus()
            return
        if self._last_activity is not None and (now - self._last_activity) > self.idle_timeout:
            # Idle gap — restart session without adding the idle time.
            self._session_start = now
        elif self._session_start is None:
            self._session_start = now
        self._last_activity = now

    def _flush(self) -> None:
        if self._session_start is None:
            return
        now = time.monotonic()
        end = self._last_activity or now
        if self._last_activity and (now - self._last_activity) > self.idle_timeout:
            end = self._last_activity
        delta = max(0.0, end - self._session_start)
        self._accumulated += delta
        self._session_start = None

    def seconds(self) -> int:
        total = self._accumulated
        if self._active and self._session_start is not None:
            now = time.monotonic()
            last = self._last_activity or now
            if (now - last) <= self.idle_timeout:
                total += max(0.0, last - self._session_start)
        return int(total)
