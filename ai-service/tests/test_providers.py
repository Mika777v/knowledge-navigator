import json
import math
from collections.abc import Callable

import httpx2
import openai
import pytest
from pydantic import ValidationError

from app.config import Settings
from app.llm.base import EmbeddingError
from app.llm.fake import FakeEmbedding
from app.llm.ollama import OllamaEmbedding
from app.llm.openai import OpenAIEmbedding


async def test_fake_is_deterministic_normalized_and_offline() -> None:
    model = FakeEmbedding("bge-m3", 1024)
    first, second, other = await model.embed(["育休", "育休", "産休"])
    assert len(first) == 1024
    assert first == second
    assert first != other
    assert math.isclose(sum(v * v for v in first), 1.0)
    assert model.external is False


def ollama_with(handler: Callable[[httpx2.Request], httpx2.Response]) -> OllamaEmbedding:
    client = httpx2.AsyncClient(base_url="http://ollama:11434", transport=httpx2.MockTransport(handler))
    return OllamaEmbedding(client, "bge-m3", 3)


async def test_ollama_sends_the_model_and_texts() -> None:
    seen = {}

    def handler(request: httpx2.Request) -> httpx2.Response:
        seen["path"] = request.url.path
        seen["body"] = json.loads(request.content)
        return httpx2.Response(200, json={"embeddings": [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]})

    vectors = await ollama_with(handler).embed(["a", "b"])
    assert vectors == [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]
    assert seen == {"path": "/api/embed", "body": {"model": "bge-m3", "input": ["a", "b"]}}


@pytest.mark.parametrize(
    "response",
    [
        httpx2.Response(200, json={"embeddings": [[0.1, 0.2]]}),  # 次元が違う
        httpx2.Response(200, json={"embeddings": []}),  # 数が違う
        httpx2.Response(200, json={"error": "model not found"}),  # 形が違う
        httpx2.Response(500, text="boom"),
    ],
    ids=["wrong-dims", "wrong-count", "wrong-shape", "server-error"],
)
async def test_ollama_bad_responses_raise_embedding_error(response: httpx2.Response) -> None:
    with pytest.raises(EmbeddingError):
        await ollama_with(lambda _: response).embed(["a"])


async def test_ollama_connection_error_raises_embedding_error() -> None:
    def handler(request: httpx2.Request) -> httpx2.Response:
        raise httpx2.ConnectError("refused", request=request)

    with pytest.raises(EmbeddingError, match="ollama"):
        await ollama_with(handler).embed(["a"])


async def test_openai_asks_for_the_dimensions_and_keeps_the_order() -> None:
    seen = {}

    def handler(request: httpx2.Request) -> httpx2.Response:
        seen["body"] = json.loads(request.content)
        data = [
            {"object": "embedding", "index": 1, "embedding": [0.4, 0.5, 0.6]},
            {"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]},
        ]
        usage = {"prompt_tokens": 2, "total_tokens": 2}
        return httpx2.Response(200, json={"object": "list", "data": data, "model": "m", "usage": usage})

    client = openai.AsyncOpenAI(
        api_key="sk-test", max_retries=0, http_client=httpx2.AsyncClient(transport=httpx2.MockTransport(handler))
    )
    model = OpenAIEmbedding(client, "openai", "text-embedding-3-small", 3)
    assert await model.embed(["a", "b"]) == [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]
    assert seen["body"]["dimensions"] == 3
    assert seen["body"]["model"] == "text-embedding-3-small"
    assert model.external is True


async def test_openai_errors_raise_embedding_error() -> None:
    client = openai.AsyncOpenAI(
        api_key="sk-test",
        max_retries=0,
        http_client=httpx2.AsyncClient(transport=httpx2.MockTransport(lambda _: httpx2.Response(401, json={}))),
    )
    with pytest.raises(EmbeddingError, match="openai"):
        await OpenAIEmbedding(client, "openai", "text-embedding-3-small", 3).embed(["a"])


@pytest.mark.parametrize(
    "env",
    [{"EMBEDDING_PROVIDER": "openai"}, {"EMBEDDING_PROVIDER": "azure_openai", "AZURE_OPENAI_API_KEY": "k"}],
    ids=["openai-without-key", "azure-without-endpoint"],
)
def test_settings_require_provider_credentials(monkeypatch: pytest.MonkeyPatch, env: dict[str, str]) -> None:
    monkeypatch.setenv("AI_SERVICE_TOKEN", "t")
    for key, value in env.items():
        monkeypatch.setenv(key, value)
    with pytest.raises(ValidationError):
        Settings()  # type: ignore[call-arg]
