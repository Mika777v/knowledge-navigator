package com.example.knavi.intelligence.application;

/**
 * ai-service の今の回答文の生成の設定（{@code GET /v1/info} の chat。ADR-0013）。
 *
 * @param external true なら回答の文脈（条の本文）が社外に出る（送ってよい公開範囲を絞る）
 */
public record AiChatInfo(String provider, String model, boolean external) {}
