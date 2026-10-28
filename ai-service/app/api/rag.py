import logging
import re
import unicodedata
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field

from app.config import Settings, get_settings
from app.llm.base import ChatError, ChatModel
from app.llm.deps import get_chat_model
from app.rag.context import Passage, build_context
from app.rag.prompt import NO_ANSWER, build_messages

router = APIRouter()
log = logging.getLogger(__name__)

_CITATION = re.compile(r"\[(\d+)\]")


class PassageIn(BaseModel):
    ref: int = Field(ge=1)
    title: str = Field(min_length=1, max_length=200)
    text: str = Field(min_length=1, max_length=8000)


class AnswerRequest(BaseModel):
    question: str
    # 検索の順位の順。backend が権限（と外部に送ってよい公開範囲）で絞り込んだ後の条（ADR-0006・0009）
    passages: list[PassageIn]


class AnswerResponse(BaseModel):
    answerable: bool
    # ANSWERED: 回答できた / NO_EVIDENCE: 根拠がなく LLM を呼ばなかった / NOT_IN_CONTEXT: LLM が「記載がない」と答えた
    reason: Literal["ANSWERED", "NO_EVIDENCE", "NOT_IN_CONTEXT"]
    answer: str | None
    # 回答文で引用した根拠の番号（出てきた順、重複なし）
    citations: list[int]
    model: str


@router.post("/rag/answer")
async def answer(
    request: AnswerRequest,
    chat: Annotated[ChatModel, Depends(get_chat_model)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> AnswerResponse:
    """根拠付きの回答文を、最後まで作ってから返す（ADR-0013）。引用・数値の照合は E14-T3 で足す"""
    question = request.question.strip()
    if not question or len(question) > settings.max_question_length:
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_CONTENT, f"question must be 1 to {settings.max_question_length} characters"
        )
    if len(request.passages) > settings.max_passages:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, f"passages must be at most {settings.max_passages}")
    if len({p.ref for p in request.passages}) != len(request.passages):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, "passage refs must be unique")
    if not request.passages:
        return AnswerResponse(answerable=False, reason="NO_EVIDENCE", answer=None, citations=[], model=chat.model)

    context = build_context(
        [Passage(p.ref, p.title, p.text) for p in request.passages],
        settings.max_context_chars,
        settings.max_passage_chars,
    )
    try:
        text = await chat.complete(build_messages(question, context.text))
    except ChatError as e:
        # 質問・文脈の中身はログに出さない（社内文書のため）
        log.warning("chat failed: %s", e)
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "chat provider failed") from e

    if NO_ANSWER.rstrip("。") in text:
        return AnswerResponse(answerable=False, reason="NOT_IN_CONTEXT", answer=None, citations=[], model=chat.model)
    return AnswerResponse(answerable=True, reason="ANSWERED", answer=text, citations=_citations(text), model=chat.model)


def _citations(text: str) -> list[int]:
    # 全角の［１］なども [1] とみなす
    refs = [int(n) for n in _CITATION.findall(unicodedata.normalize("NFKC", text))]
    return list(dict.fromkeys(refs))
