"""backend からの呼び出しだけを受け付ける（ADR-0006）"""

import hmac
from typing import Annotated

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import Settings, get_settings

_bearer = HTTPBearer(auto_error=False)


def require_token(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> None:
    """Authorization: Bearer <AI_SERVICE_TOKEN> が一致しなければ 401 にする"""
    expected = settings.ai_service_token.get_secret_value().encode()
    given = credentials.credentials.encode() if credentials else b""
    # 比べる時間からトークンを推測されないよう、一定時間で比べる
    if not hmac.compare_digest(given, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid token",
            headers={"WWW-Authenticate": "Bearer"},
        )
