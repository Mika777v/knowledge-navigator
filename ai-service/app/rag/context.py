"""回答の文脈を作る（E14-T1）。backend が付けた番号のまま、区切りで囲んで並べる（ADR-0009）"""

import re
from dataclasses import dataclass

_DELIMITER = re.compile(r"<(/?)passage", re.IGNORECASE)


@dataclass(frozen=True)
class Passage:
    # backend が付けた根拠の番号（1〜）。回答文の [n] と、画面の根拠をつなぐ
    ref: int
    # 文書名と条（例: 慶弔見舞金規程 第5条（結婚祝金））
    title: str
    text: str


@dataclass(frozen=True)
class Context:
    text: str
    # 文脈に入れた根拠の番号。上限で入らなかった番号は、回答で引用されても根拠にしない
    refs: list[int]


def build_context(passages: list[Passage], max_chars: int, max_passage_chars: int) -> Context:
    """渡された順（検索の順位の順）に、文字数の上限まで入れる。1件目は長くても切り詰めて必ず入れる"""
    blocks: list[str] = []
    refs: list[int] = []
    total = 0
    for passage in passages:
        text = _neutralize(passage.text.strip())
        if len(text) > max_passage_chars:
            text = text[:max_passage_chars] + "…"
        block = f'<passage ref="{passage.ref}" title="{_attribute(passage.title)}">\n{text}\n</passage>'
        if blocks and total + len(block) > max_chars:
            break
        blocks.append(block)
        refs.append(passage.ref)
        total += len(block)
    return Context(text="\n\n".join(blocks), refs=refs)


def _neutralize(text: str) -> str:
    # 本文の中の区切りに似た文字列で、文脈の外に抜け出せないようにする
    # （プロンプトインジェクション対策。ARCHITECTURE.md §5.5）
    return _DELIMITER.sub(r"＜\1passage", text)


def _attribute(title: str) -> str:
    return _neutralize(title).replace('"', "”").replace("\n", " ")
