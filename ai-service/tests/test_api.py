from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import create_app

TOKEN = "test-token"


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> Iterator[TestClient]:
    monkeypatch.setenv("AI_SERVICE_TOKEN", TOKEN)
    monkeypatch.setenv("EMBEDDING_PROVIDER", "fake")
    get_settings.cache_clear()
    with TestClient(create_app()) as client:
        yield client


def test_healthz_needs_no_token(client: TestClient) -> None:
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_v1_rejects_missing_token(client: TestClient) -> None:
    response = client.get("/v1/info")
    assert response.status_code == 401
    assert response.headers["WWW-Authenticate"] == "Bearer"


def test_v1_rejects_wrong_token(client: TestClient) -> None:
    response = client.get("/v1/info", headers={"Authorization": "Bearer wrong"})
    assert response.status_code == 401


def test_v1_accepts_the_shared_token(client: TestClient) -> None:
    response = client.get("/v1/info", headers={"Authorization": f"Bearer {TOKEN}"})
    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "knavi-ai-service"
    assert body["embedding"] == {"provider": "fake", "model": "bge-m3", "dims": 1024, "external": False}
    # CHAT_PROVIDER の既定はローカルの Ollama（ADR-0013）
    assert body["chat"] == {"provider": "ollama", "model": "qwen3.5:4b", "external": False}


def test_api_docs_are_not_exposed(client: TestClient) -> None:
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404


def test_settings_require_the_token(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("AI_SERVICE_TOKEN", raising=False)
    get_settings.cache_clear()
    with pytest.raises(ValueError, match="ai_service_token"):
        get_settings()


def test_app_does_not_start_without_the_token(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("AI_SERVICE_TOKEN", raising=False)
    get_settings.cache_clear()
    with pytest.raises(ValueError, match="ai_service_token"), TestClient(create_app()):
        pass
