"""設定（EMBEDDING_PROVIDER・CHAT_PROVIDER）からプロバイダを作る"""

import httpx2
import openai
from pydantic import SecretStr

from app.config import Settings
from app.llm.base import ChatModel, EmbeddingModel
from app.llm.fake import FakeChat, FakeEmbedding
from app.llm.ollama import OllamaChat, OllamaEmbedding
from app.llm.openai import OpenAIChat, OpenAIEmbedding


def create_embedding_model(settings: Settings) -> EmbeddingModel:
    model, dims, timeout = settings.embedding_model, settings.embedding_dims, settings.provider_timeout_seconds
    match settings.embedding_provider:
        case "fake":
            return FakeEmbedding(model, dims)
        case "ollama":
            client = httpx2.AsyncClient(base_url=settings.ollama_base_url, timeout=timeout)
            return OllamaEmbedding(client, model, dims)
        case "openai":
            client = openai.AsyncOpenAI(api_key=_secret(settings.openai_api_key), timeout=timeout)
            return OpenAIEmbedding(client, "openai", model, dims)
        case "azure_openai":
            # Azure ではデプロイ名をモデル名として渡す
            client = openai.AsyncAzureOpenAI(
                api_key=_secret(settings.azure_openai_api_key),
                azure_endpoint=settings.azure_openai_endpoint,
                api_version=settings.azure_openai_api_version,
                timeout=timeout,
            )
            return OpenAIEmbedding(client, "azure_openai", model, dims)


def create_chat_model(settings: Settings) -> ChatModel:
    # chat_model は設定の検査（Settings）で埋まっている
    model, timeout = str(settings.chat_model), settings.chat_timeout_seconds
    match settings.chat_provider:
        case "fake":
            return FakeChat(model)
        case "ollama":
            client = httpx2.AsyncClient(base_url=settings.ollama_base_url, timeout=timeout)
            return OllamaChat(client, model)
        case "openai":
            client = openai.AsyncOpenAI(api_key=_secret(settings.openai_api_key), timeout=timeout)
            return OpenAIChat(client, "openai", model)
        case "azure_openai":
            client = openai.AsyncAzureOpenAI(
                api_key=_secret(settings.azure_openai_api_key),
                azure_endpoint=settings.azure_openai_endpoint,
                api_version=settings.azure_openai_api_version,
                timeout=timeout,
            )
            return OpenAIChat(client, "azure_openai", model)


def _secret(value: SecretStr | None) -> str | None:
    return value.get_secret_value() if value else None
