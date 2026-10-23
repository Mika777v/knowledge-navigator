package com.example.knavi.ingestion.application;

import java.util.Optional;

/** 原本ファイルの置き場所（S3 互換ストレージ）。保存キーで読み書きする */
public interface FileStorage {

    void put(String key, byte[] content, String contentType);

    /** 無ければ空 */
    Optional<byte[]> get(String key);
}
