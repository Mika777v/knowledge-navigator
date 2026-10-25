package com.example.knavi.ingestion.application;

import com.example.knavi.ingestion.domain.PdfExtractionException;
import com.example.knavi.ingestion.domain.PdfText;

/** PDF の中身から、ページごとの行を取り出す */
public interface PdfTextReader {

    /** @throws PdfExtractionException 暗号化されている・壊れているなどで読めないとき */
    PdfText read(byte[] pdf);
}
