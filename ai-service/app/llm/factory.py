"""設定（EMBEDDING_PROVIDER）からプロバイダを作る"""

import httpx2
import openai
from pydantic import SecretStr

from app.config import Settings
from app.llm.base import EmbeddingModel
from app.llm.fake import FakeEmbedding
from app.llm.ollama import OllamaEmbedding
from app.llm.openai import OpenAIEmbedding


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


def _secret(value: SecretStr | None) -> str | None:
    return value.get_secret_value() if value else None
