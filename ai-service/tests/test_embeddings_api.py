from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.config import get_settings
from app.llm.base import EmbeddingError
from app.llm.fake import FakeEmbedding
from app.main import create_app

AUTH = {"Authorization": "Bearer test-token"}


class FailingEmbedding(FakeEmbedding):
    async def embed(self, texts: list[str]) -> list[list[float]]:
        raise EmbeddingError("ollama: ConnectError: refused")


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> Iterator[TestClient]:
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-token")
    monkeypatch.setenv("EMBEDDING_PROVIDER", "fake")
    monkeypatch.setenv("MAX_TEXTS_PER_REQUEST", "3")
    monkeypatch.setenv("MAX_TEXT_LENGTH", "20")
    get_settings.cache_clear()
    with TestClient(create_app()) as client:
        yield client


def test_returns_one_vector_per_text_in_order(client: TestClient) -> None:
    response = client.post("/v1/embeddings", json={"texts": ["育児休業", "結婚祝金"]}, headers=AUTH)
    assert response.status_code == 200
    body = response.json()
    assert body["model"] == "bge-m3"
    assert body["dims"] == 1024
    assert len(body["embeddings"]) == 2
    again = client.post("/v1/embeddings", json={"texts": ["結婚祝金"]}, headers=AUTH).json()
    assert again["embeddings"][0] == body["embeddings"][1]


def test_requires_the_token(client: TestClient) -> None:
    assert client.post("/v1/embeddings", json={"texts": ["a"]}).status_code == 401


@pytest.mark.parametrize(
    "texts",
    [[], ["a", "b", "c", "d"], ["x" * 21], ["   "]],
    ids=["empty", "too-many", "too-long", "blank"],
)
def test_rejects_requests_out_of_limits(client: TestClient, texts: list[str]) -> None:
    assert client.post("/v1/embeddings", json={"texts": texts}, headers=AUTH).status_code == 422


def test_provider_failure_is_502_without_echoing_the_text(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-token")
    get_settings.cache_clear()
    app = create_app()
    app.state.embedding_model = FailingEmbedding("bge-m3", 1024)
    with TestClient(app) as client:
        response = client.post("/v1/embeddings", json={"texts": ["社外秘の本文"]}, headers=AUTH)
    assert response.status_code == 502
    assert "社外秘" not in response.text
