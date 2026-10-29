"""回答文の機械的な照合（E14-T3・ADR-0013）。1つでも通らなければ、回答文を捨てて「回答できない」にする"""

import re
import unicodedata
from dataclasses import dataclass

_CITATION = re.compile(r"\[(\d+)\]")
# 文末の記号の直後にある引用（「…です。[1]」）は、その文のものとして扱う
_TRAILING_CITATIONS = re.compile(r"([。！？!?])((?:\s*\[\d+\])+)")
_SENTENCE_END = re.compile(r"(?<=[。！？!?])|\n+")
# 金額・日数・期限・割合などの数値（単位があれば単位も含めて照らし合わせる）
# 「3万円」のような漢字の位取りは単位として扱い、原文（30,000円）にないので通さない
_NUMBER = re.compile(
    r"(?<![\d.])(\d+(?:\.\d+)?)(営業日|か月|週間|時間|万|千|億|円|日|年|週|分|歳|%|回|km|文字|人|件|条)?"
)
_EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
_PHONE = re.compile(r"\d{2,4}-\d{2,4}-\d{3,4}|内線\s*\d+")
# 引用がなくてもよい短い文（「以下のとおりです。」など）の長さ
SHORT_SENTENCE = 15


@dataclass(frozen=True)
class Verification:
    ok: bool
    # 通らなかった検査（UNKNOWN_CITATION / NO_CITATION / UNCITED_SENTENCE / NUMBER_NOT_IN_SOURCE / CONTACT_IN_ANSWER）
    failed_check: str | None
    citations: list[int]


def normalize(text: str) -> str:
    """全角・半角をそろえ、桁区切りのカンマ、数字と単位の間の空白（28 日）、「ヶ月・カ月」の書き方のちがいをなくす"""
    value = unicodedata.normalize("NFKC", text)
    value = re.sub(r"(?<=\d),(?=\d{3})", "", value)
    value = re.sub(r"(?<=\d)[ \t]+(?=[^\s\d\[])", "", value)
    return re.sub(r"(?<=\d)[ヶケカヵ]月", "か月", value)


def verify(answer: str, sources: dict[int, str]) -> Verification:
    """
    :param answer: LLM が作った回答文
    :param sources: 文脈に入れた根拠の番号 → 文書名・条と本文（文脈に入らなかった番号は含めない）
    """
    text = normalize(answer)
    citations = list(dict.fromkeys(int(n) for n in _CITATION.findall(text)))
    if not citations:
        return Verification(False, "NO_CITATION", [])
    if any(ref not in sources for ref in citations):
        return Verification(False, "UNKNOWN_CITATION", citations)
    if _EMAIL.search(text) or _PHONE.search(text):
        return Verification(False, "CONTACT_IN_ANSWER", citations)

    normalized_sources = {ref: normalize(source) for ref, source in sources.items()}
    for sentence in _sentences(text):
        refs = [int(n) for n in _CITATION.findall(sentence)]
        body = _CITATION.sub("", sentence).strip()
        if not refs:
            if len(body) >= SHORT_SENTENCE:
                return Verification(False, "UNCITED_SENTENCE", citations)
            # 短い前置きの数値は、引用されたどれかの根拠にあればよい
            refs = citations
        cited = "\n".join(normalized_sources[ref] for ref in refs)
        for number, unit in _NUMBER.findall(body):
            # 前後に数字が続く別の数値（30 と 30000 など）と取りちがえない
            if not re.search(rf"(?<![\d.]){re.escape(number + unit)}(?![\d.])", cited):
                return Verification(False, "NUMBER_NOT_IN_SOURCE", citations)
    return Verification(True, None, citations)


def _sentences(text: str) -> list[str]:
    moved = _TRAILING_CITATIONS.sub(lambda m: m.group(2).strip() + m.group(1), text)
    return [s.strip() for s in _SENTENCE_END.split(moved) if s.strip()]
