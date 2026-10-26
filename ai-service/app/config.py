"""設定。環境変数から読む（compose の ai プロファイル・.env）"""

from functools import lru_cache
from typing import Literal

from pydantic import SecretStr, model_validator
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # backend だけが知っている共有のトークン。未設定なら起動しない（ADR-0006）
    ai_service_token: SecretStr

    # Embedding のプロバイダ（ADR-0008）。既定はローカルの Ollama
    embedding_provider: Literal["ollama", "openai", "azure_openai", "fake"] = "ollama"
    embedding_model: str = "bge-m3"
    # ベクトルの次元。DB の vector(1024) と同じにする（ADR-0007）
    embedding_dims: int = 1024
    provider_timeout_seconds: float = 30.0
    # 1回の要求で受け付ける文章の数と長さ（backend のジョブはこれ以下に分けて送る）
    max_texts_per_request: int = 64
    max_text_length: int = 8000

    ollama_base_url: str = "http://ollama:11434"
    openai_api_key: SecretStr | None = None
    azure_openai_api_key: SecretStr | None = None
    azure_openai_endpoint: str | None = None
    azure_openai_api_version: str = "2024-10-21"

    @model_validator(mode="after")
    def _require_provider_settings(self) -> "Settings":
        # 足りない設定は、最初の呼び出しではなく起動の時点で知らせる
        if self.embedding_provider == "openai" and not self.openai_api_key:
            raise ValueError("OPENAI_API_KEY is required for EMBEDDING_PROVIDER=openai")
        if self.embedding_provider == "azure_openai" and not (self.azure_openai_api_key and self.azure_openai_endpoint):
            raise ValueError(
                "AZURE_OPENAI_API_KEY and AZURE_OPENAI_ENDPOINT are required for EMBEDDING_PROVIDER=azure_openai"
            )
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]  # 必須の値は環境変数から入る
