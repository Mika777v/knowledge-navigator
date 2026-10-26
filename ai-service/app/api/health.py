from fastapi import APIRouter

router = APIRouter()


@router.get("/healthz")
def healthz() -> dict[str, str]:
    """compose のヘルスチェック用。トークンは要らない（中身を返さない）"""
    return {"status": "UP"}
