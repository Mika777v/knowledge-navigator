#!/usr/bin/env python3
"""ダミー規程（data/seed/documents の Markdown）から PDF を作る（E11-T2）。

PDF 取り込み（v0.2.0）の動作確認とテストに使う。実際の社内規程の PDF に近づけるため、
A4・明朝ではなくゴシック1書体・ヘッダー（規程名）・フッター（ページ番号）付きで組み、条がページをまたぐこともある。

    pip install -r scripts/data/requirements-pdf.txt
    python3 scripts/data/generate_pdfs.py               # data/pdf/ に10本を書き出す
    python3 scripts/data/generate_pdfs.py --font /path/to/ipag.ttf

日本語フォントは IPA ゴシック（Ubuntu では fonts-ipafont-gothic）を使う。PDF には使った文字だけを埋め込む。
同じ入力からは同じ PDF ができる（作成日時などを固定する）。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import KeepTogether, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
FONT_CANDIDATES = [
    Path("/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf"),
    Path("/usr/share/fonts/truetype/fonts-japanese-gothic.ttf"),
    Path("/Library/Fonts/ipag.ttf"),
]
FONT = "IPAGothic"


def styles() -> dict[str, ParagraphStyle]:
    base = ParagraphStyle("base", fontName=FONT, fontSize=10.5, leading=17, wordWrap="CJK")
    return {
        "title": ParagraphStyle("title", base, fontSize=18, leading=26, alignment=TA_CENTER, spaceAfter=4 * mm),
        "meta": ParagraphStyle("meta", base, fontSize=9, alignment=TA_CENTER, spaceAfter=6 * mm),
        "notice": ParagraphStyle("notice", base, fontSize=9, leading=14, leftIndent=4 * mm, spaceAfter=4 * mm),
        "chapter": ParagraphStyle("chapter", base, fontSize=13, leading=20, spaceBefore=5 * mm, spaceAfter=2 * mm),
        "article": ParagraphStyle("article", base, fontSize=11, leading=18, spaceBefore=3 * mm, spaceAfter=1 * mm),
        "body": ParagraphStyle("body", base, spaceAfter=1.5 * mm),
        "item": ParagraphStyle("item", base, leftIndent=7 * mm, firstLineIndent=-5 * mm, spaceAfter=1 * mm),
        "cell": ParagraphStyle("cell", base, fontSize=9.5, leading=14),
    }


def split_front_matter(text: str) -> tuple[dict, str]:
    match = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    if not match:
        raise ValueError("front matter がありません")
    return yaml.safe_load(match.group(1)), text[match.end():]


def inline(text: str) -> str:
    """Markdown の強調（**）だけを太字相当にし、それ以外はそのまま文字として出す"""
    return re.sub(r"\*\*(.+?)\*\*", r"<u>\1</u>", escape(text))


def table(rows: list[str], s: dict[str, ParagraphStyle]) -> Table:
    cells = [[c.strip() for c in row.strip().strip("|").split("|")] for row in rows]
    cells = [r for r in cells if not all(re.fullmatch(r":?-{3,}:?", c) for c in r)]  # 区切り行を除く
    data = [[Paragraph(inline(c), s["cell"]) for c in r] for r in cells]
    t = Table(data, repeatRows=1, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("GRID", (0, 0), (-1, -1), 0.5, "#888888"),
        ("BACKGROUND", (0, 0), (-1, 0), "#eeeeee"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ]))
    return t


def story(meta: dict, body: str, s: dict[str, ParagraphStyle]) -> list:
    flow: list = [
        Paragraph(escape(meta["title"]), s["title"]),
        Paragraph(escape(f"架空商事株式会社　{meta['version_label']}　{meta['effective_from']} 施行"), s["meta"]),
    ]
    lines = body.splitlines()
    chapter: Paragraph | None = None
    i = 0
    while i < len(lines):
        line = lines[i].rstrip()
        if line and chapter and not line.startswith("### "):
            # 章の見出しの次が条でないときは、見出しをそのまま置く
            flow.append(chapter)
            chapter = None
        if not line:
            i += 1
        elif line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append(lines[i])
                i += 1
            flow += [table(rows, s), Spacer(1, 2 * mm)]
        elif line.startswith("> "):
            flow.append(Paragraph(inline(line[2:]), s["notice"]))
            i += 1
        elif line.startswith("## "):
            # 章の見出しは、次の条と同じページに置く（下の条の処理でまとめる）
            chapter = Paragraph(inline(line[3:]), s["chapter"])
            i += 1
            continue
        elif line.startswith("### "):
            # 条の見出しだけがページの最後に残らないよう、次の段落と一緒に置く
            heading = Paragraph(inline(line[4:]), s["article"])
            i += 1
            while i < len(lines) and not lines[i].strip():
                i += 1
            block = [chapter] if chapter else []
            chapter = None
            if i < len(lines) and not lines[i].startswith(("#", "|")):
                flow.append(KeepTogether([*block, heading, paragraph(lines[i], s)]))
                i += 1
            else:
                flow.append(KeepTogether([*block, heading]))
        else:
            flow.append(paragraph(line, s))
            i += 1
    return flow


def paragraph(line: str, s: dict[str, ParagraphStyle]) -> Paragraph:
    numbered = re.match(r"^(\d+)\.\s+(.*)$", line)
    if numbered:
        return Paragraph(f"{numbered.group(1)}　{inline(numbered.group(2))}", s["item"])
    bullet = re.match(r"^\s*[-*]\s+(.*)$", line)
    if bullet:
        return Paragraph(f"・{inline(bullet.group(1))}", s["item"])
    return Paragraph(inline(line), s["body"])


def build(source: Path, output: Path) -> int:
    meta, body = split_front_matter(source.read_text(encoding="utf-8"))
    s = styles()

    def decorate(canvas, doc) -> None:
        canvas.saveState()
        canvas.setFont(FONT, 8)
        canvas.drawString(20 * mm, A4[1] - 12 * mm, f"{meta['title']}（{meta['code']}）")
        canvas.drawCentredString(A4[0] / 2, 10 * mm, f"- {doc.page} -")
        canvas.restoreState()

    doc = SimpleDocTemplate(
        str(output),
        pagesize=A4,
        leftMargin=20 * mm,
        rightMargin=20 * mm,
        topMargin=20 * mm,
        bottomMargin=18 * mm,
        title=meta["title"],
        author="架空商事株式会社",
        subject=meta.get("summary", ""),
        invariant=1,  # 作成日時・ID を固定し、同じ入力から同じ PDF を作る
    )
    doc.build(story(meta, body, s), onFirstPage=decorate, onLaterPages=decorate)
    return doc.page


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", type=Path, default=ROOT / "data/seed/documents")
    parser.add_argument("--output", type=Path, default=ROOT / "data/pdf")
    parser.add_argument("--font", type=Path, help="日本語の TrueType フォント（既定は IPA ゴシックを探す）")
    args = parser.parse_args()

    font = args.font or next((p for p in FONT_CANDIDATES if p.exists()), None)
    if font is None or not font.exists():
        print("日本語フォントが見つかりません。--font で IPA ゴシック（ipag.ttf）を指定してください", file=sys.stderr)
        return 1
    pdfmetrics.registerFont(TTFont(FONT, str(font)))

    args.output.mkdir(parents=True, exist_ok=True)
    for source in sorted(args.source.glob("*.md")):
        output = args.output / f"{source.stem}.pdf"
        pages = build(source, output)
        print(f"{output.relative_to(ROOT) if output.is_relative_to(ROOT) else output}: {pages} ページ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
