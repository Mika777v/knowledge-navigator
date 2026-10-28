import json
from collections.abc import Callable

import httpx2
import openai
import pytest
from pydantic import ValidationError

from app.config import Settings
from app.llm.base import ChatError, Message
from app.llm.fake import FakeChat
from app.llm.ollama import OllamaChat
from app.llm.openai import OpenAIChat

MESSAGES: list[Message] = [{"role": "system", "content": "s"}, {"role": "user", "content": "u"}]


def ollama_with(handler: Callable[[httpx2.Request], httpx2.Response]) -> OllamaChat:
    client = httpx2.AsyncClient(base_url="http://ollama:11434", transport=httpx2.MockTransport(handler))
    return OllamaChat(client, "qwen3.5:4b")


async def test_ollama_asks_without_streaming_thinking_or_randomness() -> None:
    seen = {}

    def handler(request: httpx2.Request) -> httpx2.Response:
        seen["path"] = request.url.path
        seen["body"] = json.loads(request.content)
        return httpx2.Response(200, json={"message": {"role": "assistant", "content": " 30,000円です。[1]\n"}})

    assert await ollama_with(handler).complete(MESSAGES) == "30,000円です。[1]"
    assert seen["path"] == "/api/chat"
    assert seen["body"] == {
        "model": "qwen3.5:4b",
        "messages": MESSAGES,
        "stream": False,
        "think": False,
        "options": {"temperature": 0},
    }


@pytest.mark.parametrize(
    "response",
    [
        httpx2.Response(200, json={"message": {"content": "   "}}),
        httpx2.Response(200, json={"error": "model not found"}),
        httpx2.Response(404, json={"error": "model 'qwen3.5:4b' not found"}),
    ],
    ids=["empty", "wrong-shape", "not-pulled"],
)
async def test_ollama_bad_responses_raise_chat_error(response: httpx2.Response) -> None:
    with pytest.raises(ChatError, match="ollama"):
        await ollama_with(lambda _: response).complete(MESSAGES)


async def test_ollama_timeout_raises_chat_error() -> None:
    def handler(request: httpx2.Request) -> httpx2.Response:
        raise httpx2.ReadTimeout("slow", request=request)

    with pytest.raises(ChatError):
        await ollama_with(handler).complete(MESSAGES)


def openai_with(handler: Callable[[httpx2.Request], httpx2.Response]) -> OpenAIChat:
    client = openai.AsyncOpenAI(
        api_key="sk-test", max_retries=0, http_client=httpx2.AsyncClient(transport=httpx2.MockTransport(handler))
    )
    return OpenAIChat(client, "openai", "my-model")


async def test_openai_sends_the_messages_with_temperature_zero() -> None:
    seen = {}

    def handler(request: httpx2.Request) -> httpx2.Response:
        seen["body"] = json.loads(request.content)
        choice = {"index": 0, "finish_reason": "stop", "message": {"role": "assistant", "content": "5日です。[1]"}}
        return httpx2.Response(
            200,
            json={"id": "x", "object": "chat.completion", "created": 0, "model": "my-model", "choices": [choice]},
        )

    model = openai_with(handler)
    assert await model.complete(MESSAGES) == "5日です。[1]"
    assert seen["body"]["model"] == "my-model"
    assert seen["body"]["messages"] == MESSAGES
    assert seen["body"]["temperature"] == 0
    assert model.external is True


async def test_openai_errors_raise_chat_error() -> None:
    with pytest.raises(ChatError, match="openai"):
        await openai_with(lambda _: httpx2.Response(401, json={})).complete(MESSAGES)


async def test_fake_answers_with_the_first_sentence_of_passage_one() -> None:
    passage = "結婚祝金は30,000円とする。申請は6か月以内。"
    user = f'規程の抜粋:\n<passage ref="1" title="慶弔見舞金規程 第5条">\n{passage}\n</passage>'
    model = FakeChat("fake-chat")
    assert await model.complete([{"role": "user", "content": user}]) == "結婚祝金は30,000円とする。[1]"
    assert model.external is False


def settings(monkeypatch: pytest.MonkeyPatch, **env: str) -> Settings:
    monkeypatch.setenv("AI_SERVICE_TOKEN", "t")
    for key, value in env.items():
        monkeypatch.setenv(key, value)
    return Settings()  # type: ignore[call-arg]


def test_chat_model_defaults_to_qwen_on_ollama(monkeypatch: pytest.MonkeyPatch) -> None:
    assert settings(monkeypatch).chat_model == "qwen3.5:4b"


@pytest.mark.parametrize(
    "env",
    [
        {"CHAT_PROVIDER": "openai", "OPENAI_API_KEY": "k"},
        {"CHAT_PROVIDER": "openai", "CHAT_MODEL": "m"},
        {"CHAT_PROVIDER": "azure_openai", "CHAT_MODEL": "m", "AZURE_OPENAI_API_KEY": "k"},
    ],
    ids=["cloud-without-model", "openai-without-key", "azure-without-endpoint"],
)
def test_chat_settings_require_model_and_credentials(monkeypatch: pytest.MonkeyPatch, env: dict[str, str]) -> None:
    with pytest.raises(ValidationError):
        settings(monkeypatch, **env)
