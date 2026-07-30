"""
Markdown → HTML renderer for the diary preview pane.
"""
from __future__ import annotations

import html
import re
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
    # Image display is disabled for now — keep markdown on disk, show a placeholder.
    safe = _strip_images_for_display(source or "")
    body = markdown.markdown(safe, extensions=_EXTENSIONS)
    css = _preview_css(palette, mono=mono)
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


_IMG_MD_RE = re.compile(r"!\[[^\]]*]\([^)]+\)")


def _strip_images_for_display(source: str) -> str:
    """Replace image markdown with a text marker; do not render binary assets."""
    text = _IMG_MD_RE.sub("［图片］", source)
    return re.sub(r"\n{3,}", "\n\n", text)


def _group_image_runs(body: str) -> str:
    """Legacy helper retained unused — image rendering is deferred."""
    return body


def _preview_css(p: dict[str, str], mono: bool = False) -> str:
    from utils.fonts import css_stack

    font = css_stack()
    code_font = css_stack()
    size = "15px" if mono else "16px"
    return f"""
    html, body {{
        margin: 0;
        padding: 0;
        background: {p['surface']};
        color: {p['text']};
        font-family: {font};
        font-size: {size};
        line-height: 1.7;
    }}
    body {{
        padding: 8px 4px 28px 4px;
    }}
    h1, h2, h3, h4 {{
        font-weight: 700;
        line-height: 1.35;
        margin: 1.2em 0 0.45em;
        letter-spacing: 0.02em;
    }}
    h1 {{ font-size: 1.22em; }}
    h2 {{ font-size: 1.22em; }}
    h3 {{ font-size: 1.12em; }}
    h4 {{ font-size: 1.05em; }}
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
        border-radius: 6px;
    }}
    pre {{
        background: {p['hover']};
        border: 1px solid {p['border']};
        border-radius: 12px;
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
    th {{ background: {p['hover']}; font-weight: 700; }}
    hr {{
        border: none;
        border-top: 1px solid {p['border']};
        margin: 1.4em 0;
    }}
    img {{
        max-width: 100%;
        height: auto;
        border-radius: 14px;
        margin: 0;
        display: block;
    }}
    figure.solo {{
        margin: 1em 0 1.2em;
        padding: 0;
    }}
    figure.solo img {{
        width: 100%;
        border-radius: 14px;
    }}
    table.gallery {{
        width: 100%;
        border-collapse: separate;
        border-spacing: 8px;
        margin: 1em 0 1.2em;
    }}
    table.gallery td.cell {{
        width: 50%;
        vertical-align: top;
        border: none;
        padding: 0;
        background: transparent;
    }}
    table.gallery td.cell img {{
        width: 100%;
        border-radius: 12px;
    }}
    .empty {{
        color: {p['muted']};
        font-style: italic;
    }}
    """
