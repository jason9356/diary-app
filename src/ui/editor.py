"""
WYSIWYG Markdown editor (QTextEdit + Qt markdown round-trip).
Persists CommonMark via toMarkdown() / setMarkdown().
"""
from __future__ import annotations

from pathlib import Path
from typing import Literal, Optional

from PySide6.QtCore import Qt, QTimer, Signal
from PySide6.QtGui import (
    QDragEnterEvent,
    QDropEvent,
    QFont,
    QKeySequence,
    QTextCharFormat,
    QTextCursor,
    QTextListFormat,
)
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QTextEdit,
    QToolButton,
    QVBoxLayout,
    QWidget,
)

from ui.image_strip import ImageFilmstrip
from ui.styles import LIGHT

EditorMode = Literal["wysiwyg", "source"]


class DiaryEditor(QWidget):
    content_changed = Signal()
    save_requested = Signal()
    image_dropped = Signal(str)
    image_pick_requested = Signal()
    focus_changed = Signal(bool)
    mode_changed = Signal(str)
    context_clicked = Signal()

    def __init__(
        self,
        autosave_ms: int = 600,
        data_root: Path | None = None,
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self._loading = False
        self._dirty = False
        self._data_root = Path(data_root) if data_root else Path(".")
        self._palette = dict(LIGHT)
        self._mono = False
        self._mode: EditorMode = "wysiwyg"
        self._source_cache = ""

        self.date_label = QLabel("")
        self.date_label.setObjectName("dateHeading")
        self.context_label = QLabel("")
        self.context_label.setObjectName("contextLabel")
        self.context_label.setCursor(Qt.CursorShape.PointingHandCursor)
        self.context_label.setToolTip("点击编辑或获取地点 / 天气")
        self.context_label.mousePressEvent = (  # type: ignore[method-assign]
            lambda ev: self.context_clicked.emit()
        )
        self.meta_label = QLabel("")
        self.meta_label.setObjectName("metaLabel")

        self.filmstrip = ImageFilmstrip()
        self.filmstrip.set_data_root(self._data_root)
        self.filmstrip.image_clicked.connect(self._on_film_click)
        self.filmstrip.setVisible(False)

        self.editor = QTextEdit()
        self.editor.setObjectName("diaryEditor")
        self.editor.setAcceptRichText(True)
        self.editor.setPlaceholderText("正文")
        self.editor.setTabChangesFocus(False)
        self.editor.setAcceptDrops(False)

        self.source = QTextEdit()
        self.source.setObjectName("diaryEditor")
        self.source.setAcceptRichText(False)
        self.source.setPlaceholderText("Markdown 源码")
        self.source.setVisible(False)

        self.setAcceptDrops(True)
        toolbar = self._build_toolbar()

        paper = QFrame()
        paper.setObjectName("paperSheet")
        paper_layout = QVBoxLayout(paper)
        paper_layout.setContentsMargins(22, 20, 22, 20)
        paper_layout.setSpacing(10)
        header = QVBoxLayout()
        header.setSpacing(2)
        header.addWidget(self.date_label)
        header.addWidget(self.context_label)
        header.addWidget(self.meta_label)
        paper_layout.addLayout(header)
        paper_layout.addWidget(self.filmstrip)
        paper_layout.addWidget(toolbar)
        paper_layout.addWidget(self.editor, 1)
        paper_layout.addWidget(self.source, 1)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 12, 20, 16)
        layout.setSpacing(0)
        layout.addWidget(paper, 1)

        self._save_timer = QTimer(self)
        self._save_timer.setSingleShot(True)
        self._save_timer.setInterval(autosave_ms)
        self._save_timer.timeout.connect(self._emit_save)

        self.editor.textChanged.connect(self._on_text_changed)
        self.source.textChanged.connect(self._on_text_changed)
        self.editor.installEventFilter(self)
        self.source.installEventFilter(self)
        self._apply_editor_chrome()

    def _build_toolbar(self) -> QWidget:
        bar = QWidget()
        row = QHBoxLayout(bar)
        row.setContentsMargins(0, 0, 0, 0)
        row.setSpacing(2)

        def btn(text: str, tip: str, slot) -> QToolButton:
            b = QToolButton()
            b.setText(text)
            b.setToolTip(tip)
            b.clicked.connect(slot)
            row.addWidget(b)
            return b

        btn("H2", "二级标题", lambda: self._set_heading(2))
        btn("H3", "三级标题", lambda: self._set_heading(3))
        btn("B", "加粗 Ctrl+B", self._toggle_bold)
        btn("I", "斜体 Ctrl+I", self._toggle_italic)
        btn("•", "无序列表", lambda: self._toggle_list(QTextListFormat.Style.ListDisc))
        btn("1.", "有序列表", lambda: self._toggle_list(QTextListFormat.Style.ListDecimal))
        btn("图片", "插入图片（也可拖入）", self.image_pick_requested.emit)

        row.addSpacing(12)
        self.btn_wysiwyg = QToolButton()
        self.btn_wysiwyg.setText("所见即所得")
        self.btn_wysiwyg.setCheckable(True)
        self.btn_wysiwyg.setChecked(True)
        self.btn_wysiwyg.clicked.connect(lambda: self.set_mode("wysiwyg"))
        row.addWidget(self.btn_wysiwyg)

        self.btn_source = QToolButton()
        self.btn_source.setText("源码")
        self.btn_source.setCheckable(True)
        self.btn_source.clicked.connect(lambda: self.set_mode("source"))
        row.addWidget(self.btn_source)

        row.addStretch(1)
        return bar

    def set_data_root(self, path: Path) -> None:
        self._data_root = Path(path)
        self.filmstrip.set_data_root(self._data_root)

    def set_theme_palette(self, palette: dict[str, str], mono: bool = False) -> None:
        from utils.fonts import emphasis_font

        self._palette = dict(palette)
        self._mono = mono
        self.date_label.setFont(emphasis_font(pixel_size=24, bold=True))
        self._apply_editor_chrome()

    def _apply_editor_chrome(self) -> None:
        size = 15 if self._mono else 16
        font = QFont(self.editor.font())
        font.setPixelSize(size)
        self.editor.setFont(font)
        self.source.setFont(font)
        color = self._palette.get("text", "#111827")
        self.editor.setStyleSheet(
            f"QTextEdit#diaryEditor {{ color: {color}; background: transparent; border: none; }}"
        )
        self.source.setStyleSheet(
            f"QTextEdit#diaryEditor {{ color: {color}; background: transparent; border: none; }}"
        )

    def set_mode(self, mode: EditorMode | str) -> None:
        if mode in ("edit", "split", "preview"):
            mode = "wysiwyg"
        if mode not in ("wysiwyg", "source"):
            mode = "wysiwyg"
        if self._mode == "wysiwyg" and mode == "source":
            self._source_cache = self.editor.toMarkdown()
            self.source.blockSignals(True)
            self.source.setPlainText(self._source_cache)
            self.source.blockSignals(False)
        elif self._mode == "source" and mode == "wysiwyg":
            md = self.source.toPlainText()
            self.editor.blockSignals(True)
            self.editor.setMarkdown(md)
            self.editor.blockSignals(False)
        self._mode = mode  # type: ignore[assignment]
        self.btn_wysiwyg.setChecked(mode == "wysiwyg")
        self.btn_source.setChecked(mode == "source")
        self.editor.setVisible(mode == "wysiwyg")
        self.source.setVisible(mode == "source")
        self.focus_editor()
        self.mode_changed.emit(mode)

    def mode(self) -> str:
        return self._mode

    def eventFilter(self, obj, event):  # noqa: N802
        from PySide6.QtCore import QEvent

        if obj in (self.editor, self.source):
            if event.type() == QEvent.Type.FocusIn:
                self.focus_changed.emit(True)
            elif event.type() == QEvent.Type.FocusOut:
                self.focus_changed.emit(False)
            elif event.type() == QEvent.Type.KeyPress and obj is self.editor:
                if event.matches(QKeySequence.StandardKey.Bold):
                    self._toggle_bold()
                    return True
                if event.matches(QKeySequence.StandardKey.Italic):
                    self._toggle_italic()
                    return True
        return super().eventFilter(obj, event)

    def set_heading(self, title: str, meta: str) -> None:
        self.date_label.setText(title)
        self.meta_label.setText(meta)

    def set_context(
        self,
        location: str = "",
        weather: str = "",
        temp_c: Optional[float] = None,
        *,
        placeholder: str = "获取天气",
    ) -> None:
        parts: list[str] = []
        if location.strip():
            parts.append(location.strip())
        wx = weather.strip()
        if temp_c is not None:
            temp = f"{temp_c:g}°"
            parts.append(f"{wx} {temp}".strip() if wx else temp)
        elif wx:
            parts.append(wx)
        if parts:
            self.context_label.setText(" · ".join(parts))
            self.context_label.setProperty("empty", False)
        else:
            self.context_label.setText(placeholder)
            self.context_label.setProperty("empty", True)
        self.context_label.style().unpolish(self.context_label)
        self.context_label.style().polish(self.context_label)

    def set_day_images(self, rel_paths: list[str]) -> None:
        self.filmstrip.set_images([])
        self.filmstrip.setVisible(False)

    def set_markdown(self, text: str) -> None:
        self._loading = True
        md = text or ""
        self.editor.setMarkdown(md)
        self.source.setPlainText(md)
        self._loading = False
        self._dirty = False
        cursor = self.editor.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.End)
        self.editor.setTextCursor(cursor)

    def markdown(self) -> str:
        if self._mode == "source":
            return self.source.toPlainText()
        return self.editor.toMarkdown()

    def focus_editor(self) -> None:
        target = self.source if self._mode == "source" else self.editor
        if target.isVisible():
            target.setFocus(Qt.FocusReason.OtherFocusReason)

    def is_dirty(self) -> bool:
        return self._dirty

    def mark_clean(self) -> None:
        self._dirty = False

    def flush_autosave(self) -> None:
        if self._save_timer.isActive():
            self._save_timer.stop()
        if self._dirty:
            self._emit_save()

    def refresh_preview(self) -> None:
        # Kept for API compatibility; WYSIWYG has no separate preview pane.
        return

    def _on_text_changed(self) -> None:
        if self._loading:
            return
        self._dirty = True
        self.content_changed.emit()
        self._save_timer.start()

    def _emit_save(self) -> None:
        self.save_requested.emit()

    def _active(self) -> QTextEdit:
        return self.source if self._mode == "source" else self.editor

    def _toggle_bold(self) -> None:
        if self._mode == "source":
            self._wrap_source("**", "**")
            return
        fmt = QTextCharFormat()
        cursor = self.editor.textCursor()
        cur = cursor.charFormat().fontWeight()
        fmt.setFontWeight(
            QFont.Weight.Normal if cur > QFont.Weight.Normal else QFont.Weight.Bold
        )
        cursor.mergeCharFormat(fmt)
        self.editor.mergeCurrentCharFormat(fmt)
        self.editor.setFocus()

    def _toggle_italic(self) -> None:
        if self._mode == "source":
            self._wrap_source("*", "*")
            return
        fmt = QTextCharFormat()
        cursor = self.editor.textCursor()
        fmt.setFontItalic(not cursor.charFormat().fontItalic())
        cursor.mergeCharFormat(fmt)
        self.editor.mergeCurrentCharFormat(fmt)
        self.editor.setFocus()

    def _set_heading(self, level: int) -> None:
        if self._mode == "source":
            prefix = "#" * max(2, level) + " "
            self._wrap_source_line(prefix)
            return
        cursor = self.editor.textCursor()
        block_fmt = cursor.blockFormat()
        # Qt headingLevel: 0 = normal, 1..6 = headings. Prefer H2+ like Android.
        block_fmt.setHeadingLevel(max(2, level))
        cursor.setBlockFormat(block_fmt)
        char = QTextCharFormat()
        char.setFontWeight(QFont.Weight.DemiBold)
        char.setFontPointSize(18 if level <= 2 else 15)
        cursor.mergeBlockCharFormat(char)
        self.editor.setFocus()

    def _toggle_list(self, style: QTextListFormat.Style) -> None:
        if self._mode == "source":
            self._wrap_source_line("- " if style == QTextListFormat.Style.ListDisc else "1. ")
            return
        cursor = self.editor.textCursor()
        cursor.createList(style)
        self.editor.setFocus()

    def _wrap_source(self, left: str, right: str) -> None:
        cursor = self.source.textCursor()
        if cursor.hasSelection():
            selected = cursor.selectedText().replace("\u2029", "\n")
            cursor.insertText(f"{left}{selected}{right}")
        else:
            cursor.insertText(f"{left}{right}")
            cursor.movePosition(QTextCursor.MoveOperation.Left, n=len(right))
            self.source.setTextCursor(cursor)
        self.source.setFocus()

    def _wrap_source_line(self, prefix: str) -> None:
        cursor = self.source.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.StartOfBlock)
        cursor.insertText(prefix)
        self.source.setFocus()

    def _on_film_click(self, rel: str) -> None:
        self.insert_image_markdown(rel)

    def insert_image_markdown(self, rel_path: str) -> None:
        name = Path(rel_path).name
        snippet = f"\n![{name}]({rel_path})\n"
        if self._mode == "source":
            cursor = self.source.textCursor()
            cursor.insertText(snippet)
            self.source.setFocus()
        else:
            # Insert as markdown then re-parse keeps image placeholder text.
            md = self.editor.toMarkdown() + snippet
            self.editor.blockSignals(True)
            self.editor.setMarkdown(md)
            self.editor.blockSignals(False)
            self._dirty = True
            self.content_changed.emit()
            self._save_timer.start()
            self.editor.setFocus()

    def dragEnterEvent(self, event: QDragEnterEvent) -> None:  # noqa: N802
        if event.mimeData().hasUrls():
            for url in event.mimeData().urls():
                if url.isLocalFile():
                    ext = Path(url.toLocalFile()).suffix.lower()
                    if ext in {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"}:
                        event.acceptProposedAction()
                        return
        event.ignore()

    def dropEvent(self, event: QDropEvent) -> None:  # noqa: N802
        for url in event.mimeData().urls():
            if url.isLocalFile():
                path = url.toLocalFile()
                ext = Path(path).suffix.lower()
                if ext in {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"}:
                    self.image_dropped.emit(path)
        event.acceptProposedAction()
