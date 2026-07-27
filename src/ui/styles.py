"""
Theme stylesheets — Day One inspired calm cards & whitespace.
"""
from __future__ import annotations

LIGHT = {
    "bg": "#F3F1EC",
    "surface": "#FFFFFF",
    "sidebar": "#EBE8E1",
    "border": "#DDD8CE",
    "text": "#1C1B19",
    "muted": "#6F6A60",
    "accent": "#2F6FED",
    "accent_soft": "#E8F0FF",
    "dot": "#2F6FED",
    "today": "#2F6FED",
    "danger": "#C62828",
    "input": "#FFFFFF",
    "hover": "#E4E0D7",
    "card": "#FFFFFF",
    "selection": "#D6E4FF",
}

DARK = {
    "bg": "#161718",
    "surface": "#1E1F21",
    "sidebar": "#121314",
    "border": "#2A2B2E",
    "text": "#EDECE8",
    "muted": "#9A9690",
    "accent": "#6B9BFF",
    "accent_soft": "#243247",
    "dot": "#6B9BFF",
    "today": "#6B9BFF",
    "danger": "#EF9A9A",
    "input": "#232426",
    "hover": "#262729",
    "card": "#222325",
    "selection": "#2A3A55",
}


def build_stylesheet(palette: dict[str, str], mono: bool = False) -> str:
    font = "Consolas, 'Cascadia Mono', 'Courier New', monospace" if mono else (
        "'Segoe UI', 'Microsoft YaHei UI', 'PingFang SC', sans-serif"
    )
    editor_font = "Consolas, 'Cascadia Mono', monospace" if mono else font
    p = palette
    return f"""
    * {{
        font-family: {font};
    }}
    QMainWindow, QWidget#centralRoot {{
        background: {p['bg']};
        color: {p['text']};
    }}
    QWidget#sidebar {{
        background: {p['sidebar']};
        border-right: 1px solid {p['border']};
    }}
    QWidget#editorPane {{
        background: {p['bg']};
    }}
    QFrame#card, QListWidget, QPlainTextEdit, QTextEdit, QLineEdit, QComboBox {{
        background: {p['surface']};
        color: {p['text']};
        border: 1px solid {p['border']};
        border-radius: 10px;
    }}
    QPlainTextEdit#diaryEditor {{
        background: {p['surface']};
        border: none;
        border-radius: 14px;
        padding: 22px 28px;
        font-size: 15px;
        font-family: {editor_font};
        selection-background-color: {p['selection']};
    }}
    QTextBrowser#diaryPreview {{
        background: {p['surface']};
        border: none;
        border-radius: 14px;
        padding: 18px 24px;
        color: {p['text']};
    }}
    QLabel#dateHeading {{
        font-size: 22px;
        font-weight: 600;
        color: {p['text']};
        padding: 4px 0;
    }}
    QToolButton:checked {{
        background: {p['accent_soft']};
        color: {p['accent']};
    }}
    QLabel#metaLabel {{
        color: {p['muted']};
        font-size: 12px;
    }}
    QLabel#sectionLabel {{
        color: {p['muted']};
        font-size: 11px;
        font-weight: 600;
        letter-spacing: 0.6px;
        text-transform: uppercase;
    }}
    QPushButton {{
        background: transparent;
        color: {p['text']};
        border: 1px solid transparent;
        border-radius: 8px;
        padding: 6px 10px;
    }}
    QPushButton:hover {{
        background: {p['hover']};
    }}
    QPushButton:checked {{
        background: {p['accent_soft']};
        color: {p['accent']};
    }}
    QPushButton#primaryBtn {{
        background: {p['accent']};
        color: white;
        border: none;
        padding: 8px 14px;
    }}
    QPushButton#primaryBtn:hover {{
        background: {p['accent']};
        opacity: 0.9;
    }}
    QToolButton {{
        background: transparent;
        color: {p['muted']};
        border: none;
        border-radius: 6px;
        padding: 6px 8px;
        font-weight: 600;
    }}
    QToolButton:hover {{
        background: {p['hover']};
        color: {p['text']};
    }}
    QLineEdit#searchBox {{
        background: {p['input']};
        border: 1px solid {p['border']};
        border-radius: 8px;
        padding: 8px 12px;
        font-size: 13px;
    }}
    QListWidget {{
        border: none;
        background: transparent;
        outline: none;
    }}
    QListWidget::item {{
        background: {p['card']};
        border: 1px solid {p['border']};
        border-radius: 10px;
        margin: 4px 2px;
        padding: 10px;
    }}
    QListWidget::item:selected {{
        background: {p['accent_soft']};
        border: 1px solid {p['accent']};
    }}
    QListWidget::item:hover {{
        background: {p['hover']};
    }}
    QCalendarWidget {{
        background: transparent;
        border: none;
    }}
    QCalendarWidget QWidget {{
        alternate-background-color: {p['sidebar']};
    }}
    QCalendarWidget QToolButton {{
        color: {p['text']};
        background: transparent;
        font-weight: 600;
        padding: 4px 8px;
    }}
    QCalendarWidget QMenu {{
        background: {p['surface']};
        color: {p['text']};
    }}
    QCalendarWidget QSpinBox {{
        color: {p['text']};
        background: {p['input']};
        border: 1px solid {p['border']};
        border-radius: 4px;
    }}
    QCalendarWidget QAbstractItemView:enabled {{
        color: {p['text']};
        background: {p['surface']};
        selection-background-color: {p['accent']};
        selection-color: white;
        outline: none;
        border-radius: 8px;
    }}
    QComboBox {{
        padding: 6px 10px;
        border-radius: 8px;
    }}
    QComboBox QAbstractItemView {{
        background: {p['surface']};
        color: {p['text']};
        selection-background-color: {p['accent_soft']};
    }}
    QScrollBar:vertical {{
        background: transparent;
        width: 10px;
        margin: 0;
    }}
    QScrollBar::handle:vertical {{
        background: {p['border']};
        border-radius: 5px;
        min-height: 30px;
    }}
    QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
        height: 0;
    }}
    QStatusBar {{
        background: {p['sidebar']};
        color: {p['muted']};
        border-top: 1px solid {p['border']};
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
