"""
Theme stylesheets — Sparkbox 青笺: cool gray paper, moss-teal accent.
Aligned with Android PaletteCatalog.slip (light/dark).
"""
from __future__ import annotations

# 青笺 · 冷灰纸 · 青苔 — match Android Slip
LIGHT = {
    "bg": "#F5F7F8",
    "bg_deep": "#EEF2F4",
    "surface": "#FFFFFF",
    "sidebar": "#EEF2F4",
    "border": "#E5E7EB",
    "text": "#111827",
    "muted": "#6B7280",
    "accent": "#3A5F5A",
    "accent_soft": "#D9E5E2",
    "dot": "#3A5F5A",
    "today": "#3A5F5A",
    "danger": "#A33B3B",
    "input": "#FFFFFF",
    "hover": "#E8EEF0",
    "card": "#FFFFFF",
    "selection": "#C5D8D4",
    "on_accent": "#FFFFFF",
}

DARK = {
    "bg": "#12161A",
    "bg_deep": "#0E1114",
    "surface": "#1A1F24",
    "sidebar": "#0E1114",
    "border": "#2A3138",
    "text": "#E8EAED",
    "muted": "#9AA0A8",
    "accent": "#7FA39C",
    "accent_soft": "#243836",
    "dot": "#7FA39C",
    "today": "#7FA39C",
    "danger": "#E08A8A",
    "input": "#1A1F24",
    "hover": "#222830",
    "card": "#1A1F24",
    "selection": "#2F4339",
    "on_accent": "#12161A",
}


def build_stylesheet(palette: dict[str, str], mono: bool = False) -> str:
    from utils.fonts import css_stack

    family = css_stack()
    ui_font = family
    display_font = family
    editor_font = family
    editor_size = "15px" if mono else "16px"
    p = palette
    bg_deep = p.get("bg_deep", p["sidebar"])
    return f"""
    * {{
        font-family: {ui_font};
    }}
    QMainWindow, QWidget#centralRoot {{
        background: qlineargradient(
            x1:0, y1:0, x2:0, y2:1,
            stop:0 {p['bg']},
            stop:1 {bg_deep}
        );
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
        border-radius: 6px;
    }}
    QMenu {{
        background: {p['surface']};
        color: {p['text']};
        border: 1px solid {p['border']};
        border-radius: 10px;
        padding: 4px;
    }}
    QMenu::item:selected {{
        background: {p['accent_soft']};
        color: {p['accent']};
        border-radius: 6px;
    }}
    QWidget#sidebar {{
        background: {p['sidebar']};
        border-right: 1px solid {p['border']};
    }}
    QLabel#brandMark {{
        color: {p['accent']};
        font-family: {display_font};
        font-size: 26px;
        font-weight: 800;
        letter-spacing: 2px;
        padding: 4px 0 12px 0;
    }}
    QWidget#editorPane {{
        background: transparent;
    }}
    QFrame#paperSheet {{
        background: {p['surface']};
        border: 1px solid {p['border']};
        border-radius: 18px;
    }}
    QFrame#inspirationCard {{
        background: {p['card']};
        border: 1px solid {p['border']};
        border-radius: 14px;
    }}
    QLabel#cardTag {{
        color: {p['muted']};
        font-size: 12px;
        font-weight: 400;
    }}
    QLabel#cardTitle {{
        color: {p['text']};
        font-size: 16px;
        font-weight: 700;
    }}
    QLabel#cardBody {{
        color: {p['muted']};
        font-size: 13px;
        font-weight: 400;
    }}
    QLabel#cardFooter {{
        color: {p['muted']};
        font-size: 11px;
        font-weight: 400;
    }}
    QPlainTextEdit, QTextEdit, QLineEdit, QComboBox {{
        background: {p['surface']};
        color: {p['text']};
        border: 1px solid {p['border']};
        border-radius: 12px;
    }}
    QPlainTextEdit#diaryEditor, QTextEdit#diaryEditor {{
        background: transparent;
        border: none;
        border-radius: 0;
        padding: 8px 4px;
        font-size: {editor_size};
        line-height: 1.6;
        font-family: {editor_font};
        selection-background-color: {p['selection']};
    }}
    QTextBrowser#diaryPreview {{
        background: transparent;
        border: none;
        border-radius: 0;
        padding: 4px 2px;
        color: {p['text']};
    }}
    QLabel#dateHeading {{
        font-family: {display_font};
        font-size: 24px;
        font-weight: 700;
        color: {p['text']};
        padding: 2px 0;
        letter-spacing: 0.5px;
    }}
    QLabel#metaLabel {{
        color: {p['muted']};
        font-size: 13px;
        font-weight: 400;
    }}
    QLabel#contextLabel {{
        color: {p['accent']};
        font-size: 13px;
        font-weight: 400;
        padding: 2px 0 4px 0;
    }}
    QLabel#contextLabel[empty="true"] {{
        color: {p['muted']};
    }}
    QLabel#filmThumb {{
        background: {p['hover']};
        border: 1px solid {p['border']};
        border-radius: 8px;
    }}
    QWidget#imageFilmstrip {{
        background: transparent;
    }}
    QLabel#sectionLabel {{
        color: {p['muted']};
        font-size: 12px;
        font-weight: 600;
        letter-spacing: 1px;
    }}
    QPushButton {{
        background: transparent;
        color: {p['muted']};
        border: none;
        border-radius: 8px;
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
        border: none;
        border-radius: 10px;
        padding: 8px 6px;
        font-size: 13px;
        font-weight: 500;
    }}
    QPushButton#navTab:hover {{
        color: {p['text']};
        background: {p['hover']};
    }}
    QPushButton#navTab:checked {{
        color: {p['accent']};
        background: {p['accent_soft']};
        font-weight: 700;
    }}
    QPushButton#toolBtn {{
        color: {p['muted']};
        font-size: 13px;
        padding: 6px 8px;
        border-radius: 8px;
    }}
    QPushButton#toolBtn:hover {{
        color: {p['text']};
        background: {p['hover']};
    }}
    QPushButton#primaryBtn {{
        background: {p['accent']};
        color: {p.get('on_accent', '#FFFFFF')};
        border: none;
        border-radius: 10px;
        padding: 8px 14px;
        font-size: 13px;
        font-weight: 700;
    }}
    QPushButton#primaryBtn:hover {{
        background: {p['accent']};
        color: {p.get('on_accent', '#FFFFFF')};
    }}
    QToolButton {{
        background: transparent;
        color: {p['muted']};
        border: none;
        border-radius: 8px;
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
        background: {p['surface']};
        border: 1px solid {p['border']};
        border-radius: 12px;
        padding: 10px 12px;
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
    QListWidget#cardList::item {{
        background: transparent;
        border: none;
        margin: 0 0 10px 0;
        padding: 0;
    }}
    QListWidget#cardList::item:selected {{
        background: transparent;
    }}
    QListWidget#cardList::item:hover {{
        background: transparent;
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
        border-radius: 8px;
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
        border-radius: 10px;
        background: {p['surface']};
        border: 1px solid {p['border']};
        color: {p['text']};
        font-size: 13px;
    }}
    QComboBox#noteCombo {{
        border-radius: 12px;
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
        background: transparent;
        color: {p['muted']};
        border-top: 1px solid {p['border']};
        font-size: 12px;
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
