package com.example.knavi.content.api;

import com.example.knavi.content.application.FaqService;
import com.example.knavi.content.domain.ContentStatus;
import com.example.knavi.content.domain.Faq;
import com.example.knavi.content.domain.LinkTargetType;
import com.example.knavi.organization.ContactDirectory.ContactRef;
import com.example.knavi.organization.DepartmentDirectory.DepartmentRef;
import com.example.knavi.shared.security.Visibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** 管理画面向け FAQ API の入出力 */
final class FaqDtos {

    private FaqDtos() {}

    /** 関連先。programCode か documentCode（articleNo を指定すると条）のどちらか */
    record LinkRequest(
            String programCode,
            String documentCode,
            @Pattern(regexp = "第\\d+条") String articleNo) {}

    record FaqContentRequest(
            @NotBlank String question,
            @NotBlank String answerMarkdown,
            @NotBlank String categorySlug,
            String contactCode,
            @NotNull Visibility visibility,
            List<@Valid LinkRequest> links) {

        FaqService.Content toContent() {
            return new FaqService.Content(
                    question,
                    answerMarkdown,
                    categorySlug,
                    contactCode,
                    visibility,
                    links == null
                            ? List.of()
                            : links.stream()
                                    .map(l -> new FaqService.LinkItem(l.programCode(), l.documentCode(), l.articleNo()))
                                    .toList());
        }
    }

    record CreateFaqRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z0-9-]+", message = "英大文字・数字・ハイフンで入力してください")
            String code,

            @NotBlank String ownerDepartmentCode,
            @NotNull @Valid FaqContentRequest content) {}

    /** 関連先。programCode / documentCode / articleNo は更新時（LinkRequest）にそのまま送り返せる */
    record LinkResponse(
            LinkTargetType targetType,
            UUID targetId,
            String programCode,
            String documentCode,
            String articleNo,
            String label) {}

    record FaqResponse(
            UUID id,
            String code,
            String question,
            String answerMarkdown,
            DocumentDtos.CategoryResponse category,
            String contactCode,
            DocumentDtos.DepartmentResponse ownerDepartment,
            Visibility visibility,
            ContentStatus status,
            List<LinkResponse> links,
            int rowVersion) {

        static FaqResponse of(Faq f, DepartmentRef owner, ContactRef contact, List<FaqService.LinkView> links) {
            return new FaqResponse(
                    f.getId(),
                    f.getCode(),
                    f.getQuestion(),
                    f.getAnswerMarkdown(),
                    f.getCategory() == null
                            ? null
                            : new DocumentDtos.CategoryResponse(
                                    f.getCategory().getSlug(), f.getCategory().getName()),
                    contact == null ? null : contact.code(),
                    DocumentDtos.DepartmentResponse.of(owner),
                    f.getVisibility(),
                    f.getStatus(),
                    links.stream()
                            .map(l -> new LinkResponse(
                                    l.targetType(),
                                    l.targetId(),
                                    l.programCode(),
                                    l.documentCode(),
                                    l.articleNo(),
                                    l.label()))
                            .toList(),
                    f.getRowVersion());
        }
    }
}
