"""ローカルの Ollama（既定）。文章を社外に送らない（ADR-0008）"""

import httpx2

from app.llm.base import EmbeddingError, check_vectors


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
