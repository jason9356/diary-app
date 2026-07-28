"""
Markdown diary editor with live rendered preview.
Modes: edit | preview | split
"""
from __future__ import annotations

from pathlib import Path
from typing import Literal, Optional

from PySide6.QtCore import Qt, QTimer, Signal
from PySide6.QtGui import QDragEnterEvent, QDropEvent, QKeySequence, QTextCursor
from PySide6.QtWidgets import (
    QHBoxLayout,
    QLabel,
    QPlainTextEdit,
    QSplitter,
    QTextBrowser,
    QToolButton,
    QVBoxLayout,
    QWidget,
)

from ui.image_strip import ImageFilmstrip
from ui.styles import LIGHT
from utils.markdown_render import md_to_html

EditorMode = Literal["edit", "preview", "split"]


class DiaryEditor(QWidget):
    """Markdown source editor + HTML preview."""

    content_changed = Signal()
    save_requested = Signal()
    image_dropped = Signal(str)  # local filesystem path
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
        self._mode: EditorMode = "split"

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
        self.filmstrip.setVisible(False)  # display deferred; upload entry kept

        self.editor = QPlainTextEdit()
        self.editor.setObjectName("diaryEditor")
        self.editor.setPlaceholderText("写点什么…（支持 Markdown，可拖入或点「图片」插入）")
        self.editor.setAcceptDrops(False)
        self.editor.setTabChangesFocus(False)
        self.editor.setLineWrapMode(QPlainTextEdit.LineWrapMode.WidgetWidth)

        self.preview = QTextBrowser()
        self.preview.setObjectName("diaryPreview")
        self.preview.setOpenExternalLinks(True)
        self.preview.setOpenLinks(True)
        self.preview.setFrameShape(QTextBrowser.Shape.NoFrame)

        self._splitter = QSplitter(Qt.Orientation.Horizontal)
        self._splitter.setChildrenCollapsible(False)
        self._splitter.addWidget(self.editor)
        self._splitter.addWidget(self.preview)
        self._splitter.setStretchFactor(0, 1)
        self._splitter.setStretchFactor(1, 1)

        self.setAcceptDrops(True)
        toolbar = self._build_toolbar()

        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 20, 28, 20)
        layout.setSpacing(10)
        header = QVBoxLayout()
        header.setSpacing(2)
        header.addWidget(self.date_label)
        header.addWidget(self.context_label)
        header.addWidget(self.meta_label)
        layout.addLayout(header)
        layout.addWidget(self.filmstrip)
        layout.addWidget(toolbar)
        layout.addWidget(self._splitter, 1)

        self._save_timer = QTimer(self)
        self._save_timer.setSingleShot(True)
        self._save_timer.setInterval(autosave_ms)
        self._save_timer.timeout.connect(self._emit_save)

        self._preview_timer = QTimer(self)
        self._preview_timer.setSingleShot(True)
        self._preview_timer.setInterval(120)
        self._preview_timer.timeout.connect(self.refresh_preview)

        self.editor.textChanged.connect(self._on_text_changed)
        self.editor.installEventFilter(self)
        self.set_mode("split")

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

        btn("H1", "标题 1", lambda: self._wrap_line("# "))
        btn("H2", "标题 2", lambda: self._wrap_line("## "))
        btn("B", "加粗 Ctrl+B", lambda: self._wrap_selection("**", "**"))
        btn("I", "斜体 Ctrl+I", lambda: self._wrap_selection("*", "*"))
        btn("•", "无序列表", lambda: self._wrap_line("- "))
        btn("1.", "有序列表", lambda: self._wrap_line("1. "))
        btn("“", "引用", lambda: self._wrap_line("> "))
        btn("</>", "代码块", lambda: self._insert_block("```\n", "\n```"))
        btn("图片", "插入图片（也可拖入编辑区）", self.image_pick_requested.emit)

        row.addSpacing(12)

        self.btn_edit = QToolButton()
        self.btn_edit.setText("编辑")
        self.btn_edit.setCheckable(True)
        self.btn_edit.setToolTip("仅编辑 Markdown 源码")
        self.btn_edit.clicked.connect(lambda: self.set_mode("edit"))
        row.addWidget(self.btn_edit)

        self.btn_split = QToolButton()
        self.btn_split.setText("分栏")
        self.btn_split.setCheckable(True)
        self.btn_split.setToolTip("左侧源码 · 右侧渲染")
        self.btn_split.clicked.connect(lambda: self.set_mode("split"))
        row.addWidget(self.btn_split)

        self.btn_preview = QToolButton()
        self.btn_preview.setText("预览")
        self.btn_preview.setCheckable(True)
        self.btn_preview.setToolTip("仅显示渲染结果")
        self.btn_preview.clicked.connect(lambda: self.set_mode("preview"))
        row.addWidget(self.btn_preview)

        row.addStretch(1)
        return bar

    def set_data_root(self, path: Path) -> None:
        self._data_root = Path(path)
        self.filmstrip.set_data_root(self._data_root)
        self.refresh_preview()

    def set_theme_palette(self, palette: dict[str, str], mono: bool = False) -> None:
        from utils.fonts import emphasis_font

        self._palette = dict(palette)
        self._mono = mono
        self.date_label.setFont(emphasis_font(pixel_size=26, bold=True))
        self.refresh_preview()

    def set_mode(self, mode: EditorMode) -> None:
        self._mode = mode
        self.btn_edit.setChecked(mode == "edit")
        self.btn_split.setChecked(mode == "split")
        self.btn_preview.setChecked(mode == "preview")
        self.editor.setVisible(mode in ("edit", "split"))
        self.preview.setVisible(mode in ("preview", "split"))
        if mode == "split":
            self._splitter.setSizes([500, 500])
        elif mode == "edit":
            self._splitter.setSizes([1, 0])
        else:
            self._splitter.setSizes([0, 1])
            self.refresh_preview()
        if mode != "preview":
            self.focus_editor()
        self.mode_changed.emit(mode)

    def mode(self) -> EditorMode:
        return self._mode

    def eventFilter(self, obj, event):  # noqa: N802
        from PySide6.QtCore import QEvent

        if obj is self.editor:
            if event.type() == QEvent.Type.FocusIn:
                self.focus_changed.emit(True)
            elif event.type() == QEvent.Type.FocusOut:
                self.focus_changed.emit(False)
            elif event.type() == QEvent.Type.KeyPress:
                if event.matches(QKeySequence.StandardKey.Bold):
                    self._wrap_selection("**", "**")
                    return True
                if event.matches(QKeySequence.StandardKey.Italic):
                    self._wrap_selection("*", "*")
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
        # Image preview deferred — keep upload entry, hide filmstrip.
        self.filmstrip.set_images([])
        self.filmstrip.setVisible(False)

    def set_markdown(self, text: str) -> None:
        self._loading = True
        self.editor.setPlainText(text)
        self._loading = False
        self._dirty = False
        cursor = self.editor.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.End)
        self.editor.setTextCursor(cursor)
        self.refresh_preview()

    def markdown(self) -> str:
        return self.editor.toPlainText()

    def focus_editor(self) -> None:
        if self.editor.isVisible():
            self.editor.setFocus(Qt.FocusReason.OtherFocusReason)

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
        if not self.preview.isVisible() and self._mode == "edit":
            return
        html_doc = md_to_html(
            self.editor.toPlainText(),
            base_dir=self._data_root,
            palette=self._palette,
            mono=self._mono,
        )
        bar = self.preview.verticalScrollBar()
        pos = bar.value()
        self.preview.setHtml(html_doc)
        self.preview.setSearchPaths([str(self._data_root)])
        bar.setValue(pos)

    def _on_text_changed(self) -> None:
        if self._loading:
            return
        self._dirty = True
        self.content_changed.emit()
        self._save_timer.start()
        if self.preview.isVisible():
            self._preview_timer.start()

    def _emit_save(self) -> None:
        self.save_requested.emit()

    def _on_film_click(self, rel: str) -> None:
        needle = f"]({rel})"
        text = self.editor.toPlainText()
        idx = text.find(needle)
        if idx >= 0:
            if self._mode == "preview":
                self.set_mode("split")
            cursor = self.editor.textCursor()
            cursor.setPosition(idx)
            self.editor.setTextCursor(cursor)
            self.editor.setFocus()
            self.editor.centerCursor()
        else:
            # Asset exists but not yet in markdown — insert reference.
            self.insert_image_markdown(rel)

    def _wrap_selection(self, left: str, right: str) -> None:
        if self._mode == "preview":
            self.set_mode("split")
        cursor = self.editor.textCursor()
        if cursor.hasSelection():
            selected = cursor.selectedText().replace("\u2029", "\n")
            cursor.insertText(f"{left}{selected}{right}")
        else:
            cursor.insertText(f"{left}{right}")
            cursor.movePosition(QTextCursor.MoveOperation.Left, n=len(right))
            self.editor.setTextCursor(cursor)
        self.editor.setFocus()

    def _wrap_line(self, prefix: str) -> None:
        if self._mode == "preview":
            self.set_mode("split")
        cursor = self.editor.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.StartOfBlock)
        cursor.insertText(prefix)
        self.editor.setFocus()

    def _insert_block(self, before: str, after: str) -> None:
        if self._mode == "preview":
            self.set_mode("split")
        cursor = self.editor.textCursor()
        selected = cursor.selectedText().replace("\u2029", "\n") if cursor.hasSelection() else ""
        cursor.insertText(f"{before}{selected}{after}")
        if not selected:
            cursor.movePosition(QTextCursor.MoveOperation.Left, n=len(after))
            self.editor.setTextCursor(cursor)
        self.editor.setFocus()

    def insert_image_markdown(self, rel_path: str) -> None:
        if self._mode == "preview":
            self.set_mode("split")
        cursor = self.editor.textCursor()
        name = Path(rel_path).name
        cursor.insertText(f"\n![{name}]({rel_path})\n")
        self.editor.setFocus()
        self.refresh_preview()

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
