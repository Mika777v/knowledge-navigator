#!/usr/bin/env python3
"""検索品質の評価バッチ（#39）。

起動中の backend に評価用質問（data/eval/questions.yaml）を投げ、Hit@1 / Hit@5 / MRR / 窓口正答率 / ゼロヒット率を計算する。
事前に `make up` と `make seed` を実行しておくこと。

    python3 scripts/eval/run_eval.py                      # 評価して結果を表示
    python3 scripts/eval/run_eval.py --update-baseline    # 基準値（data/eval/baseline.json）を更新

終了コード:
    0: 成功（基準値より悪化した指標があっても、警告を出すだけ）
    1: 目標（--min-hit5、既定は MVP の 70%）を下回った、窓口が1件もない結果があった、または検索に失敗した
"""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
from metrics import (  # noqa: E402
    QuestionResult,
    evaluate_question,
    regressions,
    summarize,
    summarize_by_tag,
    summary_table,
)

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_USER = "sato@example.com"


class Client:
    """ユーザーごとにログインして GET /api/v1/navigate を呼ぶ（Cookie のセッションと CSRF トークンを使う）"""

    def __init__(self, base_url: str, password: str):
        self.base_url = base_url.rstrip("/")
        self.password = password
        self.openers: dict[str, urllib.request.OpenerDirector] = {}

    def navigate(self, email: str, q: str) -> dict:
        opener = self.openers.get(email) or self._login(email)
        url = f"{self.base_url}/api/v1/navigate?" + urllib.parse.urlencode({"q": q})
        with opener.open(urllib.request.Request(url, headers={"Accept": "application/json"}), timeout=30) as res:
            return json.load(res)

    def _login(self, email: str) -> urllib.request.OpenerDirector:
        jar = http.cookiejar.CookieJar()
        # localhost 以外へ送らないよう、環境のプロキシは使わない
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(jar))
        opener.open(f"{self.base_url}/api/v1/auth/csrf", timeout=30).read()
        token = next((c.value for c in jar if c.name == "XSRF-TOKEN"), None)
        if token is None:
            raise RuntimeError("CSRF トークン（XSRF-TOKEN）を取得できませんでした")
        body = json.dumps({"email": email, "password": self.password}).encode()
        request = urllib.request.Request(
            f"{self.base_url}/api/v1/auth/login",
            data=body,
            headers={"Content-Type": "application/json", "X-XSRF-TOKEN": urllib.parse.unquote(token)},
            method="POST",
        )
        opener.open(request, timeout=30).read()
        self.openers[email] = opener
        return opener


def demo_password(env_file: Path) -> str:
    if os.environ.get("DEMO_USER_PASSWORD"):
        return os.environ["DEMO_USER_PASSWORD"]
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            if line.startswith("DEMO_USER_PASSWORD="):
                return line.split("=", 1)[1].strip()
    raise SystemExit("DEMO_USER_PASSWORD を環境変数か .env に設定してください")


def percent(value: float | None) -> str:
    return "—" if value is None else f"{value * 100:.1f}%"


def markdown_report(
    summary: dict, baseline: dict, by_tag: dict, results: list[QuestionResult], worse: list[str], min_hit5: float
) -> str:
    lines = ["## 検索品質の評価（data/eval）", "", *summary_table(summary, baseline, min_hit5), ""]
    if worse:
        lines += ["**基準値（data/eval/baseline.json）より悪化した指標**", ""] + [f"- {w}" for w in worse] + [""]
    lines += ["### タグ別", "", "| タグ | 問数 | Hit@1 | Hit@5 | 窓口正答率 |", "|---|---|---|---|---|"]
    for tag, s in by_tag.items():
        lines.append(
            f"| {tag} | {s['questions']} | {percent(s['hit1'])} | {percent(s['hit5'])} | {percent(s['contact_accuracy'])} |"
        )
    misses = [r for r in results if not r.hit5 or (r.contact_expected and not r.contact_hit)]
    if misses:
        lines += ["", "### 外れた質問", "", "| ID | 質問 | 期待 | 結果（上位） |", "|---|---|---|---|"]
        for r in misses:
            expected = ", ".join(f"{k}: {'/'.join(v)}" for k, v in r.expect.items())
            got = ", ".join(f"{k}: {'/'.join(v[:3])}" for k, v in r.got.items() if v)
            lines.append(f"| {r.id} | {r.q} | {expected} | {got or '（なし）'} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=os.environ.get("EVAL_BASE_URL", "http://localhost:8080"))
    parser.add_argument("--questions", type=Path, default=ROOT / "data/eval/questions.yaml")
    parser.add_argument("--baseline", type=Path, default=ROOT / "data/eval/baseline.json")
    parser.add_argument("--output", type=Path, default=ROOT / "build/eval", help="結果（result.json・report.md）の出力先")
    parser.add_argument("--min-hit5", type=float, default=0.70, help="Hit@5 の下限（下回ったら失敗）")
    parser.add_argument("--update-baseline", action="store_true", help="今回の結果を基準値として保存する")
    args = parser.parse_args()

    questions = yaml.safe_load(args.questions.read_text(encoding="utf-8"))
    client = Client(args.base_url, demo_password(ROOT / ".env"))

    results: list[QuestionResult] = []
    errors: list[str] = []
    for question in questions:
        try:
            response = client.navigate(question.get("as", DEFAULT_USER), question["q"])
        except Exception as e:  # noqa: BLE001 - どの質問で失敗したかを出して続ける
            errors.append(f"{question['id']}: {e}")
            continue
        results.append(evaluate_question(question, response))

    summary = summarize(results)
    by_tag = summarize_by_tag(results)
    baseline = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline.exists() else {}
    worse = regressions(summary, baseline.get("summary", {}))

    report = markdown_report(summary, baseline.get("summary", {}), by_tag, results, worse, args.min_hit5)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "report.md").write_text(report, encoding="utf-8")
    (args.output / "result.json").write_text(
        json.dumps({"summary": summary, "by_tag": by_tag}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(report)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
            f.write(report)

    if args.update_baseline:
        args.baseline.write_text(
            json.dumps({"summary": summary}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"基準値を更新しました: {args.baseline.relative_to(ROOT)}")

    failures = list(errors)
    failures += [f"{r.id}: 窓口が1件もありません（必ず1件以上返す仕様）" for r in results if not r.has_contact]
    if not results or summary["hit5"] < args.min_hit5:
        failures.append(f"Hit@5 が目標を下回りました: {percent(summary['hit5'])} < {percent(args.min_hit5)}")
    for w in worse:
        # GitHub Actions の注釈として表示する（失敗にはしない）
        print(f"::warning title=検索品質が基準値より悪化::{w}")
    for f in failures:
        print(f"::error title=検索品質の評価::{f}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
