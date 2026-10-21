package com.example.knavi.content.api;

import com.example.knavi.content.application.ProgramService;
import com.example.knavi.content.domain.ContactRole;
import com.example.knavi.content.domain.ContentStatus;
import com.example.knavi.content.domain.Program;
import com.example.knavi.organization.ContactDirectory.ContactRef;
import com.example.knavi.organization.DepartmentDirectory.DepartmentRef;
import com.example.knavi.shared.security.Visibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 管理画面向け制度 API の入出力 */
final class ProgramDtos {

    private ProgramDtos() {}

    record ProgramAttributesRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 200) String nameKana,
            @NotBlank String summary,
            String eligibility,
            String benefit,
            String howToApply,
            @Size(max = 200) String deadlineNote,
            String requiredDocuments,
            @NotBlank String categorySlug,
            @NotNull Visibility visibility) {

        ProgramService.Attributes toAttributes() {
            return new ProgramService.Attributes(
                    name,
                    nameKana,
                    summary,
                    eligibility,
                    benefit,
                    howToApply,
                    deadlineNote,
                    requiredDocuments,
                    categorySlug,
                    visibility);
        }
    }

    record CreateProgramRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z0-9-]+", message = "英大文字・数字・ハイフンで入力してください")
            String code,

            @NotBlank String ownerDepartmentCode,
            @NotNull @Valid ProgramAttributesRequest attributes) {}

    record BasisRequest(
            @NotBlank String documentCode,
            @Pattern(regexp = "第\\d+条") String articleNo) {}

    record ReplaceBasisRequest(@NotNull List<@Valid BasisRequest> basis) {}

    record ContactRequest(
            @NotBlank String contactCode, @NotNull ContactRole role) {}

    record ReplaceContactsRequest(@NotNull List<@Valid ContactRequest> contacts) {}

    record BasisResponse(String documentCode, String documentTitle, String articleNo, UUID sectionId, String heading) {}

    record ContactResponse(String code, String name, ContactRole role) {}

    record ProgramResponse(
            UUID id,
            String code,
            String name,
            String nameKana,
            String summary,
            String eligibility,
            String benefit,
            String howToApply,
            String deadlineNote,
            String requiredDocuments,
            DocumentDtos.CategoryResponse category,
            DocumentDtos.DepartmentResponse ownerDepartment,
            Visibility visibility,
            ContentStatus status,
            List<BasisResponse> basis,
            List<ContactResponse> contacts,
            int rowVersion) {

        static ProgramResponse of(Program p, DepartmentRef owner, Map<UUID, ContactRef> contactsById) {
            return new ProgramResponse(
                    p.getId(),
                    p.getCode(),
                    p.getName(),
                    p.getNameKana(),
                    p.getSummary(),
                    p.getEligibility(),
                    p.getBenefit(),
                    p.getHowToApply(),
                    p.getDeadlineNote(),
                    p.getRequiredDocuments(),
                    p.getCategory() == null
                            ? null
                            : new DocumentDtos.CategoryResponse(
                                    p.getCategory().getSlug(), p.getCategory().getName()),
                    DocumentDtos.DepartmentResponse.of(owner),
                    p.getVisibility(),
                    p.getStatus(),
                    p.getBasis().stream()
                            .map(b -> new BasisResponse(
                                    b.getDocument().getCode(),
                                    b.getDocument().getTitle(),
                                    b.getArticleNo(),
                                    b.getSection() == null
                                            ? null
                                            : b.getSection().getId(),
                                    b.getSection() == null
                                            ? null
                                            : b.getSection().getHeading()))
                            .toList(),
                    p.getContacts().stream()
                            .map(c -> {
                                ContactRef ref = contactsById.get(c.contactId());
                                return new ContactResponse(
                                        ref == null ? null : ref.code(), ref == null ? null : ref.name(), c.role());
                            })
                            .toList(),
                    p.getRowVersion());
        }
    }
}
