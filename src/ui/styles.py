"""
Theme stylesheets — quiet ink journal: cool paper, moss accent, soft hierarchy.
"""
from __future__ import annotations

# Cool mist paper + deep moss — not warm cream, not electric blue.
LIGHT = {
    "bg": "#F2F4F1",
    "surface": "#FBFCFB",
    "sidebar": "#E8EBE7",
    "border": "#D2D8D2",
    "text": "#1B221E",
    "muted": "#6A746C",
    "accent": "#3F6B58",
    "accent_soft": "#DCE8E1",
    "dot": "#3F6B58",
    "today": "#3F6B58",
    "danger": "#A33B3B",
    "input": "#FBFCFB",
    "hover": "#DDE3DD",
    "card": "#F6F8F5",
    "selection": "#C9DCD2",
}

DARK = {
    "bg": "#121614",
    "surface": "#1A1F1C",
    "sidebar": "#0F1311",
    "border": "#2A322D",
    "text": "#E6EBE7",
    "muted": "#8E9891",
    "accent": "#7FAE97",
    "accent_soft": "#24332C",
    "dot": "#7FAE97",
    "today": "#7FAE97",
    "danger": "#E08A8A",
    "input": "#1F2622",
    "hover": "#232A26",
    "card": "#1C221F",
    "selection": "#2F4339",
}


