"""
App typeface: unified CJK sans stack.

WenKai is lovely for long reading but weak for UI chrome (calendar, buttons,
dense lists). Prefer system UI sans — clear at all sizes, real bold weights.
"""
from __future__ import annotations

from PySide6.QtGui import QFont, QFontDatabase

# Prefer clear UI sans with proper bold; English UI fonts as last resort.
_PREFERRED = (
    "Microsoft YaHei UI",
    "Microsoft YaHei",
    "PingFang SC",
    "Noto Sans CJK SC",
    "Noto Sans SC",
    "Source Han Sans SC",
    "Segoe UI",
)
_FALLBACK = (
    "'Microsoft YaHei UI', 'Microsoft YaHei', 'PingFang SC', "
    "'Noto Sans CJK SC', 'Noto Sans SC', 'Segoe UI', sans-serif"
)


def resolve_family() -> str:
    """Return the best installed family from the preferred list."""
    available = set(QFontDatabase.families())
    for name in _PREFERRED:
        if name in available:
            return name
    return "Sans Serif"


def css_stack() -> str:
    """CSS font-family stack for stylesheets / HTML preview."""
    return _FALLBACK


def app_font(point_size: int = 12) -> QFont:
    font = QFont(resolve_family(), point_size)
    font.setStyleHint(QFont.StyleHint.SansSerif)
    font.setHintingPreference(QFont.HintingPreference.PreferFullHinting)
    return font


def emphasis_font(
    *,
    point_size: int | None = None,
    pixel_size: int | None = None,
    bold: bool = True,
) -> QFont:
    """Title / selected chrome — uses real bold when the family has it."""
    font = QFont(resolve_family())
    if pixel_size is not None:
        font.setPixelSize(pixel_size)
    elif point_size is not None:
        font.setPointSize(point_size)
    if bold:
        font.setBold(True)
        font.setWeight(QFont.Weight.DemiBold)
    font.setStyleStrategy(
        QFont.StyleStrategy.PreferQuality
        | QFont.StyleStrategy.PreferAntialias
    )
    return font
