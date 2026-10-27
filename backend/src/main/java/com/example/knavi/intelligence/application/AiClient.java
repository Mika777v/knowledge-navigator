package com.example.knavi.intelligence.application;

import java.util.List;

/** ai-service の呼び出し（ADR-0006）。権限で絞り込んだ後の文章だけを渡す */
public interface AiClient {

    /** 今のプロバイダ・モデル・次元 */
    AiServiceInfo info();

    /** 文章ごとのベクトル（入力と同じ順・同じ数）。ベクトルの合わせに使う */
    List<float[]> embed(List<String> texts);

    /** 検索の質問のベクトル。利用者を待たせないよう、短いタイムアウトで呼ぶ */
    float[] embedQuery(String question);

    /** ai-service に繋がらない・失敗した */
    class AiServiceException extends RuntimeException {
        public AiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
