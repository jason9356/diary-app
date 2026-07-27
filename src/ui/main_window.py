"""
Main application window — sidebar (calendar / timeline / search) + editor.
"""
from __future__ import annotations

import logging
from datetime import datetime
from pathlib import Path

from PySide6.QtCore import QObject, Qt, QThread, Signal
from PySide6.QtGui import QAction, QGuiApplication, QKeySequence
from PySide6.QtWidgets import (
    QDialog,
    QDialogButtonBox,
    QDoubleSpinBox,
    QFileDialog,
    QFormLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
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
from app.weather import WeatherSnapshot, fetch_desktop
from app.writing_timer import WritingTimer
from ui.calendar_view import CalendarPanel
from ui.editor import DiaryEditor
from ui.search_panel import SearchPanel, highlight_in_editor
from ui.styles import resolve_palette, build_stylesheet
from ui.timeline_view import TimelinePanel

logger = logging.getLogger("diary.ui.main")


class _WeatherWorker(QObject):
    finished = Signal(object)

    def __init__(self, city: str) -> None:
        super().__init__()
        self._city = city

    def run(self) -> None:
        try:
            self.finished.emit(fetch_desktop(self._city))
        except Exception as exc:  # noqa: BLE001
            logger.warning("Weather worker failed: %s", exc)
            self.finished.emit(None)


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
        self._context_source: str = ""
        self._weather_thread: QThread | None = None

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

        # Sidebar — slim rail; calendar keeps a fixed height so it never balloons.
        sidebar = QWidget()
        sidebar.setObjectName("sidebar")
        sidebar.setMinimumWidth(228)
        sidebar.setMaximumWidth(300)
        side_layout = QVBoxLayout(sidebar)
        side_layout.setContentsMargins(0, 14, 0, 10)
        side_layout.setSpacing(8)

        brand = QLabel("日记")
        brand.setObjectName("brandMark")
        brand.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.brand_label = brand
        brand_wrap = QHBoxLayout()
        brand_wrap.setContentsMargins(18, 0, 18, 0)
        brand_wrap.addWidget(brand)
        side_layout.addLayout(brand_wrap)

        nav = QHBoxLayout()
        nav.setContentsMargins(14, 0, 14, 2)
        nav.setSpacing(2)
        self.btn_cal = QPushButton("日历")
        self.btn_time = QPushButton("时间线")
        self.btn_search = QPushButton("搜索")
        for b in (self.btn_cal, self.btn_time, self.btn_search):
            b.setObjectName("navTab")
            b.setCheckable(True)
            b.setCursor(Qt.CursorShape.PointingHandCursor)
            b.setFlat(True)
            nav.addWidget(b, 1)
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
        tools.setContentsMargins(12, 6, 12, 4)
        tools.setSpacing(2)
        self.btn_theme = QPushButton("主题")
        self.btn_font = QPushButton("字体")
        self.btn_export = QPushButton("导出")
        for b in (self.btn_theme, self.btn_font, self.btn_export):
            b.setObjectName("toolBtn")
            b.setCursor(Qt.CursorShape.PointingHandCursor)
            b.setFlat(True)
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
        side_w = min(max(int(self.config.sidebar_width), 228), 300)
        splitter.setSizes([side_w, max(600, self.config.window_width - side_w)])
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

        act_font = QAction("切换紧凑字号", self)
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

        view_menu.addSeparator()
        act_img = QAction("插入图片…", self)
        act_img.setShortcut(QKeySequence("Ctrl+Shift+I"))
        act_img.triggered.connect(self.pick_image)
        view_menu.addAction(act_img)
        act_wx = QAction("获取天气", self)
        act_wx.triggered.connect(lambda: self.fetch_weather(force_prompt=False))
        view_menu.addAction(act_wx)
        act_ctx = QAction("编辑地点 / 天气…", self)
        act_ctx.triggered.connect(self.edit_context)
        view_menu.addAction(act_ctx)

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
        self.editor.image_pick_requested.connect(self.pick_image)
        self.editor.context_clicked.connect(self._on_context_clicked)
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
        from utils.fonts import emphasis_font

        palette = resolve_palette(self.config.theme, self._system_dark())
        mono = self.config.font_mode == "mono"
        self.setStyleSheet(build_stylesheet(palette, mono=mono))
        self.calendar_panel.apply_palette(palette)
        self.editor.set_theme_palette(palette, mono=mono)
        # WenKai is Regular-only — setBold() triggers algorithmic emboldening.
        self.brand_label.setFont(emphasis_font(pixel_size=22, bold=True))
        self.calendar_panel.title.setFont(emphasis_font(pixel_size=13, bold=True))
        self.timeline_panel.title.setFont(emphasis_font(pixel_size=13, bold=True))
        self.search_panel.title.setFont(emphasis_font(pixel_size=13, bold=True))
        for btn in (self.btn_cal, self.btn_time, self.btn_search):
            btn.setFont(emphasis_font(pixel_size=14, bold=btn.isChecked()))
        theme_label = {"system": "跟随系统", "light": "浅色", "dark": "深色"}.get(
            self.config.theme, self.config.theme
        )
        self.btn_theme.setText(f"主题·{theme_label}")
        self.btn_font.setText("字号·紧" if mono else "字号·常")

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
        from utils.fonts import emphasis_font

        self.btn_cal.setChecked(which == "cal")
        self.btn_time.setChecked(which == "time")
        self.btn_search.setChecked(which == "search")
        for btn in (self.btn_cal, self.btn_time, self.btn_search):
            btn.setFont(emphasis_font(pixel_size=14, bold=btn.isChecked()))
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
        self._context_source = entry.context_source
        self.timer.start_session(entry.writing_duration_sec)

        heading = self._format_heading(entry_date)
        meta = self._format_meta(entry.created_at, entry.updated_at, entry.writing_duration_sec)
        self.editor.set_heading(heading, meta)
        self.editor.set_context(entry.location, entry.weather, entry.temp_c)
        self.editor.set_day_images(self.service.list_image_rels(entry_date))
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

        # Auto weather only when creating today's entry with empty context.
        if entry_date == self.service.today() and not (
            entry.location or entry.weather or entry.temp_c is not None
        ):
            self.fetch_weather(force_prompt=False, silent=True)

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
        self._context_source = entry.context_source
        self.editor.mark_clean()
        meta = self._format_meta(entry.created_at, entry.updated_at, entry.writing_duration_sec)
        self.editor.set_heading(self._format_heading(entry.entry_date), meta)
        self.editor.set_context(entry.location, entry.weather, entry.temp_c)
        self.editor.set_day_images(self.service.list_image_rels(entry.entry_date))
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
            self.editor.set_day_images(self.service.list_image_rels(self._current_date))
            self._set_status(f"已插入图片 {Path(rel).name}")
        except Exception as exc:  # noqa: BLE001
            logger.exception("Image drop failed")
            QMessageBox.warning(self, "插入图片失败", str(exc))

    def pick_image(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            self,
            "选择图片",
            str(Path.home()),
            "图片 (*.png *.jpg *.jpeg *.gif *.webp *.bmp)",
        )
        if path:
            self._on_image_dropped(path)

    def _on_context_clicked(self) -> None:
        entry = self.service.get_or_create(self._current_date)
        if entry.location or entry.weather or entry.temp_c is not None:
            self.edit_context()
        else:
            self.fetch_weather(force_prompt=True, silent=False)

    def fetch_weather(self, *, force_prompt: bool = False, silent: bool = False) -> None:
        if self._context_source == "phone":
            if not silent:
                QMessageBox.information(
                    self,
                    "天气",
                    "该日天气来自手机端，电脑端不会覆盖。",
                )
            return
        if self._weather_thread is not None and self._weather_thread.isRunning():
            return
        city = (self.config.weather_city or "").strip()
        if force_prompt and not city:
            city, ok = self._ask_city()
            if not ok:
                return
            if city:
                self.config.weather_city = city
                save_config(self.config)

        if not silent:
            self._set_status("正在获取天气…")

        thread = QThread(self)
        worker = _WeatherWorker(self.config.weather_city or city)
        worker.moveToThread(thread)
        thread.started.connect(worker.run)

        def on_done(snap: object) -> None:
            thread.quit()
            thread.wait(1000)
            self._weather_thread = None
            if not isinstance(snap, WeatherSnapshot):
                if not silent:
                    if not self.config.weather_city:
                        city2, ok = self._ask_city()
                        if ok and city2:
                            self.config.weather_city = city2
                            save_config(self.config)
                            self.fetch_weather(force_prompt=False, silent=silent)
                            return
                    QMessageBox.warning(
                        self,
                        "天气",
                        "未能获取天气。可在「编辑地点 / 天气」中手填，或设置城市后重试。",
                    )
                return
            self._apply_weather_snapshot(snap)

        worker.finished.connect(on_done)
        thread.finished.connect(worker.deleteLater)
        self._weather_thread = thread
        thread.start()

    def _ask_city(self) -> tuple[str, bool]:
        from PySide6.QtWidgets import QInputDialog

        text, ok = QInputDialog.getText(
            self,
            "设置城市",
            "用于获取天气的城市（如：上海）：",
            text=self.config.weather_city or "",
        )
        return (text.strip(), bool(ok))

    def _apply_weather_snapshot(self, snap: WeatherSnapshot) -> None:
        if not self.config.weather_city and snap.location:
            # Persist a usable city label for next time (first segment).
            self.config.weather_city = snap.location.split("·")[0]
            save_config(self.config)
        entry = self.service.save_context(
            self._current_date,
            location=snap.location,
            weather=snap.weather,
            temp_c=snap.temp_c,
            context_source="desktop",
            body=self.editor.markdown(),
            writing_duration_sec=self.timer.seconds(),
            entry_id=self._entry_id,
            created_at=self._created_at,
        )
        if entry is None:
            return
        self._entry_id = entry.id
        self._created_at = entry.created_at
        self._context_source = entry.context_source
        self.editor.set_context(entry.location, entry.weather, entry.temp_c)
        self._set_status(f"天气已更新 · {entry.location} {entry.weather} {entry.temp_c:g}°")

    def edit_context(self) -> None:
        entry = self.service.get_or_create(self._current_date)
        dlg = QDialog(self)
        dlg.setWindowTitle("地点 / 天气")
        form = QFormLayout(dlg)
        loc = QLineEdit(entry.location)
        wx = QLineEdit(entry.weather)
        temp = QDoubleSpinBox()
        temp.setRange(-80, 60)
        temp.setDecimals(1)
        temp.setSuffix(" °C")
        if entry.temp_c is not None:
            temp.setValue(float(entry.temp_c))
        else:
            temp.setValue(20.0)
        form.addRow("地点", loc)
        form.addRow("天气", wx)
        form.addRow("温度", temp)
        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        fetch_btn = buttons.addButton("联网获取", QDialogButtonBox.ButtonRole.ActionRole)
        form.addRow(buttons)
        buttons.accepted.connect(dlg.accept)
        buttons.rejected.connect(dlg.reject)
        want_fetch = {"v": False}

        def do_fetch() -> None:
            want_fetch["v"] = True
            dlg.accept()

        fetch_btn.clicked.connect(do_fetch)
        if dlg.exec() != QDialog.DialogCode.Accepted:
            return
        if want_fetch["v"]:
            self.fetch_weather(force_prompt=True, silent=False)
            return
        saved = self.service.save_context(
            self._current_date,
            location=loc.text().strip(),
            weather=wx.text().strip(),
            temp_c=float(temp.value()),
            context_source="manual",
            body=self.editor.markdown(),
            writing_duration_sec=self.timer.seconds(),
            entry_id=self._entry_id,
            created_at=self._created_at,
            force=self._context_source != "phone",
        )
        if saved is None:
            QMessageBox.information(self, "地点 / 天气", "该日数据来自手机端，未覆盖。")
            return
        self._entry_id = saved.id
        self._context_source = saved.context_source
        self.editor.set_context(saved.location, saved.weather, saved.temp_c)
        self._set_status("地点 / 天气已保存")

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
        def hhmm(iso: str) -> str:
            # ISO …T23:45:12 → 23:45；日期已在标题里，不必再写一遍。
            if not iso:
                return "—"
            t = iso.replace("T", " ")
            if " " in t and len(t) >= 16:
                return t[11:16]
            return t[:5] if len(t) >= 5 else t

        mins, secs = divmod(max(0, int(duration)), 60)
        if mins and secs:
            dur = f"{mins}′{secs:02d}″"
        elif mins:
            dur = f"{mins}′"
        else:
            dur = f"{secs}″"
        return f"{hhmm(created)} 创建 · {hhmm(updated)} 修改 · 写了 {dur}"

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
