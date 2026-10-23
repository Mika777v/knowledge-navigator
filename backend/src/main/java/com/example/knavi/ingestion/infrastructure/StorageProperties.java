package com.example.knavi.ingestion.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 原本ファイルの置き場所（S3 互換ストレージ）の設定。
 *
 * @param pathStyle true なら http://host/bucket/key の形で呼ぶ（SeaweedFS・MinIO など）
 * @param createBucket true なら起動時にバケットが無ければ作る（ローカル・CI 用）
 */
@ConfigurationProperties("knavi.storage")
record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        boolean createBucket) {}
