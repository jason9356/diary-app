"""
Timeline list — reverse chronological inspiration cards.
"""
from __future__ import annotations

from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtWidgets import (
    QComboBox,
    QFrame,
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QVBoxLayout,
    QWidget,
)

from app.diary_service import DiaryEntry, DiaryService


class InspirationCardWidget(QFrame):
    """Compact card matching Android InspirationCard hierarchy."""

    def __init__(
        self,
        entry: DiaryEntry,
        summary: str,
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self.setObjectName("inspirationCard")
        self.setCursor(Qt.CursorShape.PointingHandCursor)

        tag = ""
        # Desktop entries may not carry tags yet; keep the slot for future parity.
        tags = getattr(entry, "tags", None) or []
        if tags:
            tag = str(tags[0])
        title = (entry.title or "").strip()
        if not title or title == entry.entry_date:
            title = summary.split("\n", 1)[0][:40] if summary else "未命名灵感"
        body = summary.strip()
        if body.startswith(title):
            body = body[len(title) :].strip()
        if len(body) > 90:
            body = body[:90].rstrip() + "…"

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(6)

        if tag:
            tag_label = QLabel(f"▸ {tag}")
            tag_label.setObjectName("cardTag")
            layout.addWidget(tag_label)

        title_label = QLabel(title)
        title_label.setObjectName("cardTitle")
        title_label.setWordWrap(True)
        layout.addWidget(title_label)

        if body and body != "（空）":
            body_label = QLabel(body)
            body_label.setObjectName("cardBody")
            body_label.setWordWrap(True)
            layout.addWidget(body_label)

        footer = QLabel(entry.entry_date.replace("-", "/"))
        footer.setObjectName("cardFooter")
        layout.addWidget(footer)


class TimelinePanel(QWidget):
    entry_activated = Signal(str)  # entry id
    filter_changed = Signal(object, object)  # year|None, month|None

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.title = QLabel("灵感")
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
        self.list.setObjectName("cardList")
        self.list.setSpacing(0)
        self.list.setVerticalScrollMode(QListWidget.ScrollMode.ScrollPerPixel)
        self.list.itemClicked.connect(self._on_click)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 4, 14, 8)
        layout.setSpacing(8)
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
            item = QListWidgetItem()
            item.setData(Qt.ItemDataRole.UserRole, entry.id)
            card = InspirationCardWidget(entry, summary)
            # Force layout so sizeHint is accurate for the list viewport.
            card.adjustSize()
            hint = card.sizeHint()
            width = max(160, self.list.viewport().width() - 4)
            card.setFixedWidth(width)
            card.adjustSize()
            hint = card.sizeHint()
            item.setSizeHint(QSize(width, max(hint.height(), 72)))
            self.list.addItem(item)
            self.list.setItemWidget(item, card)

    def select_entry(self, entry_id: str) -> None:
        for i in range(self.list.count()):
            item = self.list.item(i)
            if item and item.data(Qt.ItemDataRole.UserRole) == entry_id:
                self.list.setCurrentItem(item)
                return

    def select_date(self, entry_date: str) -> None:
        for i in range(self.list.count()):
            item = self.list.item(i)
            widget = self.list.itemWidget(item) if item else None
            if isinstance(widget, InspirationCardWidget):
                footer = widget.findChild(QLabel, "cardFooter")
                if footer and footer.text().replace("/", "-") == entry_date:
                    self.list.setCurrentItem(item)
                    return

    def _on_click(self, item: QListWidgetItem) -> None:
        entry_id = item.data(Qt.ItemDataRole.UserRole)
        if entry_id:
            self.entry_activated.emit(entry_id)

    def resizeEvent(self, event) -> None:  # noqa: N802
        super().resizeEvent(event)
        width = max(160, self.list.viewport().width() - 4)
        for i in range(self.list.count()):
            item = self.list.item(i)
            widget = self.list.itemWidget(item)
            if item and widget:
                widget.setFixedWidth(width)
                widget.adjustSize()
                item.setSizeHint(QSize(width, max(widget.sizeHint().height(), 72)))
