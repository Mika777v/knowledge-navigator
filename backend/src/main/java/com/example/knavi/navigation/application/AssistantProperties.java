package com.example.knavi.navigation.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 根拠付き回答（E14-T4・ADR-0013）の設定。
 *
 * @param enabled false なら回答文を作らず、4ブロックの結果だけを返す（ai-service を動かしていないとき）
 * @param maxPassages 回答の文脈に入れる条の数の上限（検索の上位から）
 */
@ConfigurationProperties("knavi.assistant")
public record AssistantProperties(boolean enabled, int maxPassages) {}
