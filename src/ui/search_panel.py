"""
Full-text search panel with keyword highlighting.
"""
from __future__ import annotations

from PySide6.QtCore import Qt, QSize, QTimer, Signal
from PySide6.QtGui import QColor, QTextCharFormat, QTextCursor, QTextDocument
from PySide6.QtWidgets import (
    QFrame,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QVBoxLayout,
    QWidget,
)

from storage.database import SearchHit
from ui.styles import LIGHT


class SearchResultCard(QFrame):
    def __init__(self, hit: SearchHit, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("inspirationCard")
        self.setCursor(Qt.CursorShape.PointingHandCursor)

        title = hit.title or hit.entry_date
        snippet = hit.snippet.replace("«", "").replace("»", "") if hit.snippet else ""
        if len(snippet) > 100:
            snippet = snippet[:100].rstrip() + "…"

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(6)

        title_label = QLabel(title)
        title_label.setObjectName("cardTitle")
        title_label.setWordWrap(True)
        layout.addWidget(title_label)

        if snippet:
            body = QLabel(snippet)
            body.setObjectName("cardBody")
            body.setWordWrap(True)
            layout.addWidget(body)

        footer = QLabel(hit.entry_date.replace("-", "/"))
        footer.setObjectName("cardFooter")
        layout.addWidget(footer)


class SearchPanel(QWidget):
    query_changed = Signal(str)
    result_activated = Signal(str)  # entry id

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.title = QLabel("搜索")
        self.title.setObjectName("sectionLabel")

        self.search_box = QLineEdit()
        self.search_box.setObjectName("searchBox")
        self.search_box.setPlaceholderText("搜索灵感…")
        self.search_box.setClearButtonEnabled(True)

        self.results = QListWidget()
        self.results.setObjectName("cardList")
        self.results.setSpacing(0)
        self.results.setVerticalScrollMode(QListWidget.ScrollMode.ScrollPerPixel)
        self.results.itemClicked.connect(self._on_click)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 4, 14, 8)
        layout.setSpacing(8)
        layout.addWidget(self.title)
        layout.addWidget(self.search_box)
        layout.addWidget(self.results, 1)

        self._debounce = QTimer(self)
        self._debounce.setSingleShot(True)
        self._debounce.setInterval(220)
        self._debounce.timeout.connect(self._emit_query)
        self.search_box.textChanged.connect(lambda _: self._debounce.start())
        self._highlight_palette = dict(LIGHT)

    def set_palette(self, palette: dict[str, str]) -> None:
        self._highlight_palette = dict(palette)

    def _emit_query(self) -> None:
        self.query_changed.emit(self.search_box.text().strip())

    def show_results(self, hits: list[SearchHit], keyword: str) -> None:
        self.results.clear()
        width = max(160, self.results.viewport().width() - 4)
        for hit in hits:
            item = QListWidgetItem()
            item.setData(Qt.ItemDataRole.UserRole, hit.entry_id)
            item.setData(Qt.ItemDataRole.UserRole + 1, keyword)
            card = SearchResultCard(hit)
            card.setFixedWidth(width)
            card.adjustSize()
            item.setSizeHint(QSize(width, max(card.sizeHint().height(), 64)))
            self.results.addItem(item)
            self.results.setItemWidget(item, card)

    def _on_click(self, item: QListWidgetItem) -> None:
        entry_id = item.data(Qt.ItemDataRole.UserRole)
        if entry_id:
            self.result_activated.emit(entry_id)

    def resizeEvent(self, event) -> None:  # noqa: N802
        super().resizeEvent(event)
        width = max(160, self.results.viewport().width() - 4)
        for i in range(self.results.count()):
            item = self.results.item(i)
            widget = self.results.itemWidget(item)
            if item and widget:
                widget.setFixedWidth(width)
                widget.adjustSize()
                item.setSizeHint(QSize(width, max(widget.sizeHint().height(), 64)))


def highlight_in_editor(editor_widget, keyword: str, palette: dict[str, str] | None = None) -> None:
    """Extra selections highlight for search keyword inside QTextEdit."""
    from PySide6.QtWidgets import QTextEdit

    if not hasattr(editor_widget, "setExtraSelections"):
        return
    if not keyword or not isinstance(editor_widget, QTextEdit):
        editor_widget.setExtraSelections([])
        return

    p = palette or LIGHT
    selections: list[QTextEdit.ExtraSelection] = []
    fmt = QTextCharFormat()
    fmt.setBackground(QColor(p.get("accent_soft", "#D9E5E2")))
    fmt.setForeground(QColor(p.get("text", "#111827")))

    doc: QTextDocument = editor_widget.document()
    cursor = QTextCursor(doc)
    while True:
        cursor = doc.find(keyword, cursor)
        if cursor.isNull():
            break
        sel = QTextEdit.ExtraSelection()
        sel.cursor = cursor
        sel.format = fmt
        selections.append(sel)
    editor_widget.setExtraSelections(selections)
