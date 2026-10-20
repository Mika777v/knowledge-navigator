package com.example.knavi.content.application;

import com.example.knavi.content.domain.DocumentType;
import com.example.knavi.shared.security.Visibility;
import java.time.LocalDate;
import java.util.UUID;

/** 文書の操作に渡す値 */
public final class DocumentCommands {

    private DocumentCommands() {}

    public record Create(
            String code,
            String title,
            String summary,
            DocumentType documentType,
            String categorySlug,
            String ownerDepartmentCode,
            Visibility visibility,
            LocalDate reviewDueOn) {}

    public record Update(
            String title,
            String summary,
            DocumentType documentType,
            String categorySlug,
            Visibility visibility,
            LocalDate reviewDueOn) {}

    public record CreateVersion(String versionLabel, LocalDate effectiveFrom, String bodyMarkdown, String changeNote) {}

    public record UpdateVersion(LocalDate effectiveFrom, String bodyMarkdown, String changeNote) {}

    public record Publish(UUID versionId) {}
}
