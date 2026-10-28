package com.example.knavi.intelligence.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai-service への接続（ADR-0006）と、社外に送るプロバイダへ送ってよい公開範囲（ADR-0008）。
 *
 * @param readTimeout ベクトルの合わせ（まとめて送る）の応答を待つ時間
 * @param queryTimeout 検索の質問の応答を待つ時間（過ぎたらキーワード検索だけで返す）
 * @param answerTimeout 回答文の生成を待つ時間（過ぎたら「回答できない」にする。ADR-0013）
 * @param externalVisibilities 社外に送るプロバイダのとき、ベクトルにする・回答の文脈に入れてよい公開範囲
 *     （CONFIDENTIAL は指定しても送らない）
 */
@ConfigurationProperties("knavi.ai")
public record AiProperties(
        String baseUrl,
        String token,
        Duration connectTimeout,
        Duration readTimeout,
        Duration queryTimeout,
        Duration answerTimeout,
        List<String> externalVisibilities) {}
