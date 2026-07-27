"""
Timeline list — reverse chronological cards with title / summary / thumb.
"""
from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtGui import QIcon, QPixmap
from PySide6.QtWidgets import (
    QComboBox,
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QVBoxLayout,
    QWidget,
)

from app.diary_service import DiaryEntry, DiaryService


class TimelinePanel(QWidget):
    entry_activated = Signal(str)
    filter_changed = Signal(object, object)  # year|None, month|None

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.title = QLabel("时间线")
        self.title.setObjectName("sectionLabel")

        self.year_combo = QComboBox()
        self.month_combo = QComboBox()
        self.year_combo.currentIndexChanged.connect(self._emit_filter)
        self.month_combo.currentIndexChanged.connect(self._emit_filter)

        filters = QHBoxLayout()
        filters.setSpacing(6)
        filters.addWidget(self.year_combo, 1)
        filters.addWidget(self.month_combo, 1)

        self.list = QListWidget()
        self.list.setSpacing(2)
        self.list.itemClicked.connect(self._on_click)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 4, 12, 8)
        layout.setSpacing(6)
        layout.addWidget(self.title)
        layout.addLayout(filters)
        layout.addWidget(self.list, 1)

        self._suppress = False

    def set_year_options(self, years: list[int]) -> None:
        self._suppress = True
        current = self.year_combo.currentData()
        self.year_combo.clear()
        self.year_combo.addItem("全部年份", None)
        for y in years:
            self.year_combo.addItem(str(y), y)
        idx = self.year_combo.findData(current)
        self.year_combo.setCurrentIndex(max(0, idx))
        self._suppress = False
        self._rebuild_months()

    def _rebuild_months(self) -> None:
        self._suppress = True
        current = self.month_combo.currentData()
        self.month_combo.clear()
        self.month_combo.addItem("全部月份", None)
        if self.year_combo.currentData() is not None:
            for m in range(1, 13):
                self.month_combo.addItem(f"{m:02d} 月", m)
        idx = self.month_combo.findData(current)
        self.month_combo.setCurrentIndex(max(0, idx))
        self._suppress = False

    def _emit_filter(self) -> None:
        if self._suppress:
            return
        if self.sender() is self.year_combo:
            self._rebuild_months()
        self.filter_changed.emit(self.year_combo.currentData(), self.month_combo.currentData())

    def populate(self, entries: list[DiaryEntry], service: DiaryService) -> None:
        self.list.clear()
        for entry in entries:
            summary = service.summary_text(entry.body)
            text = f"{entry.entry_date}\n{entry.title}\n{summary}"
            item = QListWidgetItem(text)
            item.setData(Qt.ItemDataRole.UserRole, entry.entry_date)
            thumb = self._thumbnail(entry, service)
            if thumb is not None:
                item.setIcon(QIcon(thumb))
            item.setSizeHint(QSize(0, 76))
            self.list.addItem(item)

    def select_date(self, entry_date: str) -> None:
        for i in range(self.list.count()):
            item = self.list.item(i)
            if item and item.data(Qt.ItemDataRole.UserRole) == entry_date:
                self.list.setCurrentItem(item)
                return

    def _on_click(self, item: QListWidgetItem) -> None:
        date_key = item.data(Qt.ItemDataRole.UserRole)
        if date_key:
            self.entry_activated.emit(date_key)

    @staticmethod
    def _thumbnail(entry: DiaryEntry, service: DiaryService) -> QPixmap | None:
        if entry.image_paths:
            p = Path(entry.image_paths[0])
            if p.exists():
                pix = QPixmap(str(p))
                if not pix.isNull():
                    return pix.scaled(
                        48,
                        48,
                        Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                        Qt.TransformationMode.SmoothTransformation,
                    )
        from app.diary_service import first_image_relpath

        rel = first_image_relpath(entry.body)
        if not rel:
            return None
        path = service.resolve_asset(rel)
        if not path.exists():
            return None
        pix = QPixmap(str(path))
        if pix.isNull():
            return None
        return pix.scaled(
            48,
            48,
            Qt.AspectRatioMode.KeepAspectRatioByExpanding,
            Qt.TransformationMode.SmoothTransformation,
        )
