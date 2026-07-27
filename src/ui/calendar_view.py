"""
Calendar widget with dots on days that have diary entries.
"""
from __future__ import annotations

from datetime import date

from PySide6.QtCore import QDate, QPoint, Qt, Signal
from PySide6.QtGui import QColor, QPainter
from PySide6.QtWidgets import QCalendarWidget, QLabel, QVBoxLayout, QWidget


class DotCalendar(QCalendarWidget):
    """QCalendarWidget that paints a small dot under days with entries."""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self._dates: set[str] = set()
        self._dot_color = QColor("#2F6FED")
        self.setGridVisible(False)
        self.setVerticalHeaderFormat(QCalendarWidget.VerticalHeaderFormat.NoVerticalHeader)
        self.setHorizontalHeaderFormat(QCalendarWidget.HorizontalHeaderFormat.ShortDayNames)
        self.setNavigationBarVisible(True)

    def set_entry_dates(self, dates: set[str]) -> None:
        self._dates = dates
        self.updateCells()

    def set_dot_color(self, color: str) -> None:
        self._dot_color = QColor(color)
        self.updateCells()

    def paintCell(self, painter: QPainter, rect, qdate: QDate) -> None:  # noqa: N802
        super().paintCell(painter, rect, qdate)
        key = f"{qdate.year():04d}-{qdate.month():02d}-{qdate.day():02d}"
        if key in self._dates:
            painter.save()
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)
            painter.setBrush(self._dot_color)
            painter.setPen(Qt.PenStyle.NoPen)
            r = 3
            cx = rect.center().x()
            cy = rect.bottom() - 7
            painter.drawEllipse(QPoint(cx, cy), r, r)
            painter.restore()


class CalendarPanel(QWidget):
    date_selected = Signal(str)  # YYYY-MM-DD
    month_changed = Signal(int, int)  # year, month

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.title = QLabel("日历")
        self.title.setObjectName("sectionLabel")
        self.calendar = DotCalendar()
        self.calendar.clicked.connect(self._on_clicked)
        self.calendar.currentPageChanged.connect(self._on_page)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 8, 12, 8)
        layout.setSpacing(6)
        layout.addWidget(self.title)
        layout.addWidget(self.calendar)

    def set_entry_dates(self, dates: set[str]) -> None:
        self.calendar.set_entry_dates(dates)

    def set_selected_date(self, entry_date: str) -> None:
        y, m, d = map(int, entry_date.split("-"))
        self.calendar.setSelectedDate(QDate(y, m, d))
        self.calendar.setCurrentPage(y, m)

    def set_dot_color(self, color: str) -> None:
        self.calendar.set_dot_color(color)

    def _on_clicked(self, qdate: QDate) -> None:
        self.date_selected.emit(
            f"{qdate.year():04d}-{qdate.month():02d}-{qdate.day():02d}"
        )

    def _on_page(self, year: int, month: int) -> None:
        self.month_changed.emit(year, month)

    def highlight_today(self) -> None:
        today = date.today()
        self.calendar.setSelectedDate(QDate(today.year, today.month, today.day))
