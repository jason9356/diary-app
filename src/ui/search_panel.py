"""
Full-text search panel with keyword highlighting.
"""
from __future__ import annotations

from PySide6.QtCore import Qt, QTimer, Signal
from PySide6.QtGui import QTextCharFormat, QTextCursor, QTextDocument
from PySide6.QtWidgets import (
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QVBoxLayout,
    QWidget,
)

from storage.database import SearchHit


class SearchPanel(QWidget):
    query_changed = Signal(str)
    result_activated = Signal(str)  # entry id

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.title = QLabel("搜索")
        self.title.setObjectName("sectionLabel")

        self.search_box = QLineEdit()
        self.search_box.setObjectName("searchBox")
        self.search_box.setPlaceholderText("搜索日记内容…")
        self.search_box.setClearButtonEnabled(True)

        self.results = QListWidget()
        self.results.itemClicked.connect(self._on_click)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 4, 16, 8)
        layout.setSpacing(8)
        layout.addWidget(self.title)
        layout.addWidget(self.search_box)
        layout.addWidget(self.results, 1)

        self._debounce = QTimer(self)
        self._debounce.setSingleShot(True)
        self._debounce.setInterval(220)
        self._debounce.timeout.connect(self._emit_query)
        self.search_box.textChanged.connect(lambda _: self._debounce.start())

    def _emit_query(self) -> None:
        self.query_changed.emit(self.search_box.text().strip())

    def show_results(self, hits: list[SearchHit], keyword: str) -> None:
        self.results.clear()
        for hit in hits:
            title = hit.title or hit.entry_date
            snippet = hit.snippet.replace("«", "").replace("»", "") if hit.snippet else ""
            display = f"{hit.entry_date}  ·  {title}"
            if snippet:
                display += f"\n{snippet}"
            item = QListWidgetItem(display)
            item.setData(Qt.ItemDataRole.UserRole, hit.entry_id)
            item.setData(Qt.ItemDataRole.UserRole + 1, keyword)
            self.results.addItem(item)

    def _on_click(self, item: QListWidgetItem) -> None:
        entry_id = item.data(Qt.ItemDataRole.UserRole)
        if entry_id:
            self.result_activated.emit(entry_id)


def highlight_in_editor(editor_widget, keyword: str) -> None:
    """Extra selections highlight for search keyword inside QPlainTextEdit."""
    from PySide6.QtWidgets import QPlainTextEdit, QTextEdit

    if not keyword or not isinstance(editor_widget, QPlainTextEdit):
        editor_widget.setExtraSelections([])
        return

    selections: list[QTextEdit.ExtraSelection] = []
    fmt = QTextCharFormat()
    fmt.setBackground(Qt.GlobalColor.yellow)
    fmt.setForeground(Qt.GlobalColor.black)

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
