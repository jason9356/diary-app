"""
Calendar widget with soft ink-style day cells and entry dots.
"""
from __future__ import annotations

from datetime import date

from PySide6.QtCore import QDate, QPoint, QRect, Qt, Signal
from PySide6.QtGui import QColor, QPainter, QPen, QTextCharFormat
from PySide6.QtWidgets import QCalendarWidget, QLabel, QSizePolicy, QToolButton, QVBoxLayout, QWidget

from utils.fonts import emphasis_font


class DotCalendar(QCalendarWidget):
    """Custom-painted calendar: quiet selection, today ring, entry dots."""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self._dates: set[str] = set()
        self._colors = {
            "text": QColor("#1B221E"),
            "muted": QColor("#6A746C"),
            "accent": QColor("#3F6B58"),
            "accent_soft": QColor("#DCE8E1"),
            "dot": QColor("#3F6B58"),
            "today": QColor("#3F6B58"),
            "hover_fill": QColor("#DDE3DD"),
        }
        self.setGridVisible(False)
        self.setVerticalHeaderFormat(QCalendarWidget.VerticalHeaderFormat.NoVerticalHeader)
        self.setHorizontalHeaderFormat(QCalendarWidget.HorizontalHeaderFormat.SingleLetterDayNames)
        self.setNavigationBarVisible(True)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.setFixedHeight(292)
        self.setDateEditEnabled(False)
        self._restyle_nav()
        self._apply_weekday_formats()

    def _restyle_nav(self) -> None:
        prev = self.findChild(QToolButton, "qt_calendar_prevmonth")
        next_ = self.findChild(QToolButton, "qt_calendar_nextmonth")
        for btn, label in ((prev, "‹"), (next_, "›")):
            if btn is None:
                continue
            btn.setArrowType(Qt.ArrowType.NoArrow)
            btn.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonTextOnly)
            btn.setText(label)
            btn.setFixedSize(28, 28)

    def _apply_weekday_formats(self) -> None:
        """Kill Qt's default loud weekend red; weekends use the same ink as weekdays."""
        fmt = QTextCharFormat()
        fmt.setForeground(self._colors["text"])
        fmt.setFont(emphasis_font(point_size=11, bold=False))
        for day in (
            Qt.DayOfWeek.Monday,
            Qt.DayOfWeek.Tuesday,
            Qt.DayOfWeek.Wednesday,
            Qt.DayOfWeek.Thursday,
            Qt.DayOfWeek.Friday,
            Qt.DayOfWeek.Saturday,
            Qt.DayOfWeek.Sunday,
        ):
            self.setWeekdayTextFormat(day, fmt)

        # Header labels for weekends: slightly muted, never red.
        weekend = QTextCharFormat(fmt)
        weekend.setForeground(self._colors["muted"])
        self.setWeekdayTextFormat(Qt.DayOfWeek.Saturday, weekend)
        self.setWeekdayTextFormat(Qt.DayOfWeek.Sunday, weekend)

    def set_entry_dates(self, dates: set[str]) -> None:
        self._dates = dates
        self.updateCells()

    def set_palette_colors(self, palette: dict[str, str]) -> None:
        self._colors = {
            "text": QColor(palette["text"]),
            "muted": QColor(palette["muted"]),
            "accent": QColor(palette["accent"]),
            "accent_soft": QColor(palette["accent_soft"]),
            "dot": QColor(palette["dot"]),
            "today": QColor(palette["today"]),
            "hover_fill": QColor(palette["hover"]),
        }
        self._apply_weekday_formats()
        self.updateCells()

    def set_dot_color(self, color: str) -> None:
        self._colors["dot"] = QColor(color)
        self.updateCells()

    def paintCell(self, painter: QPainter, rect: QRect, qdate: QDate) -> None:  # noqa: N802
        painter.save()
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

        in_month = qdate.month() == self.monthShown() and qdate.year() == self.yearShown()
        selected = qdate == self.selectedDate()
        is_today = qdate == QDate.currentDate()
        key = f"{qdate.year():04d}-{qdate.month():02d}-{qdate.day():02d}"
        has_entry = key in self._dates

        # Cell inset so days breathe instead of filling the grid.
        cell = rect.adjusted(3, 2, -3, -2)

        if selected and in_month:
            painter.setPen(Qt.PenStyle.NoPen)
            painter.setBrush(self._colors["accent_soft"])
            painter.drawRoundedRect(cell, 8, 8)
        elif is_today and in_month:
            pen = QPen(self._colors["today"])
            pen.setWidthF(1.2)
            painter.setPen(pen)
            painter.setBrush(Qt.BrushStyle.NoBrush)
            painter.drawRoundedRect(cell, 8, 8)

        # Day number — weekends share the same ink (no special red).
        if in_month:
            color = self._colors["accent"] if selected else self._colors["text"]
        else:
            muted = QColor(self._colors["muted"])
            muted.setAlpha(90)
            color = muted

        font = emphasis_font(
            pixel_size=14,
            bold=bool((selected or is_today) and in_month),
        )
        painter.setFont(font)
        painter.setPen(color)

        text_rect = QRect(cell.left(), cell.top() + 2, cell.width(), cell.height() - 12)
        painter.drawText(
            text_rect,
            Qt.AlignmentFlag.AlignHCenter | Qt.AlignmentFlag.AlignVCenter,
            str(qdate.day()),
        )

        # Entry mark
        if has_entry and in_month:
            painter.setPen(Qt.PenStyle.NoPen)
            painter.setBrush(self._colors["dot"] if not selected else self._colors["accent"])
            r = 2.2
            cx = cell.center().x()
            cy = cell.bottom() - 7
            painter.drawEllipse(QPoint(int(cx), int(cy)), int(r), int(r))

        painter.restore()


class CalendarPanel(QWidget):
    date_selected = Signal(str)  # YYYY-MM-DD
    month_changed = Signal(int, int)  # year, month

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.title = QLabel("日期")
        self.title.setObjectName("sectionLabel")
        self.calendar = DotCalendar()
        self.calendar.clicked.connect(self._on_clicked)
        self.calendar.currentPageChanged.connect(self._on_page)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 4, 16, 8)
        layout.setSpacing(10)
        layout.addWidget(self.title)
        layout.addWidget(self.calendar, 0, Qt.AlignmentFlag.AlignTop)
        layout.addStretch(1)

    def set_entry_dates(self, dates: set[str]) -> None:
        self.calendar.set_entry_dates(dates)

    def set_selected_date(self, entry_date: str) -> None:
        y, m, d = map(int, entry_date.split("-"))
        self.calendar.setSelectedDate(QDate(y, m, d))
        self.calendar.setCurrentPage(y, m)

    def set_dot_color(self, color: str) -> None:
        self.calendar.set_dot_color(color)

    def apply_palette(self, palette: dict[str, str]) -> None:
        self.calendar.set_palette_colors(palette)

    def _on_clicked(self, qdate: QDate) -> None:
        self.date_selected.emit(
            f"{qdate.year():04d}-{qdate.month():02d}-{qdate.day():02d}"
        )

    def _on_page(self, year: int, month: int) -> None:
        self.month_changed.emit(year, month)

    def highlight_today(self) -> None:
        today = date.today()
        self.calendar.setSelectedDate(QDate(today.year, today.month, today.day))
