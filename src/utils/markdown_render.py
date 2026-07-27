"""
Markdown → HTML renderer for the diary preview pane.
"""
from __future__ import annotations

import html
from pathlib import Path

import markdown


_EXTENSIONS = [
    "fenced_code",
    "tables",
    "nl2br",
    "sane_lists",
    "smarty",
]


def md_to_html(source: str, *, base_dir: Path, palette: dict[str, str], mono: bool = False) -> str:
    """Render Markdown to a full HTML document with themed CSS."""
    body = markdown.markdown(source or "", extensions=_EXTENSIONS)
    css = _preview_css(palette, mono=mono)
    # file:/// base lets relative assets/… paths resolve under data root.
    base = base_dir.resolve().as_uri().rstrip("/") + "/"
    empty = '<p class="empty">开始书写，预览将显示在这里…</p>'
    content = body if body.strip() else empty
    return (
        "<!DOCTYPE html><html><head>"
        f'<meta charset="utf-8"/>'
        f'<base href="{html.escape(base, quote=True)}"/>'
        f"<style>{css}</style>"
        "</head><body>"
        f"{content}"
        "</body></html>"
    )


def _preview_css(p: dict[str, str], mono: bool = False) -> str:
    font = (
        "Consolas, 'Cascadia Mono', 'Courier New', monospace"
        if mono
        else "'Segoe UI', 'Microsoft YaHei UI', 'PingFang SC', sans-serif"
    )
    code_font = "Consolas, 'Cascadia Mono', monospace"
    return f"""
    html, body {{
        margin: 0;
        padding: 0;
        background: {p['surface']};
        color: {p['text']};
        font-family: {font};
        font-size: 15px;
        line-height: 1.7;
    }}
    body {{
        padding: 8px 4px 28px 4px;
    }}
    h1, h2, h3, h4 {{
        font-weight: 650;
        line-height: 1.35;
        margin: 1.2em 0 0.45em;
    }}
    h1 {{ font-size: 1.7em; }}
    h2 {{ font-size: 1.35em; }}
    h3 {{ font-size: 1.15em; }}
    p {{ margin: 0.65em 0; }}
    a {{ color: {p['accent']}; text-decoration: none; }}
    a:hover {{ text-decoration: underline; }}
    strong {{ font-weight: 700; }}
    em {{ font-style: italic; }}
    blockquote {{
        margin: 0.8em 0;
        padding: 0.2em 0 0.2em 0.9em;
        border-left: 3px solid {p['accent']};
        color: {p['muted']};
    }}
    ul, ol {{
        margin: 0.5em 0;
        padding-left: 1.4em;
    }}
    li {{ margin: 0.2em 0; }}
    code {{
        font-family: {code_font};
        font-size: 0.92em;
        background: {p['hover']};
        padding: 0.12em 0.35em;
        border-radius: 4px;
    }}
    pre {{
        background: {p['hover']};
        border: 1px solid {p['border']};
        border-radius: 10px;
        padding: 12px 14px;
        overflow-x: auto;
    }}
    pre code {{
        background: transparent;
        padding: 0;
    }}
    table {{
        border-collapse: collapse;
        margin: 0.8em 0;
        width: 100%;
    }}
    th, td {{
        border: 1px solid {p['border']};
        padding: 6px 10px;
        text-align: left;
    }}
    th {{ background: {p['hover']}; }}
    hr {{
        border: none;
        border-top: 1px solid {p['border']};
        margin: 1.4em 0;
    }}
    img {{
        max-width: 100%;
        height: auto;
        border-radius: 10px;
        margin: 0.6em 0;
        display: block;
    }}
    .empty {{
        color: {p['muted']};
        font-style: italic;
    }}
    """
