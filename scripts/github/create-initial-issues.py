"""docs/TASKS.md の Epic 16件と初期 Issue 上位20件を起票し、Sub-issue として紐づける。
同じタイトルの Issue があれば作成しない（冪等）。
使い方: python3 scripts/github/create-initial-issues.py [repo_root] [出力JSON]"""
import json, re, subprocess, sys

REPO = "Mika777v/knowledge-navigator"
ROOT = sys.argv[1] if len(sys.argv) > 1 else "."


def gh(method, path, payload=None):
    cmd = ["gh", "api", "-X", method, f"repos/{REPO}/{path}"]
    if payload is not None:
        cmd += ["--input", "-"]
    r = subprocess.run(cmd, input=json.dumps(payload) if payload is not None else None,
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"{method} {path}: {r.stdout} {r.stderr}")
    return json.loads(r.stdout) if r.stdout.strip() else None


existing = {}
page = 1
while True:
    items = gh("GET", f"issues?state=all&per_page=100&page={page}")
    if not items:
        break
    for i in items:
        if "pull_request" not in i:
            existing[i["title"]] = i
    page += 1


def create(title, body, labels, milestone=None):
    if title in existing:
        print(f"exists #{existing[title]['number']} {title}")
        return existing[title]
    payload = {"title": title, "body": body, "labels": labels}
    if milestone:
        payload["milestone"] = milestone
    issue = gh("POST", "issues", payload)
    existing[title] = issue
    print(f"created #{issue['number']} {title}")
    return issue


# ---------- Epics ----------
EPICS = {  # id: (phase, area, milestone, goal)
    "E01": ("0", "infra", 1, "`make up` で全サービスが起動し、CI が回る開発基盤を整える"),
    "E02": ("0", "search", 1, "架空企業のダミーデータと評価用質問セットで、全機能を試せ・品質を測れる状態にする"),
    "E03": ("1", "auth", 1, "ロールと公開範囲・所管部署による認可を、検索・閲覧・API の全経路で強制する"),
    "E04": ("1", "backend", 1, "部署・窓口・ユーザーを管理し、問い合わせ先を案内できるようにする"),
    "E05": ("1", "backend", 1, "文書を版・条文セクション単位で管理し、該当箇所へリンクできるようにする"),
    "E06": ("1", "backend", 1, "制度・きっかけ・FAQ を構造化データとして管理し、根拠条文と窓口に紐づける"),
    "E07": ("1", "search", 1, "自然文の質問から制度・文書・該当箇所・FAQ・窓口を構造化して返す"),
    "E08": ("1", "frontend", 1, "利用者がホームから検索・きっかけ・詳細閲覧まで迷わず辿れる UI を作る"),
    "E09": ("1", "frontend", 1, "人事・総務・情シスがコンテンツとマスタを管理できる画面を作る"),
    "E10": ("1", "search", 1, "検索ログ・フィードバック・評価バッチで検索品質を数値で改善できるようにする"),
    "E11": ("2", "ingest", 2, "PDF 規程を取り込み、条文・ページ単位で全文検索できるようにする"),
    "E12": ("3", "search", 3, "類義語・正規化・推薦で検索精度（Hit@5 ≥ 85%）を高める"),
    "E13": ("4", "ai", 4, "Embedding とハイブリッド検索で言い換えに強い検索にする"),
    "E14": ("5", "ai", 5, "根拠を必ず提示し、根拠不足なら回答しない RAG を実装する"),
    "E15": ("6", "ai", 6, "対話で状況を確認し、該当制度と申請手順を整理するアシスタントを試作する"),
    "E16": ("1", "infra", 1, "セキュリティスキャン・リリース・デモ公開・README を整える（全フェーズ横断）"),
}

tasks_md = open(f"{ROOT}/docs/TASKS.md", encoding="utf-8").read()
sections = re.split(r"\n### ", tasks_md.split("## 2. Story / Task 分解", 1)[1].split("## 3.", 1)[0])
epic_rows = {}
for sec in sections[1:]:
    head, *lines = sec.splitlines()
    eid, name = head.split(" ", 1)
    rows = [l for l in lines if l.startswith("| E")]
    epic_rows[eid] = (re.sub(r"（Phase\d）", "", name).strip(), rows)

