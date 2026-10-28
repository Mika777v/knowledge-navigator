"""ローカルの Ollama（既定）。文章を社外に送らない（ADR-0008）"""

import httpx2

from app.llm.base import ChatError, EmbeddingError, Message, check_vectors


class OllamaEmbedding:
    provider = "ollama"
    external = False

    def __init__(self, client: httpx2.AsyncClient, model: str, dims: int) -> None:
        self._client = client
        self.model = model
        self.dims = dims

    async def embed(self, texts: list[str]) -> list[list[float]]:
        try:
            response = await self._client.post("/api/embed", json={"model": self.model, "input": texts})
            response.raise_for_status()
            vectors = response.json()["embeddings"]
        except (httpx2.HTTPError, KeyError, ValueError) as e:
            raise EmbeddingError(f"ollama: {type(e).__name__}: {e}") from e
        return check_vectors(texts, vectors, self.dims)


class OllamaChat:
    provider = "ollama"
    external = False

    def __init__(self, client: httpx2.AsyncClient, model: str) -> None:
        self._client = client
        self.model = model

    async def complete(self, messages: list[Message]) -> str:
        body = {
            "model": self.model,
            "messages": messages,
            "stream": False,
            # 考える過程（thinking）は切る。速くするためと、回答に混ざらないようにするため（ADR-0013）
            "think": False,
            "options": {"temperature": 0},
        }
        try:
            response = await self._client.post("/api/chat", json=body)
            response.raise_for_status()
            content = response.json()["message"]["content"]
        except (httpx2.HTTPError, KeyError, TypeError, ValueError) as e:
            raise ChatError(f"ollama: {type(e).__name__}: {e}") from e
        if not isinstance(content, str) or not content.strip():
            raise ChatError("ollama: empty answer")
        return content.strip()
