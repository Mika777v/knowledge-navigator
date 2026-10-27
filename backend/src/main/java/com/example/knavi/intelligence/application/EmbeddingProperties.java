package com.example.knavi.intelligence.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ベクトルの差分合わせ（ADR-0012）。
 *
 * @param syncInterval 合わせる間隔
 * @param batchSize 1回に ai-service へ送る文章の数（ai-service の上限 64 以下）
 */
@ConfigurationProperties("knavi.embedding")
public record EmbeddingProperties(Duration syncInterval, int batchSize) {}
