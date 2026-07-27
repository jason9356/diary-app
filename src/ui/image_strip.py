"""
Horizontal filmstrip of the day's images.
"""
from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)


class ImageFilmstrip(QWidget):
    """Rounded thumbs for assets belonging to the open day."""

    image_clicked = Signal(str)  # relative path from data root
    add_requested = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self._data_root = Path(".")
        self.setObjectName("imageFilmstrip")
        self.setFixedHeight(88)

        self._scroll = QScrollArea()
        self._scroll.setWidgetResizable(True)
        self._scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self._scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self._scroll.setFrameShape(QFrame.Shape.NoFrame)
        self._scroll.setStyleSheet("QScrollArea { background: transparent; border: none; }")

        self._inner = QWidget()
        self._row = QHBoxLayout(self._inner)
        self._row.setContentsMargins(0, 4, 0, 4)
        self._row.setSpacing(10)
        self._row.addStretch(1)
        self._scroll.setWidget(self._inner)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        layout.addWidget(self._scroll)
        self.hide()

    def set_data_root(self, path: Path) -> None:
        self._data_root = Path(path)

    def set_images(self, rel_paths: list[str]) -> None:
        while self._row.count():
            item = self._row.takeAt(0)
            w = item.widget()
            if w is not None:
                w.deleteLater()

        if not rel_paths:
            self.hide()
            return

        for rel in rel_paths:
            thumb = self._make_thumb(rel)
            self._row.addWidget(thumb, 0, Qt.AlignmentFlag.AlignLeft)
        self._row.addStretch(1)
        self.show()

    def _make_thumb(self, rel: str) -> QLabel:
        label = QLabel()
        label.setObjectName("filmThumb")
        label.setFixedSize(72, 72)
        label.setCursor(Qt.CursorShape.PointingHandCursor)
        label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        label.setToolTip(Path(rel).name)
        label.setProperty("rel_path", rel)
        abs_path = self._data_root / rel
        pix = QPixmap(str(abs_path))
        if not pix.isNull():
            scaled = pix.scaled(
                72,
                72,
                Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                Qt.TransformationMode.SmoothTransformation,
            )
            x = max(0, (scaled.width() - 72) // 2)
            y = max(0, (scaled.height() - 72) // 2)
            label.setPixmap(scaled.copy(x, y, 72, 72))
        else:
            label.setText("图")
        label.mousePressEvent = lambda ev, r=rel: self.image_clicked.emit(r)  # type: ignore[method-assign]
        return label
