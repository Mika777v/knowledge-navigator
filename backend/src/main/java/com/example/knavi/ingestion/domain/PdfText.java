package com.example.knavi.ingestion.domain;

import java.util.List;

/**
 * PDF から取り出した文字（ページごとの行）。
 *
 * @param title PDF の文書情報のタイトル（無ければ null）
 * @param pages ページごとの行（上から順。空行は含めない）
 */
public record PdfText(String title, List<List<String>> pages) {

    public PdfText {
        pages = pages.stream().map(List::copyOf).toList();
    }
}
