package com.example.knavi.navigation.application;

import java.util.List;
import java.util.UUID;

/**
 * 根拠付きの回答文（SSE の answer。ADR-0013）。根拠は検索で選んだ条を DB の値で描く（ADR-0009）。
 *
 * @param answer 回答文。文末の [n] は {@link AssistantEvidence#ref} を指す
 * @param evidence 回答文が引用した根拠（引用された順）
 * @param model 回答文を作ったモデル
 */
public record AssistantAnswer(String answer, List<AssistantEvidence> evidence, String model) {

    /** @param pageFrom 原本（PDF）でのページ（原本のある版だけ。それ以外は null） */
    public record AssistantEvidence(
            int ref,
            UUID documentId,
            String documentTitle,
            String versionLabel,
            UUID sectionId,
            String articleNo,
            String heading,
            Integer pageFrom,
            Integer pageTo) {}
}
