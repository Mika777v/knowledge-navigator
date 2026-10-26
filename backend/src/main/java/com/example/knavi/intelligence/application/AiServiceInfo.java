package com.example.knavi.intelligence.application;

/**
 * ai-service の今の Embedding の設定（{@code GET /v1/info}。ADR-0008）。
 *
 * @param external true なら文章が社外に出る（送ってよい公開範囲を絞る）
 */
public record AiServiceInfo(String provider, String model, int dims, boolean external) {}
