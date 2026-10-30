package com.example.knavi.ingestion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

class S3FileStorageCredentialsTest {

    @Test
    void アクセスキーを設定したらその値で接続する() {
        var provider = S3FileStorage.credentialsProvider(properties("knavi", "secret"));

        assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
        assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("knavi");
    }

    @Test
    void アクセスキーが空ならAWSの標準の順番で資格情報を探す() {
        assertThat(S3FileStorage.credentialsProvider(properties("", "")))
                .isInstanceOf(DefaultCredentialsProvider.class);
        assertThat(S3FileStorage.credentialsProvider(properties(null, null)))
                .isInstanceOf(DefaultCredentialsProvider.class);
    }

    private static StorageProperties properties(String accessKey, String secretKey) {
        return new StorageProperties("", "ap-northeast-1", "knavi-files", accessKey, secretKey, false, false);
    }
}
