"""社内規程ナビの ai-service。backend から呼ばれ、文章を受け取って計算した結果を返す（ADR-0006）"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI

from app.api import embeddings, health, info
from app.auth import require_token
from app.config import get_settings
from app.llm.factory import create_embedding_model


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    # 設定（AI_SERVICE_TOKEN・プロバイダ）が足りなければ、起動の時点で止める
    settings = get_settings()
    # テストで差し替えたものがあれば、それを使う
    if not hasattr(app.state, "embedding_model"):
        app.state.embedding_model = create_embedding_model(settings)
    yield


def create_app() -> FastAPI:
    # 内部の API なので、仕様の画面（/docs）は出さない
    app = FastAPI(title="knavi-ai-service", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)
    app.include_router(health.router)
    # /v1 はすべて backend のトークンが必要
    for router in (info.router, embeddings.router):
        app.include_router(router, prefix="/v1", dependencies=[Depends(require_token)])
    return app


app = create_app()