epic_issues = {}
for eid, (phase, area, ms, goal) in EPICS.items():
    name, rows = epic_rows[eid]
    checklist = []
    for r in rows:
        cols = [c.strip() for c in r.strip("|").split("|")]
        checklist.append(f"- [ ] {cols[0]} {cols[1]}: {cols[2]}" + (f"（{cols[3]}）" if cols[3] not in ("—", "") else ""))
    body = (
        f"### Epic ID\n\n{eid}\n\n### ゴール\n\n{goal}\n\n### Phase\n\n{phase}\n\n"
        f"### スコープ（docs/TASKS.md より）\n\n" + "\n".join(checklist) + "\n\n"
        "起票済みの Issue は Sub-issues として紐づける。未起票のものは着手時に Story / Task として起票する。\n\n"
        "### 参考\n\n- docs/TASKS.md\n- docs/ROADMAP.md\n"
    )
    labels = ["type:epic", f"phase:{phase}", f"area:{area}"]
    epic_issues[eid] = create(f"[Epic] {eid} {name}", body, labels, ms)

# ---------- Top 20 tasks ----------
DOD = ("- [ ] 受け入れ条件・作業内容を満たす\n- [ ] テストを追加・更新した（コードの場合）\n"
       "- [ ] CI がグリーン\n- [ ] 必要なドキュメント（docs/ / ADR）を更新した")
