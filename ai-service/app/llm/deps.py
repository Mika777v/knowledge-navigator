from fastapi import Request

from app.llm.base import EmbeddingModel


def get_embedding_model(request: Request) -> EmbeddingModel:
    """起動時に作ったプロバイダ（main.lifespan）を使う"""
    return request.app.state.embedding_model
