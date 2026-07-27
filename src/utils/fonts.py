"""
App typeface: 霞鹜文楷 (LXGW WenKai).

Note: the installed face is Regular-only. True bold faces are unavailable,
so emphasis uses Qt algorithmic emboldening + size/tracking hierarchy.
"""
from __future__ import annotations

from PySide6.QtGui import QFont, QFontDatabase

# Preferred PostScript / English family name; Chinese name as fallback.
_PREFERRED = ("LXGW WenKai", "霞鹜文楷")
_FALLBACK = "'LXGW WenKai', '霞鹜文楷', 'Microsoft YaHei UI', sans-serif"


def resolve_family() -> str:
    """Return an installed WenKai family name, or the English id as last resort."""
    available = set(QFontDatabase.families())
    for name in _PREFERRED:
        if name in available:
            return name
    return "LXGW WenKai"


def css_stack() -> str:
    """CSS font-family stack for stylesheets / HTML preview."""
    return _FALLBACK


def app_font(point_size: int = 12) -> QFont:
    font = QFont(resolve_family(), point_size)
    font.setStyleHint(QFont.StyleHint.SansSerif)
    font.setHintingPreference(QFont.HintingPreference.PreferFullHinting)
    return font


def emphasis_font(*, point_size: int | None = None, pixel_size: int | None = None, bold: bool = True) -> QFont:
    """WenKai with algorithmic bold — use for titles / selected chrome."""
    font = QFont(resolve_family())
    if pixel_size is not None:
        font.setPixelSize(pixel_size)
    elif point_size is not None:
        font.setPointSize(point_size)
    if bold:
        font.setBold(True)
        font.setWeight(QFont.Weight.Bold)
    font.setStyleStrategy(
        QFont.StyleStrategy.PreferQuality
        | QFont.StyleStrategy.PreferAntialias
    )
    return font
