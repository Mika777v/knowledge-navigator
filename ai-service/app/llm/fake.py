"""テスト・CI 用。文章のハッシュから決まったベクトルを返し、外部に通信しない（ADR-0008）"""

import hashlib
import math
import re
import struct

from app.llm.base import Message


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


class FakeChat:
    """文脈の1番目の根拠の最初の文に [1] を付けて返す。決まった答えになり、外部に通信しない。

    確認質問の読み取り（app/assistant/extract.py）では、文に選択肢の label がそのまま含まれていれば、その value を選ぶ。
    """

    provider = "fake"
    external = False

    def __init__(self, model: str) -> None:
        self.model = model

    async def complete(self, messages: list[Message]) -> str:
        if "<statement>" in messages[-1]["content"]:
            return self._extract(messages[-1]["content"])
        m = re.search(r'<passage ref="1"[^>]*>\n(.*?)\n</passage>', messages[-1]["content"], re.DOTALL)
        if not m:
            return "該当する記載が見つかりません。"
        first = re.split(r"(?<=。)", m.group(1).strip(), maxsplit=1)[0]
        return f"{first}[1]"

    @staticmethod
    def _extract(content: str) -> str:
        statement = re.search(r"<statement>\n(.*?)\n</statement>", content, re.DOTALL)
        text = statement.group(1) if statement else ""
        lines = []
        for code, body in re.findall(r'<question code="([A-Z0-9_]+)">\n(.*?)\n</question>', content, re.DOTALL):
            # 長い label から照合する（「社員」が「正社員」「契約社員」に含まれるような重なりを避ける）
            choices = sorted(re.findall(r"^- (\S+): (.+)$", body, re.MULTILINE), key=lambda c: -len(c[1]))
            for value, label in choices:
                if label in text:
                    lines.append(f"{code}={value}")
                    break
        return "\n".join(lines)
