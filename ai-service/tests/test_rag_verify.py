import pytest

from app.rag.verify import normalize, verify

SOURCES = {
    1: "慶弔見舞金規程 第5条（結婚祝金）\n"
    "社員が婚姻したときは、結婚祝金として30,000円を支給する。申請は、入籍日から6か月以内に行う。",
    2: "就業規則 第32条（特別休暇）\n| 本人が結婚するとき（結婚休暇） | 連続5日 | 入籍日から1年以内 |",
}


def test_a_faithful_answer_passes_and_keeps_the_citation_order() -> None:
    result = verify("結婚祝金は30,000円です。[1] 結婚休暇は連続5日取れます。[2]", SOURCES)
    assert result.ok
    assert result.citations == [1, 2]


@pytest.mark.parametrize(
    "answer",
    [
        "結婚祝金は３０，０００円です。［１］",  # 全角
        "申請は入籍日から6ヶ月以内です。[1]",  # ヶ月
        "申請は入籍日から6カ月以内です。[1]",
        "結婚休暇は5日です[2]。",  # 引用が文末の記号の前
        "第5条によると、結婚祝金は30,000円です。[1]",  # 条番号は文書名・条にある
        "結婚祝金は 30,000 円です [1]。申請は入籍日から 6 か月以内です [1]。",  # 数字と単位の間の空白
    ],
)
def test_notation_differences_are_tolerated(answer: str) -> None:
    assert verify(answer, SOURCES).ok


@pytest.mark.parametrize(
    ("answer", "check"),
    [
        ("結婚祝金は3万円です。[1]", "NUMBER_NOT_IN_SOURCE"),  # 原文の書き方で写していない
        ("結婚祝金は30,000円です。[2]", "NUMBER_NOT_IN_SOURCE"),  # 数値は引用した条にない
        ("結婚休暇は連続5年です。[2]", "NUMBER_NOT_IN_SOURCE"),  # 単位がちがう
        ("結婚祝金は30,000円です。[9]", "UNKNOWN_CITATION"),
        ("結婚祝金は30,000円です。", "NO_CITATION"),
        ("結婚祝金は30,000円です。[1] 総務部（内線 1234）へ。[1]", "CONTACT_IN_ANSWER"),
        ("結婚祝金は30,000円です。[1] 03-1234-5678 へ。[1]", "CONTACT_IN_ANSWER"),
    ],
)
def test_unfaithful_answers_are_rejected(answer: str, check: str) -> None:
    result = verify(answer, SOURCES)
    assert not result.ok
    assert result.failed_check == check


def test_a_short_lead_without_citation_is_allowed() -> None:
    assert verify("お答えします。\n結婚祝金は30,000円です。[1]", SOURCES).ok


def test_normalize_unifies_width_commas_and_month_notation() -> None:
    assert normalize("３０，０００円・6ヶ月・12,345,678") == "30000円・6か月・12345678"


def test_normalize_removes_spaces_between_numbers_and_units_but_keeps_citations() -> None:
    assert normalize("28 日間・6 時間・1 か月以内・10 [1]") == "28日間・6時間・1か月以内・10 [1]"


def test_a_spaced_wrong_unit_is_still_rejected() -> None:
    assert verify("結婚休暇は連続 5 年です [2]。", SOURCES).failed_check == "NUMBER_NOT_IN_SOURCE"


def test_unsupported_sentences_are_dropped_and_the_rest_is_kept_as_written() -> None:
    answer = (
        "結婚祝金は30,000円です。[1] 申請は社内ポータルから行い、承認を受けてください。"
        "結婚休暇は連続7日です。[2] 結婚休暇は連続5日です。[2]"
    )
    result = verify(answer, SOURCES)
    assert result.ok
    assert result.answer == "結婚祝金は30,000円です[1]。結婚休暇は連続5日です[2]。"
    assert result.citations == [1, 2]
    assert result.dropped == ["UNCITED_SENTENCE", "NUMBER_NOT_IN_SOURCE"]


def test_nothing_left_after_dropping_is_rejected_with_the_first_reason() -> None:
    result = verify("結婚祝金は35,000円です。[1] 詳しくは社内ポータルの申請画面を確認してください。", SOURCES)
    assert not result.ok
    assert result.failed_check == "NUMBER_NOT_IN_SOURCE"
    assert result.answer is None


def test_a_short_lead_alone_is_not_an_answer() -> None:
    assert verify("お答えします。", SOURCES).failed_check == "NO_CITATION"


# 引用がなくてもよい短い文（15 文字未満）にならない長さにする
LONG = "社員が婚姻したときの結婚祝金は30,000円です。"


@pytest.mark.parametrize(
    "answer",
    [
        LONG + "これは第5条に定められています。[1]",  # 引用は段落の最後の文だけ
        "申請は入籍日から1年以内です[2]。" + LONG + "申請は6か月以内に行います[1]。",  # 前後の引用
        LONG + "\n[1]",  # 引用だけの行
    ],
)
def test_an_uncited_sentence_is_kept_when_its_numbers_are_in_a_neighboring_citation(answer: str) -> None:
    result = verify(answer, SOURCES)
    assert result.ok
    assert "30,000円" in result.answer
    assert result.dropped == []


@pytest.mark.parametrize(
    "answer",
    [
        LONG.replace("30,000", "35,000") + "これは第5条に定められています。[1]",  # 数値が根拠にない
        LONG + "結婚休暇は連続5日です。[2]",  # 30,000円は後ろの引用（[2]）にない
        LONG + "\n結婚休暇は連続5日です。[2]",  # 段落がちがう
        "申請は社内ポータルから行い、承認を受けてください。" + LONG + "[1]",  # 数値のない文
    ],
)
def test_an_uncited_sentence_without_supported_numbers_is_still_dropped(answer: str) -> None:
    assert "UNCITED_SENTENCE" in verify(answer, SOURCES).dropped
