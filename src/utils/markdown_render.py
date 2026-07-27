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
    body = markdown.markdown(source or "", extensions=_EXTENSIONS)
    body = _group_image_runs(body)
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


def _group_image_runs(body: str) -> str:
    """
    Collapse consecutive image-only paragraphs into a gallery:
    1 image → figure.solo; 2+ → div.gallery (2-col).
    """
    token_re = re.compile(r"<p>\s*(<img\b[^>]*>)\s*</p>", re.IGNORECASE)
    out: list[str] = []
    last = 0
    run: list[str] = []

    def flush() -> None:
        nonlocal run
        if not run:
            return
        if len(run) == 1:
            out.append(f'<figure class="solo">{run[0]}</figure>')
        else:
            # QTextBrowser lacks CSS grid — use a simple 2-col table.
            rows: list[str] = []
            for i in range(0, len(run), 2):
                left = f'<td class="cell">{run[i]}</td>'
                right = (
                    f'<td class="cell">{run[i + 1]}</td>'
                    if i + 1 < len(run)
                    else '<td class="cell"></td>'
                )
                rows.append(f"<tr>{left}{right}</tr>")
            out.append(
                '<table class="gallery" cellspacing="8" cellpadding="0">'
                + "".join(rows)
                + "</table>"
            )
        run = []

    for m in token_re.finditer(body):
        between = body[last:m.start()]
        if between.strip():
            flush()
            out.append(between)
        elif between and run:
            # whitespace between images — keep run
            pass
        elif between:
            out.append(between)
        run.append(m.group(1))
        last = m.end()
    flush()
    out.append(body[last:])
    return "".join(out)


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
    h1 {{ font-size: 1.85em; }}
    h2 {{ font-size: 1.45em; }}
    h3 {{ font-size: 1.2em; }}
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
