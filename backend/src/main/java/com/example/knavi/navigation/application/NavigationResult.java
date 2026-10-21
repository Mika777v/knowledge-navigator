package com.example.knavi.navigation.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ナビゲーション検索の結果（GET /api/v1/navigate）。制度・文書・FAQ・窓口の4ブロックで返す（docs/ARCHITECTURE.md §3.3）。
 *
 * <p>抜粋（snippet）は HTML を含まないプレーンテキストで返す。強調する語は {@link QueryInfo#terms} を使って画面側で付ける
 * （サーバーで HTML を組み立てると、条文の中身によってはスクリプトの埋め込みにつながるため）。
 *
 * @param fallback 制度・文書・FAQ のどれも見つからず、総合窓口だけを案内している
 */
public record NavigationResult(
        QueryInfo query,
        List<ProgramHit> programs,
        List<DocumentHit> documents,
        List<FaqHit> faqs,
        List<ContactHit> contacts,
        List<RelatedProgram> relatedPrograms,
        boolean fallback) {

    /**
     * @param normalized 口語の言い回しを除いた質問
     * @param terms 検索に使った語（辞書で見つかった語を含む）
     * @param matchedLifeEvents 質問から判断したきっかけ
     */
    public record QueryInfo(String raw, String normalized, List<String> terms, List<LifeEventRef> matchedLifeEvents) {}

    public record LifeEventRef(String code, String name) {}

    /**
     * @param reasons 表示された理由（例: {@code LIFE_EVENT:結婚}, {@code KEYWORD:入籍}, {@code TEXT:休暇}）
     */
    public record ProgramHit(
            UUID id,
            String code,
            String name,
            String summary,
            String benefit,
            String deadlineNote,
            List<BasisRef> basis,
            double score,
            List<String> reasons) {}

    public record BasisRef(
            UUID documentId, String documentTitle, String versionLabel, UUID sectionId, String articleNo) {}

    public record DocumentHit(
            UUID id,
            String code,
            String title,
            String versionLabel,
            LocalDate effectiveFrom,
            List<SectionRef> sections,
            double score) {}

    public record SectionRef(UUID id, String articleNo, String heading, String snippet) {}

    public record FaqHit(UUID id, String code, String question, double score) {}

    /** @param reason この窓口を案内する理由（例: 「結婚祝金の申請窓口」） */
    public record ContactHit(
            String code,
            String name,
            String department,
            String phoneExt,
            String email,
            String chatUrl,
            String officeHours,
            String reason) {}

    public record RelatedProgram(UUID id, String code, String name) {}
}
