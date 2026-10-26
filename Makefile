# よく使う操作のショートカット。`make help` で一覧を表示する。

SHELL := /bin/bash
COMPOSE := docker compose

.DEFAULT_GOAL := help

.PHONY: help
help: ## コマンド一覧を表示する
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

.env:
	cp .env.example .env
	@echo ".env を .env.example から作成しました"

.PHONY: up
up: .env ## 全サービスをビルドして起動する（起動完了まで待つ。初回は数分かかる）
	$(COMPOSE) up -d --build --wait

.PHONY: up-ai
up-ai: .env ## AI の処理（ai-service）も含めて起動する（ADR-0006。止めるときは make down-ai）
	$(COMPOSE) --profile ai up -d --build --wait

.PHONY: down-ai
down-ai: ## ai-service も含めて停止する（データは残る）
	$(COMPOSE) --profile ai down

.PHONY: test-ai
test-ai: ## ai-service の検査とテストを実行する（uv が必要）
	cd ai-service && uv sync --frozen && uv run ruff check . && uv run ruff format --check . && uv run pytest

.PHONY: down
down: ## 全サービスを停止する（データは残る）
	$(COMPOSE) down

.PHONY: clean
clean: ## 全サービスを停止し、DB のデータも削除する
	$(COMPOSE) down -v

.PHONY: ps
ps: ## サービスの状態を表示する
	$(COMPOSE) ps

.PHONY: logs
logs: ## ログを表示し続ける（Ctrl+C で終了）
	$(COMPOSE) logs -f

.PHONY: psql
psql: .env ## PostgreSQL に psql で接続する
	$(COMPOSE) exec postgres sh -c 'psql -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"'

.PHONY: db-smoke
db-smoke: ## PostgreSQL イメージのスモークテストを実行する
	infra/docker/postgres/test/run-smoke.sh

.PHONY: seed
seed: .env ## ダミーデータ（data/seed）を投入する。何度実行してもよい（同じコードのデータは上書き）
	$(COMPOSE) run --rm --no-TTY -v "$(CURDIR)/data/seed:/seed:ro" -v "$(CURDIR)/data/pdf:/pdf:ro" backend \
	  --spring.main.web-application-type=none --knavi.seed.enabled=true --knavi.seed.path=/seed \
	  --knavi.seed.pdf-path=/pdf

.PHONY: test
test: .env ## テストを実行する（backend・frontend）
	$(COMPOSE) build postgres
	cd backend && ./gradlew test
	cd frontend && pnpm install --frozen-lockfile && pnpm test

.PHONY: pdfs
pdfs: ## ダミー規程の PDF（data/pdf）を作り直す（reportlab と IPA ゴシックが必要）
	python3 scripts/data/generate_pdfs.py

.PHONY: eval
eval: .env ## 検索品質の評価を実行する（事前に make up・make seed。PyYAML が必要）
	@port=$$(sed -n 's/^BACKEND_PORT=//p' .env); \
	python3 scripts/eval/run_eval.py --base-url http://localhost:$${port:-8080}

.PHONY: eval-paraphrase
eval-paraphrase: .env ## 言い換えの質問で検索品質を測る（意味検索の効果を見る。失敗にはしない）
	@port=$$(sed -n 's/^BACKEND_PORT=//p' .env); \
	python3 scripts/eval/run_eval.py --base-url http://localhost:$${port:-8080} \
	  --questions data/eval/paraphrase.yaml --baseline data/eval/baseline-paraphrase.json \
	  --output build/eval-paraphrase --min-hit5 0 $(ARGS)

.PHONY: eval-tune
eval-tune: .env ## 検索スコアの重みをグリッド探索する（事前に make up・make seed。jar を起動し直すため数十分かかる）
	cd backend && ./gradlew bootJar
	@set -a; . ./.env; set +a; \
	DB_URL=jdbc:postgresql://localhost:$${POSTGRES_PORT:-5432}/$${POSTGRES_DB} \
	KNAVI_SUDACHI_DICTIONARY=$$(ls $$HOME/.gradle/caches/knavi-sudachi/*.dic 2>/dev/null | head -1) \
	python3 scripts/eval/tune_weights.py --jar backend/build/libs/knowledge-navigator-backend-0.0.1-SNAPSHOT.jar $(ARGS)
