package com.example.knavi.intelligence.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 長い本文を、前後を少し重ねて区切る（docs/ARCHITECTURE.md §5.2: 600〜800 字、前後 100 字の重なり）。
 * どの部分にも見出しを先頭に付け、区切った後でも何の文章か分かるようにする。
 */
public final class TextChunker {

    /** 1つの部分の本文の長さ */
    static final int SIZE = 700;
    /** 隣の部分と重ねる長さ */
    static final int OVERLAP = 100;

    private TextChunker() {}

    public static List<String> chunks(String header, String body) {
        String text = body == null ? "" : body.strip();
        String prefix = header == null || header.isBlank() ? "" : header.strip() + "\n";
        if (text.length() <= SIZE) {
            return List.of(prefix + text);
        }
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += SIZE - OVERLAP) {
            int end = Math.min(start + SIZE, text.length());
            chunks.add(prefix + text.substring(start, end));
            if (end == text.length()) {
                break;
            }
        }
        return chunks;
    }
}
