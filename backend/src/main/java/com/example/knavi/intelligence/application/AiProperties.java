package com.example.knavi.intelligence.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai-service への接続（ADR-0006）と、社外に送るプロバイダへ送ってよい公開範囲（ADR-0008）。
 *
 * @param externalVisibilities 社外に送るプロバイダのとき、ベクトルにしてよい公開範囲（CONFIDENTIAL は指定しても送らない）
 */
@ConfigurationProperties("knavi.ai")
public record AiProperties(
        String baseUrl,
        String token,
        Duration connectTimeout,
        Duration readTimeout,
        List<String> externalVisibilities) {}
