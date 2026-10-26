"""設定。環境変数から読む（compose の ai プロファイル・.env）"""

from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # backend だけが知っている共有のトークン。未設定なら起動しない（ADR-0006）
    ai_service_token: SecretStr


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]  # 必須の値は環境変数から入る
