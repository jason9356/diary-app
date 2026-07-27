"""
Personal Diary App — entry point.

Run:
  python src/main.py
"""
from __future__ import annotations

import sys
from pathlib import Path

# Allow `python src/main.py` by putting src/ on sys.path.
SRC_DIR = Path(__file__).resolve().parent
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication

from app.config import load_config
from app.diary_service import DiaryService
from ui.main_window import MainWindow
from utils.fonts import app_font
from utils.logger import setup_logging
from utils.paths import default_log_dir


def main() -> int:
    setup_logging(default_log_dir())
    config = load_config()

    # High-DPI friendly defaults.
    QApplication.setHighDpiScaleFactorRoundingPolicy(
        Qt.HighDpiScaleFactorRoundingPolicy.PassThrough
    )
    app = QApplication(sys.argv)
    app.setApplicationName("Diary")
    app.setOrganizationName("PersonalDiary")
    app.setQuitOnLastWindowClosed(True)
    app.setFont(app_font(12))

    service = DiaryService(config)
    window = MainWindow(config, service)
    window.show()

    code = app.exec()
    service.close()
    return code


if __name__ == "__main__":
    raise SystemExit(main())
