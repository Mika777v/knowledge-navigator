"""OpenAI・Azure OpenAI。文章が社外に出る（ADR-0008。送ってよい公開範囲は backend が決める）"""

import openai

from app.llm.base import ChatError, EmbeddingError, Message, check_vectors


class OpenAIEmbedding:
    external = True

    def __init__(self, client: openai.AsyncOpenAI, provider: str, model: str, dims: int) -> None:
        self._client = client
        self.provider = provider
        self.model = model
        self.dims = dims

    async def embed(self, texts: list[str]) -> list[list[float]]:
        try:
            # text-embedding-3 系は dimensions で次元を指定できる。ADR-0007 の 1024 にそろえる
            response = await self._client.embeddings.create(model=self.model, input=texts, dimensions=self.dims)
        except openai.OpenAIError as e:
            raise EmbeddingError(f"{self.provider}: {type(e).__name__}: {e}") from e
        vectors = [item.embedding for item in sorted(response.data, key=lambda item: item.index)]
        return check_vectors(texts, vectors, self.dims)


class OpenAIChat:
    external = True

    def __init__(self, client: openai.AsyncOpenAI, provider: str, model: str) -> None:
        self._client = client
        self.provider = provider
        self.model = model

    async def complete(self, messages: list[Message]) -> str:
        try:
            response = await self._client.chat.completions.create(model=self.model, messages=messages, temperature=0)
        except openai.OpenAIError as e:
            raise ChatError(f"{self.provider}: {type(e).__name__}: {e}") from e
        content = response.choices[0].message.content if response.choices else None
        if not content or not content.strip():
            raise ChatError(f"{self.provider}: empty answer")
        return content.strip()
