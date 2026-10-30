package com.example.knavi.assistant.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 会話の履歴の API の応答（ADR-0014 の決定 3）。OpenAPI のスキーマ名が他のモジュールと重ならない名前にする */
public final class ConversationViews {

    private ConversationViews() {}

    /** @param lastMessageAt 最後に話した日時（保持期間はここから数える） */
    public record ConversationSummary(UUID id, String title, Instant lastMessageAt) {}

    /** @param expiresAt この日時を過ぎると消える（このまま話さなかった場合） */
    public record ConversationDetail(
            UUID id, String title, Instant lastMessageAt, Instant expiresAt, List<ConversationMessage> messages) {}

    /**
     * @param role USER / ASSISTANT
     * @param content 質問、または回答文（回答文を出せなかったときは空）
     * @param answerable ASSISTANT のときだけ。回答文を出せたか
     * @param unanswerableReason 回答文を出せなかった理由（AssistantUnanswerable の reason）
     * @param evidence 今も見られる根拠（[n] の n の順）
     * @param hiddenEvidenceCount 見られなくなった・消えた根拠の数（中身は出さない）
     */
    public record ConversationMessage(
            String role,
            String content,
            Boolean answerable,
            String unanswerableReason,
            String model,
            Instant createdAt,
            List<ConversationEvidence> evidence,
            int hiddenEvidenceCount) {}

    /**
     * 根拠の条。開いた時点の権限で読み直した DB の値（ADR-0009）。
     *
     * @param versionLabel 引用したときの版
     * @param current 引用した版が今の版か（改定されていれば false）
     */
    public record ConversationEvidence(
            int ref,
            UUID documentId,
            String documentTitle,
            String versionLabel,
            boolean current,
            UUID sectionId,
            String articleNo,
            String heading,
            Integer pageFrom,
            Integer pageTo) {}
}
