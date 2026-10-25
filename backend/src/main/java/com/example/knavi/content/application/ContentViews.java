package com.example.knavi.content.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 一般利用者向けの読み取りモデル（API のレスポンスとしてそのまま返す） */
public final class ContentViews {

    private ContentViews() {}

    public record CategoryRef(String slug, String name) {}

    public record DepartmentRef(String code, String name) {}

    public record DocumentSummary(
            UUID id,
            String code,
            String title,
            String summary,
            String documentType,
            CategoryRef category,
            DepartmentRef ownerDepartment,
            String versionLabel,
            LocalDate effectiveFrom,
            List<String> tags) {}

    /**
     * @param pageFrom 原本（PDF）でのページの始まり（PDF から取り込んだ版だけ。それ以外は null）
     * @param pageTo 原本（PDF）でのページの終わり
     */
    public record SectionView(
            UUID id,
            int ordinal,
            String path,
            String articleNo,
            String heading,
            String content,
            Integer pageFrom,
            Integer pageTo) {}

    /**
     * 文書の詳細。
     *
     * @param version 表示している版（指定がなければ公開中の版）
     * @param versions 公開済みの版（新しい順）。過去の版も閲覧できる
     * @param programs この文書を根拠にしている制度
     * @param contacts それらの制度の窓口（重複を除く）
     */
    public record DocumentDetail(
            DocumentSummary document,
            UUID currentVersionId,
            VersionView version,
            List<VersionView> versions,
            List<SectionView> sections,
            List<DocumentProgram> programs,
            List<ContactView> contacts) {}

    /** @param hasSourceFile 原本（PDF）がある（PDF から取り込んだ版） */
    public record VersionView(
            UUID id,
            String versionLabel,
            LocalDate effectiveFrom,
            String changeNote,
            boolean current,
            boolean hasSourceFile) {}

    /** @param articleNos 根拠になっている条（文書全体が根拠なら空） */
    public record DocumentProgram(UUID id, String code, String name, String summary, List<String> articleNos) {}

    /** 条文単体（引用リンクの行き先）。過去の版の条文も、文書が見えれば表示する */
    public record SectionDetail(
            SectionView section, DocumentSummary document, String versionLabel, boolean currentVersion) {}

    public record ContactView(
            String code,
            String name,
            String description,
            DepartmentRef department,
            String email,
            String phoneExt,
            String chatUrl,
            String officeHours,
            String role) {}

    public record BasisView(
            UUID documentId,
            String documentCode,
            String documentTitle,
            UUID sectionId,
            String articleNo,
            String heading) {}

    public record ProgramSummary(
            UUID id,
            String code,
            String name,
            String summary,
            String benefit,
            String deadlineNote,
            CategoryRef category) {}

    public record ProgramDetail(
            ProgramSummary program,
            String nameKana,
            String eligibility,
            String howToApply,
            String requiredDocuments,
            DepartmentRef ownerDepartment,
            List<BasisView> basis,
            List<ContactView> contacts,
            List<FaqSummary> faqs,
            List<LifeEventSummary> lifeEvents) {}

    public record FaqSummary(UUID id, String code, String question, CategoryRef category) {}

    public record FaqDetail(
            FaqSummary faq,
            String answerMarkdown,
            ContactView contact,
            List<ProgramSummary> programs,
            List<BasisView> references) {}

    public record LifeEventSummary(String code, String name, String icon, String description) {}

    public record LifeEventProgram(ProgramSummary program, String relation) {}

    public record LifeEventDetail(LifeEventSummary lifeEvent, List<LifeEventProgram> programs) {}

    public record CategoryNode(String slug, String name, List<CategoryNode> children) {}
}
