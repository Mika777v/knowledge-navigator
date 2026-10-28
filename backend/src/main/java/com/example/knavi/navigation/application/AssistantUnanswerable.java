package com.example.knavi.navigation.application;

/**
 * 回答文を出さないとき（SSE の unanswerable。ADR-0013）。窓口は先に送った4ブロックの結果のものを案内する。
 *
 * @param reason 理由。DISABLED（回答の機能がオフ）/ NO_EVIDENCE（根拠の条がない）/ NOT_IN_CONTEXT（条に答えがない）/
 *     GENERATION_FAILED（生成に失敗・時間切れ）/ VERIFICATION_FAILED（引用を確かめられなかった）
 */
public record AssistantUnanswerable(String reason) {}
