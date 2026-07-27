"""
Main application window — sidebar (calendar / timeline / search) + editor.
"""
from __future__ import annotations

import logging
from datetime import datetime
from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtGui import QAction, QGuiApplication, QKeySequence
from PySide6.QtWidgets import (
    QFileDialog,
    QHBoxLayout,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QSplitter,
    QStatusBar,
    QVBoxLayout,
    QWidget,
)

from app.config import AppConfig, save_config
from app.diary_service import DiaryService
from app.writing_timer import WritingTimer
from ui.calendar_view import CalendarPanel
from ui.editor import DiaryEditor
from ui.search_panel import SearchPanel, highlight_in_editor
from ui.styles import resolve_palette, build_stylesheet
from ui.timeline_view import TimelinePanel

logger = logging.getLogger("diary.ui.main")


class MainWindow(QMainWindow):
    def __init__(self, config: AppConfig, service: DiaryService) -> None:
        super().__init__()
        self.config = config
        self.service = service
        self.timer = WritingTimer()
        self._current_date: str = service.today()
        self._entry_id: str = ""
        self._created_at: str = ""
        self._loading = False
        self._search_keyword = ""

        self.setWindowTitle("日记")
        self.setMinimumSize(config.min_window_width, config.min_window_height)
        self.resize(config.window_width, config.window_height)

        self._build_ui()
        self._build_menu()
        self._apply_theme()
        self._connect()
        self.open_date(self.service.today(), focus=True)
        self.refresh_sidebar()

    # ----- UI construction -----

    def _build_ui(self) -> None:
        root = QWidget()
        root.setObjectName("centralRoot")
        self.setCentralWidget(root)
        root_layout = QHBoxLayout(root)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)

        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.setHandleWidth(1)
        root_layout.addWidget(splitter)

        # Sidebar
        sidebar = QWidget()
        sidebar.setObjectName("sidebar")
        sidebar.setMinimumWidth(260)
        sidebar.setMaximumWidth(420)
        side_layout = QVBoxLayout(sidebar)
        side_layout.setContentsMargins(0, 8, 0, 8)
        side_layout.setSpacing(4)

        nav = QHBoxLayout()
        nav.setContentsMargins(12, 4, 12, 4)
        self.btn_cal = QPushButton("日历")
        self.btn_time = QPushButton("时间线")
        self.btn_search = QPushButton("搜索")
        for b in (self.btn_cal, self.btn_time, self.btn_search):
            b.setCheckable(True)
            nav.addWidget(b)
        self.btn_cal.setChecked(True)
        side_layout.addLayout(nav)

        self.calendar_panel = CalendarPanel()
        self.timeline_panel = TimelinePanel()
        self.search_panel = SearchPanel()
        side_layout.addWidget(self.calendar_panel, 1)
        side_layout.addWidget(self.timeline_panel, 1)
        side_layout.addWidget(self.search_panel, 1)
        self.timeline_panel.hide()
        self.search_panel.hide()

        tools = QHBoxLayout()
        tools.setContentsMargins(12, 4, 12, 8)
        self.btn_theme = QPushButton("主题")
        self.btn_font = QPushButton("字体")
        self.btn_export = QPushButton("导出")
        tools.addWidget(self.btn_theme)
        tools.addWidget(self.btn_font)
        tools.addStretch(1)
        tools.addWidget(self.btn_export)
        side_layout.addLayout(tools)

        # Editor pane
        editor_pane = QWidget()
        editor_pane.setObjectName("editorPane")
        editor_layout = QVBoxLayout(editor_pane)
        editor_layout.setContentsMargins(0, 0, 0, 0)
        self.editor = DiaryEditor(
            autosave_ms=self.config.autosave_ms,
            data_root=self.config.data_path,
        )
        editor_layout.addWidget(self.editor)

        splitter.addWidget(sidebar)
        splitter.addWidget(editor_pane)
        splitter.setStretchFactor(0, 0)
        splitter.setStretchFactor(1, 1)
        splitter.setSizes([self.config.sidebar_width, 800])
        self._splitter = splitter

        status = QStatusBar()
        self.setStatusBar(status)
        self._set_status("就绪 · 自动保存已开启")

    def _build_menu(self) -> None:
        file_menu = self.menuBar().addMenu("文件")
        act_today = QAction("今天的日记", self)
        act_today.setShortcut(QKeySequence("Ctrl+T"))
        act_today.triggered.connect(lambda: self.open_date(self.service.today(), focus=True))
        file_menu.addAction(act_today)

        act_export = QAction("导出 ZIP…", self)
        act_export.setShortcut(QKeySequence("Ctrl+E"))
        act_export.triggered.connect(self.export_zip)
        file_menu.addAction(act_export)

        file_menu.addSeparator()
        act_quit = QAction("退出", self)
        act_quit.setShortcut(QKeySequence("Ctrl+Q"))
        act_quit.triggered.connect(self.close)
        file_menu.addAction(act_quit)

        view_menu = self.menuBar().addMenu("视图")
        act_theme = QAction("切换深色/浅色", self)
        act_theme.setShortcut(QKeySequence("Ctrl+Shift+L"))
        act_theme.triggered.connect(self.cycle_theme)
        view_menu.addAction(act_theme)

        act_font = QAction("切换等宽字体", self)
        act_font.triggered.connect(self.toggle_font)
        view_menu.addAction(act_font)

        view_menu.addSeparator()
        act_edit = QAction("仅编辑", self)
        act_edit.setShortcut(QKeySequence("Ctrl+1"))
        act_edit.triggered.connect(lambda: self.editor.set_mode("edit"))
        view_menu.addAction(act_edit)
        act_split = QAction("分栏预览", self)
        act_split.setShortcut(QKeySequence("Ctrl+2"))
        act_split.triggered.connect(lambda: self.editor.set_mode("split"))
        view_menu.addAction(act_split)
        act_preview = QAction("仅预览", self)
        act_preview.setShortcut(QKeySequence("Ctrl+3"))
        act_preview.triggered.connect(lambda: self.editor.set_mode("preview"))
        view_menu.addAction(act_preview)

    def _connect(self) -> None:
        self.btn_cal.clicked.connect(lambda: self._show_panel("cal"))
        self.btn_time.clicked.connect(lambda: self._show_panel("time"))
        self.btn_search.clicked.connect(lambda: self._show_panel("search"))

        self.calendar_panel.date_selected.connect(self.open_date)
        self.calendar_panel.month_changed.connect(self._on_month_changed)

        self.timeline_panel.entry_activated.connect(self.open_date)
        self.timeline_panel.filter_changed.connect(self._on_timeline_filter)

        self.search_panel.query_changed.connect(self._on_search)
        self.search_panel.result_activated.connect(self._on_search_open)

        self.editor.save_requested.connect(self.save_current)
        self.editor.content_changed.connect(self._on_edit_activity)
        self.editor.image_dropped.connect(self._on_image_dropped)
        self.editor.focus_changed.connect(self._on_focus)
        self.editor.mode_changed.connect(self._on_editor_mode)

        self.btn_theme.clicked.connect(self.cycle_theme)
        self.btn_font.clicked.connect(self.toggle_font)
        self.btn_export.clicked.connect(self.export_zip)

        mode = self.config.editor_mode if self.config.editor_mode in ("edit", "preview", "split") else "split"
        self.editor.set_mode(mode)  # type: ignore[arg-type]

    # ----- Theme / font -----

    def _system_dark(self) -> bool:
        hints = QGuiApplication.styleHints()
        try:
            scheme = hints.colorScheme()
            from PySide6.QtCore import Qt as _Qt

            return scheme == _Qt.ColorScheme.Dark
        except Exception:  # noqa: BLE001
            return False

    def _apply_theme(self) -> None:
        palette = resolve_palette(self.config.theme, self._system_dark())
        mono = self.config.font_mode == "mono"
        self.setStyleSheet(build_stylesheet(palette, mono=mono))
        self.calendar_panel.set_dot_color(palette["dot"])
        self.editor.set_theme_palette(palette, mono=mono)
        theme_label = {"system": "跟随系统", "light": "浅色", "dark": "深色"}.get(
            self.config.theme, self.config.theme
        )
        self.btn_theme.setText(f"主题·{theme_label}")
        self.btn_font.setText("等宽" if mono else "系统字体")

    def _on_editor_mode(self, mode: str) -> None:
        if mode in ("edit", "preview", "split"):
            self.config.editor_mode = mode  # type: ignore[assignment]
            save_config(self.config)

    def cycle_theme(self) -> None:
        order = ["system", "light", "dark"]
        i = order.index(self.config.theme) if self.config.theme in order else 0
        self.config.theme = order[(i + 1) % len(order)]  # type: ignore[assignment]
        save_config(self.config)
        self._apply_theme()

    def toggle_font(self) -> None:
        self.config.font_mode = "mono" if self.config.font_mode == "system" else "system"
        save_config(self.config)
        self._apply_theme()

    # ----- Navigation -----

    def _show_panel(self, which: str) -> None:
        self.btn_cal.setChecked(which == "cal")
        self.btn_time.setChecked(which == "time")
        self.btn_search.setChecked(which == "search")
        self.calendar_panel.setVisible(which == "cal")
        self.timeline_panel.setVisible(which == "time")
        self.search_panel.setVisible(which == "search")
        if which == "time":
            self.refresh_timeline()

    def refresh_sidebar(self) -> None:
        dates = self.service.dates_with_content()
        self.calendar_panel.set_entry_dates(dates)
        self.calendar_panel.set_selected_date(self._current_date)
        self.timeline_panel.set_year_options(self.service.available_years())
        self.refresh_timeline()

    def refresh_timeline(self) -> None:
        year = self.timeline_panel.year_combo.currentData()
        month = self.timeline_panel.month_combo.currentData()
        entries = self.service.timeline(year=year, month=month)
        self.timeline_panel.populate(entries, self.service)
        self.timeline_panel.select_date(self._current_date)

    def _on_month_changed(self, year: int, month: int) -> None:
        dates = self.service.dates_with_content(year=year, month=month)
        # Keep all dates for dots across months user may navigate — use full set.
        self.calendar_panel.set_entry_dates(self.service.dates_with_content())

    def _on_timeline_filter(self, year, month) -> None:
        self.refresh_timeline()

    # ----- Entry load / save -----

    def open_date(self, entry_date: str, focus: bool = True) -> None:
        if entry_date == self._current_date and self.editor.is_dirty():
            self.save_current()
        elif self.editor.is_dirty():
            self.save_current()

        self._loading = True
        entry = self.service.get_or_create(entry_date)
        self._current_date = entry.entry_date
        self._entry_id = entry.id
        self._created_at = entry.created_at
        self.timer.start_session(entry.writing_duration_sec)

        heading = self._format_heading(entry_date)
        meta = self._format_meta(entry.created_at, entry.updated_at, entry.writing_duration_sec)
        self.editor.set_heading(heading, meta)
        self.editor.set_markdown(entry.body)
        self._loading = False

        self.calendar_panel.set_selected_date(entry_date)
        self.timeline_panel.select_date(entry_date)
        if self._search_keyword:
            highlight_in_editor(self.editor.editor, self._search_keyword)
        else:
            highlight_in_editor(self.editor.editor, "")

        if focus:
            self.editor.focus_editor()
        self._set_status(f"已打开 {entry_date}")

    def save_current(self) -> None:
        if self._loading:
            return
        body = self.editor.markdown()
        # Do not create empty files when browsing a blank day.
        existing = self.service.db.get_by_date(self._current_date)
        if not body.strip() and existing is None and not self.md_exists_on_disk():
            self.editor.mark_clean()
            return
        entry = self.service.save(
            entry_date=self._current_date,
            body=body,
            writing_duration_sec=self.timer.seconds(),
            entry_id=self._entry_id,
            created_at=self._created_at,
        )
        self._entry_id = entry.id
        self._created_at = entry.created_at
        self.editor.mark_clean()
        meta = self._format_meta(entry.created_at, entry.updated_at, entry.writing_duration_sec)
        self.editor.set_heading(self._format_heading(entry.entry_date), meta)
        # Refresh dots without heavy timeline rebuild every keystroke-save
        self.calendar_panel.set_entry_dates(self.service.dates_with_content())
        if self.timeline_panel.isVisible():
            self.refresh_timeline()
        self._set_status(f"已保存 · {datetime.now().strftime('%H:%M:%S')}")

    def md_exists_on_disk(self) -> bool:
        return self.service.md.exists(self._current_date)

    def _on_edit_activity(self) -> None:
        if self._loading:
            return
        self.timer.on_activity()

    def _on_focus(self, focused: bool) -> None:
        if focused:
            self.timer.on_focus()
        else:
            self.timer.on_blur()
            if self.editor.is_dirty():
                self.save_current()

    def _on_image_dropped(self, path: str) -> None:
        try:
            rel = self.service.save_dropped_image(self._current_date, Path(path))
            self.editor.insert_image_markdown(rel)
            self.save_current()
            self._set_status(f"已插入图片 {Path(rel).name}")
        except Exception as exc:  # noqa: BLE001
            logger.exception("Image drop failed")
            QMessageBox.warning(self, "插入图片失败", str(exc))

    # ----- Search -----

    def _on_search(self, query: str) -> None:
        self._search_keyword = query
        hits = self.service.search(query) if query else []
        self.search_panel.show_results(hits, query)
        highlight_in_editor(self.editor.editor, query)

    def _on_search_open(self, entry_date: str) -> None:
        self.open_date(entry_date, focus=True)
        highlight_in_editor(self.editor.editor, self._search_keyword)

    # ----- Export -----

    def export_zip(self) -> None:
        self.save_current()
        default_name = f"diary-export-{datetime.now().strftime('%Y%m%d')}.zip"
        path, _ = QFileDialog.getSaveFileName(
            self,
            "导出日记 ZIP",
            str(Path.home() / default_name),
            "ZIP 文件 (*.zip)",
        )
        if not path:
            return
        try:
            out = self.service.export(Path(path))
            QMessageBox.information(self, "导出完成", f"已导出到：\n{out}")
            self._set_status(f"已导出 {out.name}")
        except Exception as exc:  # noqa: BLE001
            logger.exception("Export failed")
            QMessageBox.critical(self, "导出失败", str(exc))

    # ----- Helpers -----

    @staticmethod
    def _format_heading(entry_date: str) -> str:
        dt = datetime.strptime(entry_date, "%Y-%m-%d")
        weekdays = "一二三四五六日"
        return f"{dt.strftime('%Y年%m月%d日')}  星期{weekdays[dt.weekday()]}"

    @staticmethod
    def _format_meta(created: str, updated: str, duration: int) -> str:
        mins = duration // 60
        secs = duration % 60
        dur = f"{mins} 分 {secs} 秒" if mins else f"{secs} 秒"
        c = created.replace("T", " ")[:19] if created else "—"
        u = updated.replace("T", " ")[:19] if updated else "—"
        return f"创建 {c}  ·  修改 {u}  ·  写作 {dur}"

    def _set_status(self, text: str) -> None:
        self.statusBar().showMessage(text)

    def closeEvent(self, event) -> None:  # noqa: N802
        try:
            self.editor.flush_autosave()
            self.save_current()
            self.config.window_width = max(self.width(), self.config.min_window_width)
            self.config.window_height = self.height()
            sizes = self._splitter.sizes()
            if sizes:
                self.config.sidebar_width = sizes[0]
            save_config(self.config)
        except Exception:  # noqa: BLE001
            logger.exception("Error during shutdown save")
        event.accept()
