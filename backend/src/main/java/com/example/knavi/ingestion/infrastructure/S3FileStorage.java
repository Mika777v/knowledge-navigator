package com.example.knavi.ingestion.infrastructure;

import com.example.knavi.ingestion.application.FileStorage;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** S3 互換ストレージ（ローカル・CI は SeaweedFS、本番は S3 など）に原本を置く。ADR-0010 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

    private final StorageProperties properties;
    private final S3Client s3;

    S3FileStorage(StorageProperties properties) {
        this.properties = properties;
        // 接続は最初の読み書きのときに行う（ストレージを使わない処理・テストは起動を待たない）
        S3ClientBuilder builder = S3Client.builder();
        // 接続先が空なら AWS の S3 そのもの（リージョンから決まる）。SeaweedFS などはその URL を指定する
        if (hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        this.s3 = builder.region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .forcePathStyle(properties.pathStyle())
                // SDK 2.30 以降の既定（チェックサムを毎回付ける新しい形式）は、S3 互換の製品が受け付けないことがある。
                // 必要な API のときだけ付ける従来の動きにする
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    /**
     * アクセスキーが空なら、AWS の標準の順番で資格情報を探す（EC2 の IAM ロールなど。デモ環境はアクセスキーを作らない。ADR-0015）。
     * ローカル・CI の SeaweedFS では、設定したアクセスキーを使う。
     */
    static AwsCredentialsProvider credentialsProvider(StorageProperties properties) {
        if (!hasText(properties.accessKey())) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @EventListener(ApplicationReadyEvent.class)
    void createBucketIfConfigured() {
        if (!properties.createBucket()) {
            return;
        }
        try {
            s3.headBucket(b -> b.bucket(properties.bucket()));
        } catch (NoSuchBucketException e) {
            createBucket();
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            putObject(key, content, contentType);
        } catch (NoSuchBucketException e) {
            // 起動の完了前（make seed のデータ投入中など）は、まだバケットを作っていないことがある
            if (!properties.createBucket()) {
                throw e;
            }
            createBucket();
            putObject(key, content, contentType);
        }
    }

    private void putObject(String key, byte[] content, String contentType) {
        s3.putObject(
                b -> b.bucket(properties.bucket()).key(key).contentType(contentType), RequestBody.fromBytes(content));
    }

    private void createBucket() {
        s3.createBucket(b -> b.bucket(properties.bucket()));
        log.info("バケット {} を作りました", properties.bucket());
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(
                    s3.getObjectAsBytes(b -> b.bucket(properties.bucket()).key(key))
                            .asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}
