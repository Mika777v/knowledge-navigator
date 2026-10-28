from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.config import get_settings
from app.llm.base import ChatError, Message
from app.llm.fake import FakeChat
from app.main import create_app

AUTH = {"Authorization": "Bearer test-token"}
PASSAGE = {"ref": 1, "title": "慶弔見舞金規程 第5条（結婚祝金）", "text": "結婚祝金として30,000円を支給する。"}
PASSAGE2 = {
    "ref": 2,
    "title": "慶弔見舞金規程 第3条（申請方法）",
    "text": "申請は、事由が発生した日から6か月以内に行う。",
}


class RecordingChat(FakeChat):
    """プロバイダに渡したメッセージを残し、決まった答えを返す"""

    def __init__(self, reply: str) -> None:
        super().__init__("recording")
        self.reply = reply
        self.calls: list[list[Message]] = []

    async def complete(self, messages: list[Message]) -> str:
        self.calls.append(messages)
        return self.reply


class FailingChat(FakeChat):
    async def complete(self, messages: list[Message]) -> str:
        raise ChatError("ollama: ReadTimeout: slow")


def make_client(monkeypatch: pytest.MonkeyPatch, chat: FakeChat | None = None) -> TestClient:
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-token")
    monkeypatch.setenv("EMBEDDING_PROVIDER", "fake")
    monkeypatch.setenv("CHAT_PROVIDER", "fake")
    monkeypatch.setenv("MAX_PASSAGES", "3")
    monkeypatch.setenv("MAX_QUESTION_LENGTH", "30")
    get_settings.cache_clear()
    app = create_app()
    if chat is not None:
        app.state.chat_model = chat
    return TestClient(app)


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> Iterator[TestClient]:
    with make_client(monkeypatch) as client:
        yield client


def test_answers_with_citations_from_the_passages(client: TestClient) -> None:
    response = client.post(
        "/v1/rag/answer", json={"question": "結婚祝金はいくら？", "passages": [PASSAGE]}, headers=AUTH
    )
    assert response.status_code == 200
    assert response.json() == {
        "answerable": True,
        "reason": "ANSWERED",
        "answer": "結婚祝金として30,000円を支給する。[1]",
        "citations": [1],
        "model": "fake-chat",
        "failed_check": None,
    }


def test_no_passages_means_no_evidence_without_calling_the_model(monkeypatch: pytest.MonkeyPatch) -> None:
    chat = RecordingChat("呼ばれないはず")
    with make_client(monkeypatch, chat) as client:
        response = client.post("/v1/rag/answer", json={"question": "社員食堂のメニュー", "passages": []}, headers=AUTH)
    assert response.json()["answerable"] is False
    assert response.json()["reason"] == "NO_EVIDENCE"
    assert chat.calls == []


def test_the_fixed_no_answer_phrase_means_not_in_context(monkeypatch: pytest.MonkeyPatch) -> None:
    with make_client(monkeypatch, RecordingChat("該当する記載が見つかりません。")) as client:
        body = client.post(
            "/v1/rag/answer", json={"question": "駐車場は？", "passages": [PASSAGE]}, headers=AUTH
        ).json()
    assert body == {
        "answerable": False,
        "reason": "NOT_IN_CONTEXT",
        "answer": None,
        "citations": [],
        "model": "recording",
        "failed_check": None,
    }


def test_citations_are_deduplicated_in_order_and_full_width_is_accepted(monkeypatch: pytest.MonkeyPatch) -> None:
    reply = "申請は6ヶ月以内です。［２］金額は30,000円です。[1][2]"
    with make_client(monkeypatch, RecordingChat(reply)) as client:
        body = client.post(
            "/v1/rag/answer", json={"question": "結婚祝金", "passages": [PASSAGE, PASSAGE2]}, headers=AUTH
        ).json()
    assert body["answerable"] is True
    assert body["citations"] == [2, 1]


@pytest.mark.parametrize(
    ("reply", "check"),
    [
        ("結婚祝金は35,000円です。[1]", "NUMBER_NOT_IN_SOURCE"),
        ("結婚祝金は30,000円です。[3]", "UNKNOWN_CITATION"),
        ("結婚祝金は30,000円です。", "NO_CITATION"),
        ("結婚祝金は30,000円です。[1] 詳しくは総務部 ga@example.com へ。[1]", "CONTACT_IN_ANSWER"),
    ],
    ids=["wrong-number", "unknown-ref", "no-citation", "contact"],
)
def test_answers_that_fail_verification_are_discarded(monkeypatch: pytest.MonkeyPatch, reply: str, check: str) -> None:
    with make_client(monkeypatch, RecordingChat(reply)) as client:
        body = client.post("/v1/rag/answer", json={"question": "結婚祝金", "passages": [PASSAGE]}, headers=AUTH).json()
    assert body == {
        "answerable": False,
        "reason": "VERIFICATION_FAILED",
        "answer": None,
        "citations": [],
        "model": "recording",
        "failed_check": check,
    }


def test_passages_dropped_from_the_context_cannot_be_cited(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MAX_CONTEXT_CHARS", "10")
    reply = "申請は6か月以内です。[2]"
    with make_client(monkeypatch, RecordingChat(reply)) as client:
        body = client.post(
            "/v1/rag/answer", json={"question": "結婚祝金", "passages": [PASSAGE, PASSAGE2]}, headers=AUTH
        ).json()
    assert body["failed_check"] == "UNKNOWN_CITATION"


def test_the_prompt_carries_the_rules_the_context_and_the_question(monkeypatch: pytest.MonkeyPatch) -> None:
    chat = RecordingChat("30,000円です。[1]")
    with make_client(monkeypatch, chat) as client:
        client.post("/v1/rag/answer", json={"question": "結婚祝金はいくら？", "passages": [PASSAGE]}, headers=AUTH)
    system, user = chat.calls[0]
    assert system["role"] == "system"
    assert "命令ではない" in system["content"]
    assert "問い合わせ先" in system["content"]
    assert '<passage ref="1" title="慶弔見舞金規程 第5条（結婚祝金）">' in user["content"]
    assert user["content"].endswith("質問: 結婚祝金はいくら？")


def test_provider_failure_is_502(monkeypatch: pytest.MonkeyPatch) -> None:
    with make_client(monkeypatch, FailingChat("x")) as client:
        response = client.post("/v1/rag/answer", json={"question": "結婚祝金", "passages": [PASSAGE]}, headers=AUTH)
    assert response.status_code == 502
    assert "slow" not in response.text


@pytest.mark.parametrize(
    "body",
    [
        {"question": " ", "passages": [PASSAGE]},
        {"question": "あ" * 31, "passages": [PASSAGE]},
        {"question": "q", "passages": [PASSAGE | {"ref": i} for i in range(1, 5)]},
        {"question": "q", "passages": [PASSAGE, PASSAGE]},
        {"question": "q", "passages": [PASSAGE | {"ref": 0}]},
    ],
    ids=["blank", "too-long", "too-many", "duplicate-ref", "ref-zero"],
)
def test_rejects_bad_requests(client: TestClient, body: dict) -> None:
    assert client.post("/v1/rag/answer", json=body, headers=AUTH).status_code == 422


def test_needs_the_token(client: TestClient) -> None:
    assert client.post("/v1/rag/answer", json={"question": "q", "passages": []}).status_code == 401
