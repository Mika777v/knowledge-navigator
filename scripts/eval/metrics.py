"""検索品質の指標（data/eval/README.md の定義）を計算する。HTTP やファイルに依存しない部分だけを置く。"""

from __future__ import annotations

from dataclasses import dataclass, field

# 正解を探すブロック（窓口は別の指標で見る）
RANKED_BLOCKS = ("programs", "documents", "faqs")
TOP_K = 5


@dataclass
class QuestionResult:
    """1問の評価結果"""

    id: str
    q: str
    tags: list[str]
    no_answer: bool
    hit1: bool
    hit5: bool
    reciprocal_rank: float
    contact_expected: bool
    contact_hit: bool
    zero_hit: bool
    has_contact: bool
    got: dict[str, list[str]] = field(default_factory=dict)
    expect: dict[str, list[str]] = field(default_factory=dict)


def evaluate_question(question: dict, response: dict) -> QuestionResult:
    """質問1件の検索結果を評価する。response は GET /api/v1/navigate のレスポンス"""
    expect = question.get("expect") or {}
    got = {block: [item["code"] for item in response.get(block, [])] for block in (*RANKED_BLOCKS, "contacts")}
    ranked_expected = any(block in expect for block in RANKED_BLOCKS)

    best_rank = None
    for block in RANKED_BLOCKS:
        wanted = set(expect.get(block, []))
        for rank, code in enumerate(got[block], start=1):
            if code in wanted:
                best_rank = rank if best_rank is None else min(best_rank, rank)
                break

    if ranked_expected:
        hit1 = best_rank == 1
        hit5 = best_rank is not None and best_rank <= TOP_K
        rr = 1.0 / best_rank if best_rank else 0.0
    else:
        # 窓口だけを期待する質問（該当なし・窓口案内）は、順位の指標では正解扱いにする
        hit1 = hit5 = True
        rr = 1.0

    contacts_wanted = set(expect.get("contacts", []))
    return QuestionResult(
        id=question["id"],
        q=question["q"],
        tags=list(question.get("tags", [])),
        no_answer="no-answer" in question.get("tags", []) or bool(question.get("fallback")),
        hit1=hit1,
        hit5=hit5,
        reciprocal_rank=rr,
        contact_expected=bool(contacts_wanted),
        contact_hit=bool(contacts_wanted & set(got["contacts"])),
        zero_hit=all(not got[block] for block in RANKED_BLOCKS),
        has_contact=bool(got["contacts"]),
        got=got,
        expect={k: list(v) for k, v in expect.items()},
    )


def summarize(results: list[QuestionResult]) -> dict:
    """指標をまとめる（割合は 0〜1、小数第4位まで。対象の質問がない指標は None）"""

    def ratio(numerator: int, denominator: int) -> float | None:
        # 対象の質問がない指標は None（0% と区別する）
        return round(numerator / denominator, 4) if denominator else None

    answerable = [r for r in results if not r.no_answer]
    contact_targets = [r for r in results if r.contact_expected]
    return {
        "questions": len(results),
        "hit1": ratio(sum(r.hit1 for r in results), len(results)),
        "hit5": ratio(sum(r.hit5 for r in results), len(results)),
        "mrr": round(sum(r.reciprocal_rank for r in results) / len(results), 4) if results else 0.0,
        "contact_accuracy": ratio(sum(r.contact_hit for r in contact_targets), len(contact_targets)),
        "zero_hit_rate": ratio(sum(r.zero_hit for r in answerable), len(answerable)),
    }


def summarize_by_tag(results: list[QuestionResult]) -> dict[str, dict]:
    tags = sorted({tag for r in results for tag in r.tags})
    return {tag: summarize([r for r in results if tag in r.tags]) for tag in tags}


# 大きいほど良い指標 / 小さいほど良い指標
HIGHER_IS_BETTER = ("hit1", "hit5", "mrr", "contact_accuracy")
LOWER_IS_BETTER = ("zero_hit_rate",)


def regressions(current: dict, baseline: dict) -> list[str]:
    """基準値（main で記録した値）より悪くなった指標を返す"""
    worse = []
    for key in HIGHER_IS_BETTER:
        if baseline.get(key) is not None and current.get(key) is not None and current[key] < baseline[key]:
            worse.append(f"{key}: {baseline[key]:.4f} → {current[key]:.4f}")
    for key in LOWER_IS_BETTER:
        if baseline.get(key) is not None and current.get(key) is not None and current[key] > baseline[key]:
            worse.append(f"{key}: {baseline[key]:.4f} → {current[key]:.4f}")
    return worse


def _format(key: str, value: float) -> str:
    return f"{value:.3f}" if key == "mrr" else f"{value * 100:.1f}%"


def summary_table(current: dict, baseline: dict, min_hit5: float) -> list[str]:
    """指標の表（Markdown の行）。基準値があれば並べて、変化を ▲（良くなった）・▼（悪くなった）で示す"""
    labels = {
        "hit1": "Hit@1",
        "hit5": "Hit@5",
        "mrr": "MRR",
        "contact_accuracy": "窓口正答率",
        "zero_hit_rate": "ゼロヒット率",
    }
    lines = ["| 指標 | 今回 | 基準値 | 変化 |", "|---|---|---|---|"]
    for key, label in labels.items():
        value, base = current.get(key), baseline.get(key)
        shown = "—" if value is None else _format(key, value)
        if key == "hit5":
            shown += f"（目標 {min_hit5 * 100:.1f}% 以上）"
        change = ""
        if value is not None and base is not None and round(value - base, 4) != 0:
            better = value > base if key in HIGHER_IS_BETTER else value < base
            change = "▲ 良くなった" if better else "▼ 悪くなった"
        lines.append(f"| {label} | {shown} | {'—' if base is None else _format(key, base)} | {change} |")
    return lines
