#!/usr/bin/env python3
"""根拠付き回答（RAG）の評価バッチ（E14-T6）。

起動中の backend に評価用質問（data/eval/rag.yaml）を投げ（POST /api/v1/assistant/answers、SSE）、
引用正確性・要点の正答率・回答率・回答不能の適切さ・回答までの時間を計算する。
事前に `make up-ai` と `make seed` を実行し、ベクトルの合わせを終えておくこと（make eval-rag が待つ）。

    python3 scripts/eval/run_rag_eval.py                      # 評価して結果を表示
    python3 scripts/eval/run_rag_eval.py --min-citation 0.95  # 引用正確性が下回ったら失敗にする

終了コード:
    0: 成功（既定では、指標が低くても失敗にしない。本物のモデルの速さと品質は環境で変わるため）
    1: --min-citation を下回った、または回答の API の呼び出しに失敗した
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rag_metrics import RagResult, evaluate_rag_question, parse_sse, summarize_rag  # noqa: E402
from run_eval import DEFAULT_USER, ROOT, Client, demo_password, display_path, percent  # noqa: E402

# 回答文の生成を待つ時間（backend は 150 秒で SSE を閉じる）
ANSWER_TIMEOUT_SECONDS = 180


class RagClient(Client):
    def answer(self, email: str, q: str) -> str:
        opener = self.openers.get(email) or self._login(email)
        token = next((c.value for c in self.jars[email] if c.name == "XSRF-TOKEN"), "")
        request = urllib.request.Request(
            f"{self.base_url}/api/v1/assistant/answers",
            data=json.dumps({"question": q}).encode(),
            headers={
                "Content-Type": "application/json",
                "Accept": "text/event-stream",
                "X-XSRF-TOKEN": urllib.parse.unquote(token),
            },
            method="POST",
        )
        # SSE は done を送ったあとに閉じるので、閉じるまで読む
        with opener.open(request, timeout=ANSWER_TIMEOUT_SECONDS) as res:
            return res.read().decode("utf-8")


def document_titles(seed_dir: Path) -> dict[str, str]:
    titles = {}
    for path in (seed_dir / "documents").glob("*.md"):
        front = path.read_text(encoding="utf-8").split("---\n", 2)[1]
        meta = yaml.safe_load(front) or {}
        titles[meta["code"]] = meta["title"]
    return titles


def markdown_report(summary: dict, results: list[RagResult]) -> str:
    lines = [
        "## 根拠付き回答の評価（data/eval/rag.yaml）",
        "",
        "| 指標 | 今回 | 目標 |",
        "|---|---|---|",
        f"| 引用正確性 | {percent(summary['citation_accuracy'])} | 95% 以上 |",
        f"| 要点の正答率 | {percent(summary['facts_rate'])} | — |",
        f"| 文脈の再現率（正解の条を AI に渡せた割合） | {percent(summary['context_recall'])} | — |",
        f"| 回答率 | {percent(summary['answer_rate'])} | — |",
        f"| 回答不能の適切さ | {percent(summary['abstain_rate'])} | — |",
        f"| 回答までの時間（中央値 / 最大） | {summary['seconds_median']} 秒 / {summary['seconds_max']} 秒 | — |",
        "",
    ]
    if summary["reasons"]:
        lines += ["回答できなかった理由: " + "、".join(f"{k} {v}問" for k, v in summary["reasons"].items()), ""]
    misses = [r for r in results if (r.answerable and not (r.answered and r.citation_ok and r.facts_ok))]
    misses += [r for r in results if not r.answerable and r.answered]
    if misses:
        lines += ["### 外れた質問", "", "| ID | 質問 | 結果 | 文脈に正解 | 引用 |", "|---|---|---|---|---|"]
        for r in misses:
            if not r.answered:
                got = f"回答できない（{r.reason}）"
            elif not r.answerable:
                got = "記載がないのに回答した"
            else:
                got = ("引用ちがい " if not r.citation_ok else "") + ("要点ちがい" if not r.facts_ok else "")
            hit = "—" if not r.answerable else ("あり" if r.context_hit else "なし")
            lines.append(f"| {r.id} | {r.q} | {got.strip()} | {hit} | {'、'.join(r.cited) or '—'} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=os.environ.get("EVAL_BASE_URL", "http://localhost:8080"))
    parser.add_argument("--questions", type=Path, default=ROOT / "data/eval/rag.yaml")
    parser.add_argument("--seed", type=Path, default=ROOT / "data/seed")
    parser.add_argument("--output", type=Path, default=ROOT / "build/eval-rag", help="結果（result.json・report.md）の出力先")
    parser.add_argument("--min-citation", type=float, default=None, help="引用正確性の下限（下回ったら失敗）")
    args = parser.parse_args()

    questions = yaml.safe_load(args.questions.read_text(encoding="utf-8"))
    titles = document_titles(args.seed)
    client = RagClient(args.base_url, demo_password(ROOT / ".env"))
    results: list[RagResult] = []
    for q in questions:
        started = time.monotonic()
        try:
            body = client.answer(q.get("as", DEFAULT_USER), q["q"])
        except Exception as e:  # noqa: BLE001 - 1問の失敗で全体を止めず、終了コードで知らせる
            print(f"NG {q['id']}: 回答の API の呼び出しに失敗しました: {e}", file=sys.stderr)
            return 1
        result = evaluate_rag_question(q, parse_sse(body), time.monotonic() - started, titles)
        results.append(result)
        mark = "OK" if (result.answered == result.answerable and (not result.answered or result.citation_ok)) else "--"
        print(f"{mark} {q['id']} {result.seconds:5.1f}s {result.reason or '回答'} {q['q']}")

    summary = summarize_rag(results)
    report = markdown_report(summary, results)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "report.md").write_text(report, encoding="utf-8")
    (args.output / "result.json").write_text(
        json.dumps({"summary": summary, "results": [r.__dict__ for r in results]}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print()
    print(report)
    print(f"結果: {display_path(args.output / 'report.md')}")
    if args.min_citation is not None and (summary["citation_accuracy"] or 0) < args.min_citation:
        print(f"NG: 引用正確性が {args.min_citation:.0%} を下回りました", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
