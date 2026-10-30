package com.example.knavi.intelligence.application;

import java.util.List;
import java.util.Map;

/** ai-service の呼び出し（ADR-0006）。権限で絞り込んだ後の文章だけを渡す */
public interface AiClient {

    /** 今のプロバイダ・モデル・次元 */
    AiServiceInfo info();

    /** 文章ごとのベクトル（入力と同じ順・同じ数）。ベクトルの合わせに使う */
    List<float[]> embed(List<String> texts);

    /** 検索の質問のベクトル。利用者を待たせないよう、短いタイムアウトで呼ぶ */
    float[] embedQuery(String question);

    /** 今の回答文の生成のプロバイダ・モデル（{@code GET /v1/info} の chat） */
    AiChatInfo chatInfo();

    /** 根拠付きの回答文（{@code POST /v1/rag/answer}）。生成を待つため、長いタイムアウトで呼ぶ */
    RagAnswer answer(String question, List<RagPassage> passages);

    /**
     * 利用者が書いた文から、確認質問の答えを読み取る（{@code POST /v1/assistant/extract}。ADR-0014 の決定 2）。
     *
     * @return 質問のコード → 選択肢の value（読み取れた質問だけ）
     */
    Map<String, String> extract(String text, List<ExtractQuestion> questions);

    record ExtractQuestion(String code, String text, List<ExtractChoice> choices) {}

    record ExtractChoice(String value, String label) {}

    /** @param ref 根拠の番号（1〜） */
    record RagPassage(int ref, String title, String text) {}

    /**
     * @param reason ANSWERED / NO_EVIDENCE / NOT_IN_CONTEXT（E14-T3 からは照合に通らなかった理由も）
     * @param citations 回答文で引用した根拠の番号
     */
    record RagAnswer(boolean answerable, String reason, String answer, List<Integer> citations, String model) {}

    /** ai-service に繋がらない・失敗した */
    class AiServiceException extends RuntimeException {
        public AiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
