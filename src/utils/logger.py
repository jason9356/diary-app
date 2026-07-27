"""
Application logging setup.
"""
from __future__ import annotations

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path


def setup_logging(log_dir: Path, level: int = logging.INFO) -> logging.Logger:
    """Configure root logger with console + rotating file handlers."""
    log_dir.mkdir(parents=True, exist_ok=True)
    log_file = log_dir / "diary-app.log"

    root = logging.getLogger()
    if root.handlers:
        return logging.getLogger("diary")

    root.setLevel(level)
    fmt = logging.Formatter(
        "%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    console = logging.StreamHandler()
    console.setLevel(level)
    console.setFormatter(fmt)
    root.addHandler(console)

    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=2 * 1024 * 1024,
        backupCount=5,
        encoding="utf-8",
    )
    file_handler.setLevel(level)
    file_handler.setFormatter(fmt)
    root.addHandler(file_handler)

    logger = logging.getLogger("diary")
    logger.info("Logging initialized → %s", log_file)
    return logger
