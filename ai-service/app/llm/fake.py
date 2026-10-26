"""テスト・CI 用。文章のハッシュから決まったベクトルを返し、外部に通信しない（ADR-0008）"""

import hashlib
import math
import struct


class FakeEmbedding:
    provider = "fake"
    external = False

    def __init__(self, model: str, dims: int) -> None:
        self.model = model
        self.dims = dims

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [self._vector(text) for text in texts]

    def _vector(self, text: str) -> list[float]:
        # SHA-256 を数珠つなぎにして必要な数の値を作り、長さ1にそろえる（コサイン類似度で比べるため）
        values: list[float] = []
        block = hashlib.sha256(text.encode()).digest()
        while len(values) < self.dims:
            values.extend(v / 2**31 - 1.0 for v in struct.unpack("<8I", block))
            block = hashlib.sha256(block).digest()
        values = values[: self.dims]
        norm = math.sqrt(sum(v * v for v in values)) or 1.0
        return [v / norm for v in values]
