package com.example.knavi.intelligence;

import java.util.Optional;

/**
 * 検索の質問をベクトルにする（ADR-0006・0007）。ほかのモジュール（navigation）から使う入口。
 *
 * <p>ai-service に繋がらない・遅い・失敗したときは空を返す。呼ぶ側はキーワード検索だけで続ける（Graceful Degradation）。
 */
public interface QueryEmbedder {

    Optional<QueryEmbedding> embed(String question);

    /**
     * @param model ベクトルを作ったモデル。embeddings.model がこれと同じ行だけを比べる（ADR-0007 の決定 6）
     */
    record QueryEmbedding(String model, float[] vector) {

        /** pgvector の文字列表現（[0.1,0.2,...]） */
        public String literal() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < vector.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(vector[i]);
            }
            return sb.append(']').toString();
        }
    }
}