def build_stylesheet(palette: dict[str, str], mono: bool = False) -> str:
    from utils.fonts import css_stack

    # Unified CJK sans for UI + editor; mono only tightens size slightly.
    family = css_stack()
    ui_font = family
    display_font = family
    editor_font = family
    editor_size = "15px" if mono else "16px"
    p = palette
    return f"""
    * {{
        font-family: {ui_font};
    }}
    QMainWindow, QWidget#centralRoot {{
        background: {p['bg']};
        color: {p['text']};
    }}
    QMenuBar {{
        background: {p['bg']};
        color: {p['text']};
        border-bottom: 1px solid {p['border']};
        padding: 2px 6px;
    }}
    QMenuBar::item:selected {{
        background: {p['hover']};
        border-radius: 4px;
    }}
    QMenu {{
        background: {p['surface']};
        color: {p['text']};
        border: 1px solid {p['border']};
        padding: 4px;
    }}
    QMenu::item:selected {{
        background: {p['accent_soft']};
        color: {p['accent']};
    }}
    QWidget#sidebar {{
        background: {p['sidebar']};
        border-right: 1px solid {p['border']};
    }}
    QLabel#brandMark {{
        color: {p['text']};
        font-family: {display_font};
        font-size: 22px;
        font-weight: 700;
        letter-spacing: 3px;
        padding: 2px 0 10px 0;
    }}
    QWidget#editorPane {{
        background: {p['bg']};
    }}
    QFrame#card, QPlainTextEdit, QTextEdit, QLineEdit, QComboBox {{
        background: {p['surface']};
        color: {p['text']};
        border: 1px solid {p['border']};
        border-radius: 8px;
    }}
    QPlainTextEdit#diaryEditor {{
        background: {p['surface']};
        border: none;
        border-radius: 12px;
        padding: 24px 30px;
        font-size: {editor_size};
        line-height: 1.55;
        font-family: {editor_font};
        selection-background-color: {p['selection']};
    }}
    QTextBrowser#diaryPreview {{
        background: {p['surface']};
        border: none;
        border-radius: 12px;
        padding: 20px 26px;
        color: {p['text']};
    }}
    QLabel#dateHeading {{
        font-family: {display_font};
        font-size: 26px;
        font-weight: 700;
        color: {p['text']};
        padding: 2px 0;
        letter-spacing: 1px;
    }}
    QLabel#metaLabel {{
        color: {p['muted']};
        font-size: 14px;
        font-weight: 400;
    }}
    QLabel#contextLabel {{
        color: {p['accent']};
        font-size: 14px;
        font-weight: 400;
        padding: 2px 0 4px 0;
    }}
    QLabel#contextLabel[empty="true"] {{
        color: {p['muted']};
    }}
    QLabel#filmThumb {{
        background: {p['hover']};
        border: 1px solid {p['border']};
        border-radius: 10px;
    }}
    QWidget#imageFilmstrip {{
        background: transparent;
    }}
    QLabel#sectionLabel {{
        color: {p['muted']};
        font-size: 13px;
        font-weight: 700;
        letter-spacing: 1.4px;
    }}
    QPushButton {{
        background: transparent;
        color: {p['muted']};
        border: none;
        border-radius: 6px;
        padding: 6px 10px;
        font-size: 13px;
    }}
    QPushButton:hover {{
        background: {p['hover']};
        color: {p['text']};
    }}
    QPushButton#navTab {{
        color: {p['muted']};
        background: transparent;
        border-top: 2px solid transparent;
        border-right: 2px solid transparent;
        border-left: 2px solid transparent;
        border-bottom: 2px solid transparent;
        border-radius: 0;
        padding: 8px 4px 7px 4px;
        font-size: 14px;
        font-weight: 400;
    }}
    QPushButton#navTab:hover {{
        color: {p['text']};
        background: transparent;
    }}
    QPushButton#navTab:checked {{
        color: {p['text']};
        background: transparent;
        border-bottom: 2px solid {p['accent']};
        font-weight: 700;
    }}
    QPushButton#toolBtn {{
        color: {p['muted']};
        font-size: 13px;
        padding: 6px 8px;
    }}
    QPushButton#toolBtn:hover {{
        color: {p['text']};
        background: {p['hover']};
    }}
    QToolButton {{
        background: transparent;
        color: {p['muted']};
        border: none;
        border-radius: 5px;
        padding: 6px 8px;
        font-weight: 700;
        font-size: 13px;
    }}
    QToolButton:hover {{
        background: {p['hover']};
        color: {p['text']};
    }}
    QToolButton:checked {{
        background: {p['accent_soft']};
        color: {p['accent']};
    }}
    QLineEdit#searchBox {{
        background: {p['input']};
        border: 1px solid {p['border']};
        border-radius: 8px;
        padding: 9px 12px;
        font-size: 14px;
    }}
    QLineEdit#searchBox:focus {{
        border: 1px solid {p['accent']};
    }}
    QListWidget {{
        border: none;
        background: transparent;
        outline: none;
    }}
    QListWidget::item {{
        background: transparent;
        border: none;
        border-bottom: 1px solid {p['border']};
        border-radius: 0;
        margin: 0;
        padding: 12px 4px;
        color: {p['text']};
    }}
    QListWidget::item:selected {{
        background: {p['accent_soft']};
        color: {p['text']};
        border-bottom: 1px solid {p['accent_soft']};
    }}
    QListWidget::item:hover {{
        background: {p['hover']};
    }}
    QCalendarWidget {{
        background: transparent;
        border: none;
    }}
    QCalendarWidget QWidget#qt_calendar_navigationbar {{
        background: transparent;
        border: none;
        min-height: 36px;
    }}
    QCalendarWidget QToolButton {{
        color: {p['text']};
        background: transparent;
        font-family: {display_font};
        font-size: 15px;
        font-weight: 700;
        padding: 4px 6px;
        border-radius: 6px;
    }}
    QCalendarWidget QToolButton:hover {{
        background: {p['hover']};
    }}
    QCalendarWidget QToolButton#qt_calendar_prevmonth,
    QCalendarWidget QToolButton#qt_calendar_nextmonth {{
        color: {p['muted']};
        font-size: 16px;
        font-weight: 400;
        qproperty-icon: none;
        qproperty-text: "";
    }}
    QCalendarWidget QMenu {{
        background: {p['surface']};
        color: {p['text']};
    }}
    QCalendarWidget QSpinBox {{
        color: {p['text']};
        background: transparent;
        border: none;
        font-family: {display_font};
        font-size: 14px;
        font-weight: 700;
    }}
    QCalendarWidget QWidget {{
        alternate-background-color: transparent;
    }}
    QCalendarWidget QAbstractItemView:enabled {{
        color: {p['text']};
        background: transparent;
        selection-background-color: transparent;
        selection-color: {p['text']};
        outline: none;
        font-size: 14px;
    }}
    QCalendarWidget QAbstractItemView:disabled {{
        color: {p['muted']};
    }}
    QComboBox {{
        padding: 6px 10px;
        border-radius: 8px;
        background: {p['input']};
        border: 1px solid {p['border']};
        color: {p['text']};
        font-size: 13px;
    }}
    QComboBox QAbstractItemView {{
        background: {p['surface']};
        color: {p['text']};
        selection-background-color: {p['accent_soft']};
        border: 1px solid {p['border']};
    }}
    QScrollBar:vertical {{
        background: transparent;
        width: 8px;
        margin: 0;
    }}
    QScrollBar::handle:vertical {{
        background: {p['border']};
        border-radius: 4px;
        min-height: 28px;
    }}
    QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
        height: 0;
    }}
    QStatusBar {{
        background: {p['bg']};
        color: {p['muted']};
        border-top: 1px solid {p['border']};
        font-size: 13px;
    }}
    QSplitter::handle {{
        background: {p['border']};
        width: 1px;
    }}
    """


def resolve_palette(theme: str, system_dark: bool) -> dict[str, str]:
    if theme == "dark":
        return DARK
    if theme == "light":
        return LIGHT
    return DARK if system_dark else LIGHT
