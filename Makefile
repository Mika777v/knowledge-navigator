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
up: .env ## 全サービスをビルドして起動する（起動完了まで待つ）
	$(COMPOSE) up -d --build --wait

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
seed: ## ダミーデータを投入する（#33 で実装）
	@echo "未実装です（#33 で追加予定）" >&2; exit 1

.PHONY: test
test: ## 全テストを実行する（#25 / #26 で実装）
	@echo "未実装です（#25 / #26 で追加予定）" >&2; exit 1

.PHONY: eval
eval: ## 検索品質の評価を実行する（#39 で実装）
	@echo "未実装です（#39 で追加予定）" >&2; exit 1
