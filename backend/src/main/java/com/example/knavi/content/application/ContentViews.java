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
            LocalDate effectiveFrom) {}

    public record SectionView(UUID id, int ordinal, String path, String articleNo, String heading, String content) {}

    public record DocumentDetail(DocumentSummary document, UUID currentVersionId, List<SectionView> sections) {}

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
