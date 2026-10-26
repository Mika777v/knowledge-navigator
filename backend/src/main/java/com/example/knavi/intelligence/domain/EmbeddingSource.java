package com.example.knavi.intelligence.domain;

import java.util.UUID;

/**
 * ベクトルにする元の文章（条・FAQ・制度の1件）。
 *
 * @param header 先頭に付ける見出し（文書名 > 条の見出し、FAQ の質問、制度名）。区切ったどの部分にも付ける
 */
public record EmbeddingSource(SourceType type, UUID id, String header, String body) {

    public enum SourceType {
        SECTION,
        FAQ,
        PROGRAM
    }
}
