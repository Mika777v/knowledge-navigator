package com.example.knavi.content.api;

import com.example.knavi.content.domain.ContentStatus;
import com.example.knavi.content.domain.Document;
import com.example.knavi.content.domain.DocumentType;
import com.example.knavi.content.domain.DocumentVersion;
import com.example.knavi.content.domain.Section;
import com.example.knavi.organization.DepartmentDirectory.DepartmentRef;
import com.example.knavi.shared.security.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 管理画面向け文書 API の入出力 */
final class DocumentDtos {

    private DocumentDtos() {}

    record CreateDocumentRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z0-9-]+", message = "英大文字・数字・ハイフンで入力してください")
            String code,

            @NotBlank @Size(max = 200) String title,
            String summary,
            @NotNull DocumentType documentType,
            @NotBlank String categorySlug,
            @NotBlank String ownerDepartmentCode,
            @NotNull Visibility visibility,
            LocalDate reviewDueOn) {}

    /** @param tags 付けるタグの名前（省略すると変えない。空の配列ですべて外す） */
    record UpdateDocumentRequest(
            @NotBlank @Size(max = 200) String title,
            String summary,
            @NotNull DocumentType documentType,
            @NotBlank String categorySlug,
            @NotNull Visibility visibility,
            LocalDate reviewDueOn,
            @Size(max = 10) List<String> tags) {}

    record CreateVersionRequest(
            @NotBlank @Size(max = 20) String versionLabel,
            @NotNull LocalDate effectiveFrom,
            @NotBlank String bodyMarkdown,
            String changeNote) {}

    record UpdateVersionRequest(
            @NotNull LocalDate effectiveFrom, @NotBlank String bodyMarkdown, String changeNote) {}

    record PublishRequest(@NotNull UUID versionId) {}

    record CategoryResponse(String slug, String name) {}

    record DepartmentResponse(String code, String name) {
        static DepartmentResponse of(DepartmentRef ref) {
            return ref == null ? null : new DepartmentResponse(ref.code(), ref.name());
        }
    }

    record DocumentResponse(
            UUID id,
            String code,
            String title,
            String summary,
            DocumentType documentType,
            CategoryResponse category,
            DepartmentResponse ownerDepartment,
            Visibility visibility,
            ContentStatus status,
            UUID currentVersionId,
            LocalDate reviewDueOn,
            List<String> tags,
            int rowVersion) {

        static DocumentResponse of(Document d, DepartmentRef owner, List<String> tags) {
            return new DocumentResponse(
                    d.getId(),
                    d.getCode(),
                    d.getTitle(),
                    d.getSummary(),
                    d.getDocumentType(),
                    new CategoryResponse(
                            d.getCategory().getSlug(), d.getCategory().getName()),
                    DepartmentResponse.of(owner),
                    d.getVisibility(),
                    d.getStatus(),
                    d.getCurrentVersionId(),
                    d.getReviewDueOn(),
                    tags,
                    d.getRowVersion());
        }
    }

    record VersionSummaryResponse(
            UUID id,
            String versionLabel,
            LocalDate effectiveFrom,
            String changeNote,
            Instant publishedAt,
            boolean current,
            int sectionCount,
            UUID sourceFileId) {

        static VersionSummaryResponse of(DocumentVersion v, UUID currentVersionId) {
            return new VersionSummaryResponse(
                    v.getId(),
                    v.getVersionLabel(),
                    v.getEffectiveFrom(),
                    v.getChangeNote(),
                    v.getPublishedAt(),
                    v.getId().equals(currentVersionId),
                    v.getSections().size(),
                    v.getSourceFileId());
        }
    }

    /** @param pageFrom 原本（PDF）でのページ（PDF から取り込んだ版だけ） */
    record SectionResponse(
            UUID id,
            int ordinal,
            String path,
            String articleNo,
            String heading,
            String content,
            Integer pageFrom,
            Integer pageTo) {
        static SectionResponse of(Section s) {
            return new SectionResponse(
                    s.getId(),
                    s.getOrdinal(),
                    s.getPath(),
                    s.getArticleNo(),
                    s.getHeading(),
                    s.getContent(),
                    s.getPageFrom(),
                    s.getPageTo());
        }
    }

    record VersionResponse(
            UUID id,
            String versionLabel,
            LocalDate effectiveFrom,
            String changeNote,
            Instant publishedAt,
            String bodyMarkdown,
            List<SectionResponse> sections,
            UUID sourceFileId) {

        static VersionResponse of(DocumentVersion v) {
            return new VersionResponse(
                    v.getId(),
                    v.getVersionLabel(),
                    v.getEffectiveFrom(),
                    v.getChangeNote(),
                    v.getPublishedAt(),
                    v.getBodyMarkdown(),
                    v.getSections().stream().map(SectionResponse::of).toList(),
                    v.getSourceFileId());
        }
    }
}
