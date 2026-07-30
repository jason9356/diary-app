"""Todos / 事项 panel — local-first vault todos."""
from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QCheckBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)

from storage.native_todo_store import NativeTodo, NativeTodoStore


class TodoPanel(QWidget):
    changed = Signal()

    def __init__(self, store: NativeTodoStore, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.store = store
        self.title = QLabel("事项")
        self.title.setObjectName("sectionLabel")

        self.input = QLineEdit()
        self.input.setObjectName("searchBox")
        self.input.setPlaceholderText("新事项…")
        self.input.returnPressed.connect(self._add)

        self.btn_add = QPushButton("添加")
        self.btn_add.setObjectName("primaryBtn")
        self.btn_add.clicked.connect(self._add)

        row = QHBoxLayout()
        row.setSpacing(6)
        row.addWidget(self.input, 1)
        row.addWidget(self.btn_add)

        self.list_host = QWidget()
        self.list_layout = QVBoxLayout(self.list_host)
        self.list_layout.setContentsMargins(0, 0, 0, 0)
        self.list_layout.setSpacing(8)
        self.list_layout.addStretch(1)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QScrollArea.Shape.NoFrame)
        scroll.setWidget(self.list_host)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 4, 14, 8)
        layout.setSpacing(8)
        layout.addWidget(self.title)
        layout.addLayout(row)
        layout.addWidget(scroll, 1)

        self.refresh()

    def refresh(self) -> None:
        while self.list_layout.count() > 1:
            item = self.list_layout.takeAt(0)
            w = item.widget()
            if w:
                w.deleteLater()
        for todo in self.store.list():
            self.list_layout.insertWidget(self.list_layout.count() - 1, self._row(todo))

    def _row(self, todo: NativeTodo) -> QWidget:
        wrap = QWidget()
        row = QHBoxLayout(wrap)
        row.setContentsMargins(4, 2, 4, 2)
        row.setSpacing(8)

        check = QCheckBox()
        check.setChecked(todo.done)
        check.toggled.connect(lambda done, tid=todo.id: self._toggle(tid, done))

        label = QLabel(todo.text or "（空）")
        label.setWordWrap(True)
        if todo.done:
            label.setStyleSheet("color: palette(mid); text-decoration: line-through;")

        btn_del = QPushButton("删")
        btn_del.setObjectName("toolBtn")
        btn_del.setFlat(True)
        btn_del.clicked.connect(lambda _=False, tid=todo.id: self._delete(tid))

        row.addWidget(check, 0)
        row.addWidget(label, 1)
        row.addWidget(btn_del, 0)
        return wrap

    def _add(self) -> None:
        text = self.input.text().strip()
        if not text:
            return
        self.store.add(text)
        self.input.clear()
        self.refresh()
        self.changed.emit()

    def _toggle(self, todo_id: str, done: bool) -> None:
        self.store.set_done(todo_id, done)
        self.refresh()
        self.changed.emit()

    def _delete(self, todo_id: str) -> None:
        self.store.delete(todo_id)
        self.refresh()
        self.changed.emit()
