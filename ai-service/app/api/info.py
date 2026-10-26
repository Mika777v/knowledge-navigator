from importlib.metadata import version

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()


class ServiceInfo(BaseModel):
    service: str
    version: str


@router.get("/info")
def info() -> ServiceInfo:
    """サービスの情報。プロバイダ・モデル・次元は E13-T2 で足す（ADR-0008）"""
    return ServiceInfo(service="knavi-ai-service", version=_version())


def _version() -> str:
    try:
        return version("knavi-ai-service")
    except Exception:  # noqa: BLE001 - パッケージとして入れずに動かしたとき
        return "0.1.0"
