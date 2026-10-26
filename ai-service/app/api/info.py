from importlib.metadata import version
from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.llm.base import EmbeddingModel
from app.llm.deps import get_embedding_model

router = APIRouter()


class EmbeddingInfo(BaseModel):
    provider: str
    model: str
    dims: int
    # true なら文章が社外に出る。backend は送る文章を公開範囲で絞る（ADR-0008）
    external: bool


class ServiceInfo(BaseModel):
    service: str
    version: str
    embedding: EmbeddingInfo


@router.get("/info")
def info(embedding: Annotated[EmbeddingModel, Depends(get_embedding_model)]) -> ServiceInfo:
    """今のプロバイダ・モデル・次元。backend は保存・検索するベクトルのモデル名をここから決める（ADR-0007）"""
    return ServiceInfo(
        service="knavi-ai-service",
        version=_version(),
        embedding=EmbeddingInfo(
            provider=embedding.provider, model=embedding.model, dims=embedding.dims, external=embedding.external
        ),
    )


def _version() -> str:
    try:
        return version("knavi-ai-service")
    except Exception:  # noqa: BLE001 - パッケージとして入れずに動かしたとき
        return "0.1.0"
