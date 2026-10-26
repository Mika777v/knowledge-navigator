#!/usr/bin/env python3
"""検索スコアの重みのグリッド探索（E12-T4。docs/ARCHITECTURE.md §4.3）。

重みの組み合わせごとに backend の jar を起動し直し（knavi.navigation.weights.* を起動引数で渡す）、
評価用の質問（data/eval/questions.yaml）で Hit@5 → MRR → Hit@1 の順に良いものを探す。
DB は起動中のもの（make up・make seed 済み）をそのまま使う。接続先などは環境変数で渡す（backend と同じ名前）。

    cd backend && ./gradlew bootJar
    python3 scripts/eval/tune_weights.py --jar backend/build/libs/knowledge-navigator-backend-0.0.1-SNAPSHOT.jar \\
        --grid program-name=2.0,2.5,3.0 --grid faq-question=1.5,2.0,2.5

結果は build/eval/tuning.md・tuning.json に出す。重みを変えるときは、application.yaml を直して同じ PR に結果を書く。
"""

from __future__ import annotations

import argparse
import itertools
import json
import os
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
from metrics import evaluate_question, summarize  # noqa: E402
from run_eval import DEFAULT_USER, ROOT, Client, demo_password, percent  # noqa: E402

# 既定のグリッド（今の値を真ん中にする）
DEFAULT_GRID = {
    "dictionary": [2.5, 3.0, 3.5],
    "program-name": [2.0, 2.5, 3.0],
    "faq-question": [1.5, 2.0, 2.5],
}


def parse_grid(specs: list[str]) -> dict[str, list[float]]:
    """「program-name=2.0,2.5」の並びを {名前: [値]} にする"""
    grid: dict[str, list[float]] = {}
    for spec in specs:
        name, _, values = spec.partition("=")
        if not name or not values:
            raise SystemExit(f"--grid は 名前=値,値 の形で指定してください: {spec}")
        grid[name.strip()] = [float(v) for v in values.split(",")]
    return grid


def combinations(grid: dict[str, list[float]]) -> list[dict[str, float]]:
    names = list(grid)
    return [dict(zip(names, values, strict=True)) for values in itertools.product(*(grid[n] for n in names))]


def rank_key(summary: dict) -> tuple:
    """良い順に並べるための鍵（Hit@5 → MRR → Hit@1 → ゼロヒット率の低さ）"""
    return (summary["hit5"] or 0, summary["mrr"] or 0, summary["hit1"] or 0, -(summary["zero_hit_rate"] or 0))


def wait_healthy(base_url: str, timeout: float) -> None:
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with opener.open(f"{base_url}/actuator/health", timeout=5) as res:
                if res.status == 200:
                    return
        except OSError:
            pass
        time.sleep(1)
    raise RuntimeError("backend が起動しませんでした")


def evaluate(jar: Path, port: int, weights: dict[str, float], questions: list[dict], password: str, log: Path) -> dict:
    args = ["java", "-jar", str(jar), f"--server.port={port}"]
    args += [f"--knavi.navigation.weights.{name}={value}" for name, value in weights.items()]
    with log.open("w", encoding="utf-8") as out:
        process = subprocess.Popen(args, stdout=out, stderr=subprocess.STDOUT, env=os.environ.copy())
    try:
        base_url = f"http://localhost:{port}"
        wait_healthy(base_url, timeout=120)
        client = Client(base_url, password)
        results = [evaluate_question(q, client.navigate(q.get("as", DEFAULT_USER), q["q"])) for q in questions]
        return summarize(results)
    finally:
        process.terminate()
        process.wait(timeout=30)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", type=Path, required=True, help="backend の jar（./gradlew bootJar で作る）")
    parser.add_argument("--port", type=int, default=18090, help="探索中に backend を起動するポート")
    parser.add_argument("--grid", action="append", default=[], help="名前=値,値（複数指定可。省略時は既定のグリッド）")
    parser.add_argument("--questions", type=Path, default=ROOT / "data/eval/questions.yaml")
    parser.add_argument("--output", type=Path, default=ROOT / "build/eval")
    args = parser.parse_args()

    grid = parse_grid(args.grid) if args.grid else DEFAULT_GRID
    questions = yaml.safe_load(args.questions.read_text(encoding="utf-8"))
    password = demo_password(ROOT / ".env")
    args.output.mkdir(parents=True, exist_ok=True)

    rows = []
    combos = combinations(grid)
    for i, weights in enumerate(combos, 1):
        summary = evaluate(args.jar, args.port, weights, questions, password, args.output / "tuning-backend.log")
        rows.append({"weights": weights, "summary": summary})
        print(f"[{i}/{len(combos)}] {weights} → Hit@5 {percent(summary['hit5'])} MRR {summary['mrr']}", flush=True)

    rows.sort(key=lambda r: rank_key(r["summary"]), reverse=True)
    (args.output / "tuning.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    names = list(grid)
    lines = [
        "## 重みのグリッド探索（data/eval）",
        "",
        "| " + " | ".join(names) + " | Hit@1 | Hit@5 | MRR | ゼロヒット率 |",
        "|" + "---|" * (len(names) + 4),
    ]
    for r in rows:
        s = r["summary"]
        cells = [str(r["weights"][n]) for n in names]
        cells += [percent(s["hit1"]), percent(s["hit5"]), f"{s['mrr']:.3f}", percent(s["zero_hit_rate"])]
        lines.append("| " + " | ".join(cells) + " |")
    report = "\n".join(lines) + "\n"
    (args.output / "tuning.md").write_text(report, encoding="utf-8")
    print(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
