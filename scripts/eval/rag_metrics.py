"""根拠付き回答（RAG）の指標（data/eval/README.md の「RAG の質問セット」）。HTTP やファイルに依存しない部分だけを置く。"""

from __future__ import annotations

import json
import re
import statistics
import unicodedata
from dataclasses import dataclass, field


@dataclass
class RagResult:
    """1問の評価結果"""

    id: str
    q: str
    tags: list[str]
    answerable: bool
    # 回答文が返った（unanswerable でなかった）
    answered: bool
    # 回答できなかった理由（DISABLED / NO_EVIDENCE / NOT_IN_CONTEXT / GENERATION_FAILED / VERIFICATION_FAILED）
    reason: str | None
    # 引用に expect.sections の条が1つ以上含まれる（回答できたときだけ意味がある）
    citation_ok: bool
    # expect.facts がすべて回答文に含まれる（回答できたときだけ意味がある）
    facts_ok: bool
    seconds: float
    # 正解の条が、AI に渡した条（文脈）の候補に入っていた
    context_hit: bool = False
    answer: str | None = None
    cited: list[str] = field(default_factory=list)


def parse_sse(text: str) -> list[tuple[str, dict]]:
    """Server-Sent Events の本文を (イベント名, データ) の並びにする"""
    events = []
    for block in re.split(r"\r?\n\r?\n", text):
        name, data = "message", []
        for line in block.splitlines():
            if line.startswith("event:"):
                name = line[len("event:") :].strip()
            elif line.startswith("data:"):
                data.append(line[len("data:") :].removeprefix(" "))
        if data:
            events.append((name, json.loads("\n".join(data))))
    return events


def normalize(text: str) -> str:
    """全角・半角、桁区切りのカンマ、数字と単位の間の空白、「ヶ月・カ月」の書き方のちがいをそろえる（ai-service の照合と同じ）"""
    value = unicodedata.normalize("NFKC", text)
    value = re.sub(r"(?<=\d),(?=\d{3})", "", value)
    value = re.sub(r"(?<=\d)[ \t]+(?=[^\s\d\[])", "", value)
    return re.sub(r"(?<=\d)[ヶケカヵ]月", "か月", value)


# backend（AnswerService）が文脈にする条の数の上限（knavi.assistant.max-passages）と、根拠を見る上位の制度の数
MAX_PASSAGES = 6
TOP_PROGRAMS = 3


def context_candidates(results: dict | None) -> list[tuple[str, str]]:
    """4ブロックの結果から、backend が文脈にする条を同じ順で選ぶ（上位の制度の根拠 → 上位の文書の条）"""
    if not results:
        return []
    candidates: list[tuple[str, str]] = []
    for program in (results.get("programs") or [])[:TOP_PROGRAMS]:
        for basis in program.get("basis") or []:
            if basis.get("sectionId"):
                candidates.append((basis.get("documentTitle"), basis.get("articleNo")))
    for document in results.get("documents") or []:
        for section in document.get("sections") or []:
            candidates.append((document.get("title"), section.get("articleNo")))
    return list(dict.fromkeys(candidates))[:MAX_PASSAGES]


def evaluate_rag_question(
    q: dict, events: list[tuple[str, dict]], seconds: float, document_titles: dict[str, str]
) -> RagResult:
    """
    :param document_titles: 文書のコード → 文書名（回答の根拠は文書名と条で返るため）
    """
    results = next((data for name, data in events if name == "results"), None)
    answer = next((data for name, data in events if name == "answer"), None)
    unanswerable = next((data for name, data in events if name == "unanswerable"), None)
    expect = q.get("expect") or {}
    expected = {(document_titles.get(s["document"], s["document"]), s["article"]) for s in expect.get("sections") or []}
    cited = [(e.get("documentTitle"), e.get("articleNo")) for e in (answer or {}).get("evidence", [])]
    text = normalize((answer or {}).get("answer") or "")
    return RagResult(
        id=q["id"],
        q=q["q"],
        tags=q.get("tags") or [],
        answerable=q.get("answerable", True),
        answered=answer is not None,
        reason=(unanswerable or {}).get("reason"),
        citation_ok=answer is not None and any(c in expected for c in cited),
        facts_ok=answer is not None and all(normalize(f) in text for f in expect.get("facts") or []),
        seconds=seconds,
        context_hit=any(c in expected for c in context_candidates(results)),
        answer=(answer or {}).get("answer"),
        cited=[f"{title} {article}" for title, article in cited],
    )


def summarize_rag(results: list[RagResult]) -> dict:
    answerable = [r for r in results if r.answerable]
    answered = [r for r in answerable if r.answered]
    no_answer = [r for r in results if not r.answerable]
    times = [r.seconds for r in results]
    return {
        "questions": len(results),
        # 回答できた質問のうち、正しい条を引用した割合（目標 95% 以上、docs/ROADMAP.md M6）
        "citation_accuracy": _ratio(sum(r.citation_ok for r in answered), len(answered)),
        # 回答できるはずの質問のうち、正解の条を AI に渡せた割合（検索の Recall@6。低ければ文脈の選び方を直す）
        "context_recall": _ratio(sum(r.context_hit for r in answerable), len(answerable)),
        "facts_rate": _ratio(sum(r.facts_ok for r in answered), len(answered)),
        "answer_rate": _ratio(len(answered), len(answerable)),
        # 規程に記載がない質問で、回答文を出さなかった割合
        "abstain_rate": _ratio(sum(not r.answered for r in no_answer), len(no_answer)),
        "seconds_median": round(statistics.median(times), 1) if times else None,
        "seconds_max": round(max(times), 1) if times else None,
        "reasons": _count(r.reason for r in results if r.reason),
    }


def _ratio(numerator: int, denominator: int) -> float | None:
    return None if denominator == 0 else numerator / denominator


def _count(values) -> dict[str, int]:
    counts: dict[str, int] = {}
    for value in values:
        counts[value] = counts.get(value, 0) + 1
    return dict(sorted(counts.items()))
