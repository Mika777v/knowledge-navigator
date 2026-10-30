package com.example.knavi.assistant.application;

import com.example.knavi.assistant.domain.Eligibility.Status;
import java.util.List;
import java.util.UUID;

/** 案内（きっかけ → 確認質問 → 制度の一覧 → チェックリスト）の応答（ADR-0014 の決定 1）。値はすべて DB から */
public final class GuideViews {

    private GuideViews() {}

    /**
     * @param questions このきっかけで、見られる制度の条件に出てくる質問だけ
     * @param programs 見られる制度（PRIMARY → RELATED の順。対象外も理由つきで出す）
     * @param checklist 対象・要確認の制度の手続き（programs と同じ順）
     */
    public record GuideResult(
            GuideLifeEvent lifeEvent,
            List<GuideQuestion> questions,
            List<GuideProgram> programs,
            List<GuideChecklistItem> checklist) {}

    public record GuideLifeEvent(String code, String name, String description) {}

    /**
     * @param answer 判定に使った答え（未回答なら null）
     * @param answerSource USER = 送られた答え / PROFILE = 利用者の属性から選んだ / NONE = 未回答
     */
    public record GuideQuestion(
            String code, String text, List<GuideChoice> choices, String answer, String answerSource) {}

    public record GuideChoice(String value, String label) {}

    /**
     * @param relation PRIMARY = まず確認する制度 / RELATED = あわせて必要になりやすい手続き
     * @param eligibility 対象者（文章のまま。判定は目安で、最後は窓口が決める）
     * @param unmetQuestions 外れた条件の質問のコード（対象外の理由）
     * @param unansweredQuestions 答えていない条件の質問のコード（要確認の理由）
     */
    public record GuideProgram(
            UUID id,
            String code,
            String name,
            String summary,
            String relation,
            String eligibility,
            Status status,
            List<String> unmetQuestions,
            List<String> unansweredQuestions,
            List<GuideBasis> basis,
            List<GuideContact> contacts) {}

    /** 根拠の条（見られる文書だけ）。条を指定しない根拠は sectionId・articleNo が null */
    public record GuideBasis(UUID documentId, String documentTitle, UUID sectionId, String articleNo, String heading) {}

    /** @param role APPLY = 申請先 / INQUIRY = 問い合わせ先 / PAYMENT = 支給担当 */
    public record GuideContact(
            String code,
            String name,
            String department,
            String email,
            String phoneExt,
            String chatUrl,
            String officeHours,
            String role) {}

    /**
     * 手続きの ToDo。期限は文章のまま（日付は計算しない。ADR-0014）。
     *
     * @param requiredDocuments 必要書類（無ければ null）
     * @param deadlineNote 期限（無ければ null）
     * @param howToApply 申請の方法（無ければ null）
     */
    public record GuideChecklistItem(
            UUID programId,
            String programName,
            Status status,
            String requiredDocuments,
            String deadlineNote,
            String howToApply) {}
}
