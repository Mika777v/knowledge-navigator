"""LLM・Embedding のプロバイダの薄い抽象（ADR-0008）。LangChain などは使わない"""

from typing import Literal, Protocol, TypedDict


class EmbeddingError(Exception):
    """プロバイダの呼び出しに失敗した（接続できない・応答の形が違う・次元が違う）"""


class EmbeddingModel(Protocol):
    # 設定の名前（ollama / openai / azure_openai / fake）
    provider: str
    # 保存するベクトルに付けるモデル名（embeddings.model。ADR-0007）
    model: str
    dims: int
    # 文章を社外に送るか。backend はこれを見て、送ってよい公開範囲を決める（ADR-0008）
    external: bool

    async def embed(self, texts: list[str]) -> list[list[float]]:
        """文章ごとにベクトルを返す（入力と同じ順・同じ数）"""
        ...


def check_vectors(texts: list[str], vectors: list[list[float]], dims: int) -> list[list[float]]:
    """プロバイダの応答が、数と次元の約束を守っているか確かめる"""
    if len(vectors) != len(texts):
        raise EmbeddingError(f"expected {len(texts)} vectors, got {len(vectors)}")
    for vector in vectors:
        if len(vector) != dims:
            raise EmbeddingError(f"expected {dims} dimensions, got {len(vector)}")
    return vectors


class ChatError(Exception):
    """回答文の生成に失敗した（接続できない・時間切れ・応答の形が違う）"""


class Message(TypedDict):
    role: Literal["system", "user"]
    content: str


class ChatModel(Protocol):
    provider: str
    model: str
    # 文章を社外に送るか（EmbeddingModel と同じ。ADR-0008）
    external: bool

    async def complete(self, messages: list[Message]) -> str:
        """回答文を最後まで作って返す。ストリーミングはしない（引用を確かめてから返すため。ADR-0013）"""
        ...
