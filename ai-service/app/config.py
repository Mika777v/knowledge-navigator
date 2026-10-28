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

    # 回答文の生成のプロバイダ（ADR-0008・0013）。既定はローカルの Ollama の qwen3.5:4b
    chat_provider: Literal["ollama", "openai", "azure_openai", "fake"] = "ollama"
    # openai・azure_openai では必須（クラウドのモデル名は入れ替わりが速いため、既定を持たない。Azure はデプロイ名）
    chat_model: str | None = None
    # backend は 120 秒で打ち切る（ADR-0013）。それより先にこちらで止めて 502 を返す
    chat_timeout_seconds: float = 110.0
    # 回答の文脈（E14-T1）。文字数で上限を決める（日本語では 1 文字がおおよそ 1 トークン）
    max_question_length: int = 500
    max_passages: int = 20
    max_context_chars: int = 6000
    max_passage_chars: int = 1500

    ollama_base_url: str = "http://ollama:11434"
    openai_api_key: SecretStr | None = None
    azure_openai_api_key: SecretStr | None = None
    azure_openai_endpoint: str | None = None
    azure_openai_api_version: str = "2024-10-21"

    @model_validator(mode="after")
    def _require_provider_settings(self) -> "Settings":
        # 足りない設定は、最初の呼び出しではなく起動の時点で知らせる
        for name, provider in (("EMBEDDING_PROVIDER", self.embedding_provider), ("CHAT_PROVIDER", self.chat_provider)):
            if provider == "openai" and not self.openai_api_key:
                raise ValueError(f"OPENAI_API_KEY is required for {name}=openai")
            if provider == "azure_openai" and not (self.azure_openai_api_key and self.azure_openai_endpoint):
                raise ValueError(f"AZURE_OPENAI_API_KEY and AZURE_OPENAI_ENDPOINT are required for {name}=azure_openai")
        # compose は未設定を空文字で渡すため、空文字も未設定として扱う
        if not self.chat_model:
            match self.chat_provider:
                case "ollama":
                    self.chat_model = "qwen3.5:4b"
                case "fake":
                    self.chat_model = "fake-chat"
                case _:
                    raise ValueError(f"CHAT_MODEL is required for CHAT_PROVIDER={self.chat_provider}")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]  # 必須の値は環境変数から入る
