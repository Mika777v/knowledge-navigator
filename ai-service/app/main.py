"""社内規程ナビの ai-service。backend から呼ばれ、文章を受け取って計算した結果を返す（ADR-0006）"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI

from app.api import health, info
from app.auth import require_token
from app.config import get_settings


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    # 設定（AI_SERVICE_TOKEN など）が足りなければ、起動の時点で止める
    get_settings()
    yield


def create_app() -> FastAPI:
    # 内部の API なので、仕様の画面（/docs）は出さない
    app = FastAPI(title="knavi-ai-service", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)
    app.include_router(health.router)
    # /v1 はすべて backend のトークンが必要
    app.include_router(info.router, prefix="/v1", dependencies=[Depends(require_token)])
    return app


app = create_app()
