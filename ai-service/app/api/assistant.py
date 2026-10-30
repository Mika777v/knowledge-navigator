import logging
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field

from app.assistant.extract import Choice, Question, build_messages, parse_answers
from app.llm.base import ChatError, ChatModel
from app.llm.deps import get_chat_model

router = APIRouter()
log = logging.getLogger(__name__)


class ChoiceIn(BaseModel):
    value: str = Field(min_length=1, max_length=40)
    label: str = Field(min_length=1, max_length=100)


class QuestionIn(BaseModel):
    code: str = Field(min_length=1, max_length=40)
    text: str = Field(min_length=1, max_length=200)
    choices: list[ChoiceIn] = Field(min_length=2, max_length=20)


class ExtractRequest(BaseModel):
    # 利用者が書いた文（backend が長さを確かめた後）
    text: str = Field(min_length=1, max_length=500)
    # 答えを選んでほしい質問（backend が、見られる制度の条件に出てくるものだけに絞った後。ADR-0014）
    questions: list[QuestionIn] = Field(min_length=1, max_length=10)


class ExtractResponse(BaseModel):
    # 質問のコード → 選択肢の value。読み取れなかった質問は含めない
    answers: dict[str, str]
    model: str


@router.post("/assistant/extract")
async def extract(request: ExtractRequest, chat: Annotated[ChatModel, Depends(get_chat_model)]) -> ExtractResponse:
    """自由な文から確認質問の答えを読み取る（ADR-0014 の決定 2）。決まった質問・選択肢だけを返す"""
    statement = request.text.strip()
    if not statement:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, "text must not be blank")
    questions = [Question(q.code, q.text, [Choice(c.value, c.label) for c in q.choices]) for q in request.questions]
    try:
        reply = await chat.complete(build_messages(statement, questions))
    except ChatError as e:
        # 利用者の文はログに出さない（個人の事情が書かれやすいため）
        log.warning("chat failed: %s", e)
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "chat provider failed") from e
    return ExtractResponse(answers=parse_answers(reply, questions), model=chat.model)
