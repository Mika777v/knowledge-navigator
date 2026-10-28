from fastapi import Request

from app.llm.base import ChatModel, EmbeddingModel


def get_embedding_model(request: Request) -> EmbeddingModel:
    """起動時に作ったプロバイダ（main.lifespan）を使う"""
    return request.app.state.embedding_model


def get_chat_model(request: Request) -> ChatModel:
    return request.app.state.chat_model