TASKS = [
    # (no, title, epic, size, areas, phase, prio, deps, refs, work)
    (1, "chore: リポジトリ初期設定（ブランチ保護・ラベル・Projects・テンプレート）", "E01", "S", ["infra"], "0", "P1", [], "E01-T1, E01-T2",
     "- Issue/PR テンプレート、CODEOWNERS、dependabot、ラベル同期\n- マージ方式・セキュリティ・Milestone・Ruleset（`scripts/github/setup-repo.sh`）\n- GitHub Projects とビュー（`scripts/github/setup-project.sh` + 手動）\n- 手順は docs/REPOSITORY_SETUP.md"),
    (2, "docs: ADR 0001〜0004 を作成", "E01", "S", ["infra"], "0", "P1", [1], "E01-T9, docs/TECH_STACK_DECISION.md §4",
     "`docs/adr/` に以下を作成（設計ドキュメント一式はコミット済み）\n- 0001 モジュラーモノリス（Spring Boot）を採用する\n- 0002 日本語検索に pg_bigm を採用する\n- 0003 制度（Program）を文書から独立したエンティティにする\n- 0004 権限フィルタは検索前（プレフィルタ）で適用する\n- ADR テンプレート（`docs/adr/0000-template.md`）"),
    (3, "build(infra): PostgreSQL 17 + pg_bigm + pgvector の Docker イメージ", "E01", "M", ["infra", "db"], "0", "P1", [1], "E01-T3, docs/DOMAIN_MODEL.md §5",
     "- `infra/docker/postgres/Dockerfile`（`pgvector/pgvector:pg17` ベースに pg_bigm をビルド）\n- 起動時に `CREATE EXTENSION pg_bigm / vector` が成功することを確認\n- 日本語の部分一致（`LIKE '%結婚%'`）が bigm インデックスを使うことを `EXPLAIN` で確認"),
    (4, "build: compose.yaml / Makefile / .env.example", "E01", "S", ["infra"], "0", "P1", [3], "E01-T4, docs/ARCHITECTURE.md §7-8",
     "- compose: postgres / backend / frontend（ai は profile で後日）\n- Makefile: `up` `down` `seed` `test` `eval`\n- `.env.example`（秘密情報は含めない）"),
    (5, "feat(backend): Spring Boot 雛形（Modulith・Flyway・springdoc・ヘルスチェック）", "E01", "M", ["backend"], "0", "P1", [4], "E01-T5, docs/ARCHITECTURE.md §2.3",
     "- Java 21 / Gradle Kotlin DSL / Spring Boot 3\n- パッケージ: shared, organization, content, navigation, insight, audit\n- Spring Modulith のモジュール検証テスト\n- Flyway、springdoc（OpenAPI）、Actuator health、Spotless\n- Testcontainers で PostgreSQL を起動するテスト基盤"),
    (6, "feat(frontend): Next.js 雛形と BFF プロキシ", "E01", "M", ["frontend"], "0", "P1", [4], "E01-T6, docs/TECH_STACK_DECISION.md D-03",
     "- Next.js（App Router, TypeScript）、Tailwind、shadcn/ui、TanStack Query\n- Route Handlers で `/api/*` を backend へプロキシ（Cookie 転送）\n- ESLint / Vitest\n- OpenAPI から型生成（openapi-typescript）の仕組み"),
    (7, "ci: backend / frontend の CI ワークフロー", "E01", "M", ["infra"], "0", "P1", [5, 6], "E01-T7, docs/GITHUB_WORKFLOW.md §8",
     "- `ci-backend.yml`: build / test（Testcontainers）/ Spotless / Modulith 検証\n- `ci-frontend.yml`: lint / type-check / test / build\n- main で動作確認後、Ruleset の必須チェックに追加（`scripts/github/setup-repo.sh`）\n- dependabot に gradle / npm を追加"),
    (8, "feat(seed): 架空商事株式会社の組織・窓口・デモユーザー", "E02", "S", ["backend"], "0", "P1", [], "E02-T1, docs/PRD_KNOWLEDGE_NAVIGATOR.md §9",
     "- `data/seed/departments.yaml`（8部署・階層）\n- `data/seed/contacts.yaml`（15窓口、各部署に代表窓口1つ）\n- `data/seed/users.yaml`（EMPLOYEE / MANAGER / CONTENT_EDITOR(人事・総務) / SYSTEM_ADMIN / 新入社員）\n- 連絡先は example.com・架空内線のみ"),
    (9, "feat(seed): 架空規程10本（Markdown・条文構造）", "E02", "L", ["backend"], "0", "P1", [], "E02-T2, docs/PRD_KNOWLEDGE_NAVIGATOR.md §9",
     "- `data/seed/documents/*.md`: 就業規則、福利厚生規程、育児介護休業規程、慶弔見舞金規程、在宅勤務規程、通勤手当規程、旅費規程、情報セキュリティ規程、PC貸与規程、ハラスメント防止規程\n- 各 20〜40 条、「第N条（見出し）」形式\n- 実在企業の規程を転載しない。公的モデル規程を参考にする場合は出典を明記"),
    (10, "feat(db): 初期スキーマ（組織・文書・制度・FAQ・辞書・ログ）", "E05", "M", ["db"], "1", "P1", [5], "E05-T1, docs/DOMAIN_MODEL.md §5",
     "- Flyway `V1__init.sql`（DOMAIN_MODEL.md §5 の DDL をベースに）\n- pg_bigm の GIN インデックス\n- 監査ログ用 DB ロール権限（INSERT/SELECT のみ）"),
    (11, "feat(auth): セッション認証・ロール・/me", "E03", "M", ["auth", "backend"], "1", "P1", [10], "E03-T1, docs/ARCHITECTURE.md §6",
     "- Spring Security フォームログイン + BCrypt、セッション Cookie（HttpOnly/Secure/SameSite=Lax）\n- CSRF トークン\n- `POST /auth/login` `/auth/logout`、`GET /me`\n- デモユーザーでログインできる"),
    (12, "feat(content): 文書・版・Markdown セクション分割", "E05", "L", ["backend"], "1", "P1", [10], "E05-T2〜T4, docs/PRD_KNOWLEDGE_NAVIGATOR.md F-101〜F-103",
     "- 文書 CRUD、版作成、公開/アーカイブ（楽観ロック）\n- Markdown → セクション分割（見出し・「第N条」検出、path・article_no 付与）\n- `search_text` 正規化（NFKC・見出し連結）\n- 公開中の版は文書につき1つ（不変条件のテスト）"),
    (13, "feat(content): 制度・きっかけ・FAQ・キーワードの API とシード投入", "E06", "L", ["backend"], "1", "P1", [12, 8, 9], "E06-T1〜T4, E02-T3〜T4",
     "- programs / program_basis / program_contacts（公開時に窓口1件以上必須）\n- life_events / life_event_programs、faqs / faq_links、navigation_keywords\n- 制度30件・きっかけ8件・FAQ50件のシード（`make seed`）\n- 不変条件: 制度の公開範囲 ≦ 根拠文書の公開範囲"),
    (14, "feat(auth): AccessPolicy と検索用権限述語（プレフィルタ）", "E03", "L", ["auth", "backend"], "1", "P1", [11], "E03-T2〜T3, docs/DESIGN_REVIEW.md §4",
     "- `AccessPolicy` / `AccessScope`（ロール・公開範囲・access_rules・所管部署）\n- 検索 SQL に付与する WHERE 述語の生成（ポストフィルタ禁止）\n- 編集権限: CONTENT_EDITOR は所管部署のみ、SYSTEM_ADMIN はコンテンツ編集不可"),
    (15, "feat(search): GET /navigate（正規化・辞書・pg_bigm・窓口導出）", "E07", "L", ["search", "backend"], "1", "P1", [13, 14], "E07-T1〜T7, docs/ARCHITECTURE.md §3.3, §4",
     "- クエリ正規化（NFKC・口語ストップワード）\n- 辞書最長一致 → きっかけ/制度ヒット\n- pg_bigm 全文検索（programs / sections / faqs）+ 権限述語\n- スコア統合（重みは設定化）、`SearchPort` 抽象\n- 結果組立: 制度→根拠→文書、窓口導出ルール（必ず1件以上）\n- レスポンスは ARCHITECTURE.md §3.3 の形式"),
    (16, "feat(frontend): ホーム・検索結果画面（4ブロック表示）", "E08", "L", ["frontend"], "1", "P1", [15], "E08-T1〜T3, docs/PRD_KNOWLEDGE_NAVIGATOR.md §6.2",
     "- ログイン画面\n- ホーム: 検索窓・きっかけ・人気の質問・カテゴリ\n- 検索結果: 制度 / 文書・該当箇所 / FAQ / 問い合わせ先、ハイライト、フィードバック\n- ゼロヒット時の総合窓口表示"),
    (17, "feat(frontend): 文書詳細・制度詳細画面", "E08", "L", ["frontend"], "1", "P2", [12, 13], "E08-T4〜T5",
     "- 文書詳細: 目次・条文アンカー・版情報・旧版リンク・関係する制度と窓口\n- 制度詳細: 対象者・金額/日数・申請方法・期限・根拠条文・窓口・関連FAQ"),
    (18, "test(auth): ロール×公開範囲の権限マトリクステスト", "E03", "M", ["auth"], "1", "P1", [14, 15], "E03-T4, docs/DESIGN_REVIEW.md §4",
     "- ロール × 公開範囲 × 操作 × 経路（検索 / 閲覧 / API）のパラメタライズドテスト\n- 件数・スニペットから権限外の存在が漏れないこと"),
    (19, "feat(eval): 評価用質問50問と評価バッチ・CI 連携", "E10", "M", ["search"], "1", "P1", [15], "E02-T5, E10-T2〜T3, docs/ARCHITECTURE.md §4.4",
     "- `data/eval/questions.yaml`（50問、期待する制度/文書/窓口）\n- Hit@1 / Hit@5 / MRR / 窓口正答率 / ゼロヒット率の算出\n- `search-eval.yml`: PR に結果をコメント"),
    (20, "feat(insight): 検索ログ・クリック・フィードバック", "E10", "M", ["search", "backend"], "1", "P2", [15], "E10-T1",
     "- search_logs / search_clicks / feedbacks（ユーザー ID は HMAC で仮名化）\n- `POST /search-logs/{id}/clicks`、`/feedback`"),
]

