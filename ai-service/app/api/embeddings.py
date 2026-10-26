import logging
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel

from app.config import Settings, get_settings
from app.llm.base import EmbeddingError, EmbeddingModel
from app.llm.deps import get_embedding_model

router = APIRouter()
log = logging.getLogger(__name__)


class EmbeddingRequest(BaseModel):
    texts: list[str]


class EmbeddingResponse(BaseModel):
    model: str
    dims: int
    embeddings: list[list[float]]


@router.post("/embeddings")
async def embeddings(
    request: EmbeddingRequest,
    embedding: Annotated[EmbeddingModel, Depends(get_embedding_model)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> EmbeddingResponse:
    """文章をベクトルにする。受け取る文章は、backend が権限で絞り込んだ後のもの（ADR-0006）"""
    texts = request.texts
    if not texts or len(texts) > settings.max_texts_per_request:
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_CONTENT, f"texts must have 1 to {settings.max_texts_per_request} items"
        )
    if any(not text.strip() or len(text) > settings.max_text_length for text in texts):
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_CONTENT, f"each text must be 1 to {settings.max_text_length} characters"
        )
    try:
        vectors = await embedding.embed(texts)
    except EmbeddingError as e:
        # 文章の中身はログに出さない（社内文書のため）
        log.warning("embedding failed: %s", e)
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "embedding provider failed") from e
    return EmbeddingResponse(model=embedding.model, dims=embedding.dims, embeddings=vectors)
