package com.example.knavi.ingestion.domain;

/** PDF から文字を取り出せない（暗号化・画像だけ・壊れているなど）。メッセージは利用者に見せる理由 */
public class PdfExtractionException extends RuntimeException {

    public PdfExtractionException(String message) {
        super(message);
    }

    public PdfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
