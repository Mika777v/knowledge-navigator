"""回答文の機械的な照合（E14-T3・ADR-0013）。

番号・連絡先の検査に通らなければ、回答文を捨てて「回答できない」にする。引用のない文・引用した条にない数値を含む文は、
その文だけを取り除く（根拠のない内容は画面に出さない）。根拠のある文が1つも残らなければ「回答できない」にする。

引用のない文でも、数値を含み、その数値がすべて同じ段落の前後の引用した条にあれば残す。モデルは段落の最後の文にだけ
引用を付けることがあり、答えの数値を書いた文ほど取り除かれてしまうため。数値のない文は引用がなければ取り除く。
"""

import re
import unicodedata
from dataclasses import dataclass, field

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
    # 照合を通った文だけをつないだ回答文（通らなければ None）
    answer: str | None = None
    # 取り除いた文ごとの、通らなかった検査
    dropped: list[str] = field(default_factory=list)


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
    # 画面に出す文は全角・半角だけをそろえ、桁区切りなどの書き方は元のまま残す。比べるときは normalize する
    display = unicodedata.normalize("NFKC", answer)
    text = normalize(answer)
    citations = list(dict.fromkeys(int(n) for n in _CITATION.findall(text)))
    if not citations:
        return Verification(False, "NO_CITATION", [])
    if any(ref not in sources for ref in citations):
        return Verification(False, "UNKNOWN_CITATION", citations)
    if _EMAIL.search(text) or _PHONE.search(text):
        return Verification(False, "CONTACT_IN_ANSWER", citations)

    normalized_sources = {ref: normalize(source) for ref, source in sources.items()}
    kept: list[str] = []
    dropped: list[str] = []
    sentences = _sentences(display)
    refs_of = [[int(n) for n in _CITATION.findall(sentence)] for _, sentence in sentences]
    for i, (paragraph, sentence) in enumerate(sentences):
        refs = refs_of[i]
        body = _CITATION.sub("", normalize(sentence)).strip()
        numbers = _NUMBER.findall(body)
        if not refs and len(body) >= SHORT_SENTENCE:
            neighbors = _neighbor_refs(sentences, refs_of, i, paragraph)
            if not numbers or not neighbors or not _all_in(numbers, neighbors, normalized_sources):
                dropped.append("UNCITED_SENTENCE")
                continue
        # 短い前置きの数値は、引用されたどれかの根拠にあればよい
        elif not _all_in(numbers, refs or citations, normalized_sources):
            dropped.append("NUMBER_NOT_IN_SOURCE")
            continue
        kept.append(sentence)

    kept_citations = list(dict.fromkeys(int(n) for s in kept for n in _CITATION.findall(s)))
    if not kept_citations:
        return Verification(False, dropped[0] if dropped else "NO_CITATION", citations, dropped=dropped)
    return Verification(True, None, kept_citations, answer="".join(kept), dropped=dropped)


def _all_in(numbers: list[tuple[str, str]], refs: list[int], sources: dict[int, str]) -> bool:
    cited = "\n".join(sources[ref] for ref in refs)
    # 前後に数字が続く別の数値（30 と 30000 など）と取りちがえない
    return all(re.search(rf"(?<![\d.]){re.escape(number + unit)}(?![\d.])", cited) for number, unit in numbers)


def _neighbor_refs(sentences: list[tuple[int, str]], refs_of: list[list[int]], i: int, paragraph: int) -> list[int]:
    """同じ段落で、前と後ろのいちばん近い引用のある文の番号"""
    refs: list[int] = []
    for step in (-1, 1):
        j = i + step
        while 0 <= j < len(sentences) and sentences[j][0] == paragraph:
            if refs_of[j]:
                refs += refs_of[j]
                break
            j += step
    return list(dict.fromkeys(refs))


def _sentences(text: str) -> list[tuple[int, str]]:
    """文に分け、段落の番号と組にする。引用だけの行（「[1]」）は前の段落に含める"""
    moved = _TRAILING_CITATIONS.sub(lambda m: m.group(2).strip() + m.group(1), text)
    result: list[tuple[int, str]] = []
    paragraph = -1
    for line in moved.split("\n"):
        parts = [s.strip() for s in _SENTENCE_END.split(line) if s.strip()]
        if not parts:
            continue
        if paragraph < 0 or _CITATION.sub("", line).strip():
            paragraph += 1
        result += [(paragraph, part) for part in parts]
    return result
