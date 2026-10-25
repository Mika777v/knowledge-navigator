package com.example.knavi.ingestion;

/** 文書に原本（PDF）を結び付ける公開 API（ダミーデータの投入用。ADR-0010） */
public interface SourcePdfImport {

    /**
     * PDF を保存し（同じ内容のファイルは使い回す）、解析した条ごとのページを付けて、文書の公開中の版に結び付ける。
     *
     * @return ページを結び付けた条・見出しの数
     */
    int attachToCurrentVersion(String documentCode, String fileName, byte[] pdf);
}