task_issues = {}
for no, title, eid, size, areas, phase, prio, deps, refs, work in TASKS:
    dep_text = "\n".join(f"- #{task_issues[d]['number']}" for d in deps) or "なし"
    body = (
        f"### 親 Story / Epic\n\n#{epic_issues[eid]['number']}\n\n"
        f"### 作業内容\n\n{work}\n\n"
        f"### 完了条件（Definition of Done）\n\n{DOD}\n\n"
        f"### 見積り\n\n{size}\n\n"
        f"### 依存\n\n{dep_text}\n\n"
        f"### 参考\n\n{refs}（初期 Issue 上位20件の {no} 番）\n"
    )
    labels = ["type:task", f"phase:{phase}", prio] + [f"area:{a}" for a in areas]
    task_issues[no] = create(title, body, labels, 1)

# ---------- Sub-issues ----------
for no, title, eid, *_ in TASKS:
    parent = epic_issues[eid]["number"]
    child = task_issues[no]
    try:
        gh("POST", f"issues/{parent}/sub_issues", {"sub_issue_id": child["id"]})
        print(f"linked #{child['number']} -> #{parent}")
    except RuntimeError as e:
        msg = str(e)
        if "already" in msg.lower() or "duplicate" in msg.lower():
            print(f"already linked #{child['number']}")
        else:
            print(f"link failed #{child['number']} -> #{parent}: {msg[:200]}")

json.dump({"epics": {k: v["number"] for k, v in epic_issues.items()},
           "tasks": {k: v["number"] for k, v in task_issues.items()}},
          open(sys.argv[2] if len(sys.argv) > 2 else "/dev/stdout", "w"), ensure_ascii=False, indent=1)
