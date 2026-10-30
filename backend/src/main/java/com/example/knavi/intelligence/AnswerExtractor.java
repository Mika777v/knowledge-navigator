package com.example.knavi.intelligence;

import java.util.List;
import java.util.Map;

/**
 * 利用者が書いた文から、確認質問の答えを読み取る（ADR-0014 の決定 2）。ほかのモジュール（assistant）から使う入口。
 *
 * <p>読み取った答えは、画面の選択肢を選んだ状態にするだけで、判定は呼ぶ側が DB の条件で行う。ai-service に繋がらない・
 * 失敗したときは、例外ではなく空を返す（利用者はクリックで答えられるため）。
 */
public interface AnswerExtractor {

    /** @return 質問のコード → 選択肢の value（読み取れた質問だけ。決まった選択肢にあるかは呼ぶ側でも確かめる） */
    Map<String, String> extract(String text, List<Question> questions);

    record Question(String code, String text, List<Choice> choices) {}

    record Choice(String value, String label) {}
}
