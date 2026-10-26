package com.example.knavi.intelligence.application;

import java.util.List;

/** ai-service の呼び出し（ADR-0006）。権限で絞り込んだ後の文章だけを渡す */
public interface AiClient {

    /** 今のプロバイダ・モデル・次元 */
    AiServiceInfo info();

    /** 文章ごとのベクトル（入力と同じ順・同じ数） */
    List<float[]> embed(List<String> texts);

    /** ai-service に繋がらない・失敗した */
    class AiServiceException extends RuntimeException {
        public AiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
