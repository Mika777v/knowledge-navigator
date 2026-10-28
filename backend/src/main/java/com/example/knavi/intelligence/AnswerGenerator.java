package com.example.knavi.intelligence;

import java.util.List;
import java.util.Set;

/**
 * 根拠付きの回答文を作る（ADR-0009・0013）。ほかのモジュール（navigation）から使う入口。
 *
 * <p>渡す条は、呼ぶ側が権限で絞り込み、{@link #sendableVisibilities()} の公開範囲だけにしたもの。窓口・根拠の表示は呼ぶ側が
 * DB の値で描き、ここで返す文字列は回答文と引用の番号だけにする。ai-service に繋がらない・失敗したときは、例外ではなく
 * 「回答できない」を返す（4ブロックの結果は呼ぶ側で出せているため）。
 */
public interface AnswerGenerator {

    /** 生成のプロバイダに送ってよい公開範囲（ADR-0008・E14-T7）。ai-service に繋がらなければ空 */
    Set<String> sendableVisibilities();

    GeneratedAnswer generate(String question, List<Passage> passages);

    /**
     * @param ref 根拠の番号（1〜）。回答文の [n] と、画面の根拠をつなぐ
     * @param title 文書名と条（例: 慶弔見舞金規程 第5条（結婚祝金））
     */
    record Passage(int ref, String title, String text) {}

    /**
     * @param reason 回答できたときは ANSWERED。できないときの理由（NO_EVIDENCE / NOT_IN_CONTEXT / GENERATION_FAILED など）
     * @param citations 回答文で引用した根拠の番号
     */
    record GeneratedAnswer(boolean answerable, String reason, String answer, List<Integer> citations, String model) {

        public static GeneratedAnswer unanswerable(String reason) {
            return new GeneratedAnswer(false, reason, null, List.of(), null);
        }
    }
}
