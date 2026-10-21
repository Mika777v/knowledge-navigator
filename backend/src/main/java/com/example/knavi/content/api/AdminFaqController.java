package com.example.knavi.content.api;

import com.example.knavi.content.api.FaqDtos.CreateFaqRequest;
import com.example.knavi.content.api.FaqDtos.FaqContentRequest;
import com.example.knavi.content.api.FaqDtos.FaqResponse;
import com.example.knavi.content.application.FaqService;
import com.example.knavi.content.domain.Faq;
import com.example.knavi.organization.ContactDirectory;
import com.example.knavi.organization.DepartmentDirectory;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ETags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FAQ の管理 API（所管部署の CONTENT_EDITOR のみ）。更新系は If-Match 必須 */
@Tag(name = "admin-faqs", description = "FAQ の管理（コンテンツ担当者）")
@RestController
// 応答への変換（関連先・根拠・カテゴリなどの遅延読み込み）もサービスと同じトランザクションの中で行う。
// サービスは保存後に flush するため、応答の版番号（ETag）は更新後の値になる
@Transactional
@RequestMapping("/api/v1/admin/faqs")
class AdminFaqController {

    private final FaqService service;
    private final DepartmentDirectory departments;
    private final ContactDirectory contacts;

    AdminFaqController(FaqService service, DepartmentDirectory departments, ContactDirectory contacts) {
        this.service = service;
        this.departments = departments;
        this.contacts = contacts;
    }

    @Operation(summary = "自部署が所管する FAQ の一覧")
    @GetMapping
    List<FaqResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.listOwnedBy(user).stream().map(this::toResponse).toList();
    }

    @Operation(summary = "FAQ を作成する（下書き）")
    @PostMapping
    ResponseEntity<FaqResponse> create(
            @Valid @RequestBody CreateFaqRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        Faq faq = service.create(
                body.code(), body.ownerDepartmentCode(), body.content().toContent(), user);
        return withETag(ResponseEntity.created(URI.create("/api/v1/admin/faqs/" + faq.getId())), faq);
    }

    @Operation(summary = "FAQ の詳細")
    @GetMapping("/{id}")
    ResponseEntity<FaqResponse> get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.get(id, user));
    }

    @Operation(summary = "FAQ を更新する")
    @PutMapping("/{id}")
    ResponseEntity<FaqResponse> update(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody FaqContentRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.update(id, ETags.parseIfMatch(ifMatch), body.toContent(), user));
    }

    @Operation(summary = "公開する（窓口か関連制度が必要。公開範囲は関連文書より広くできない）")
    @PostMapping("/{id}/publish")
    ResponseEntity<FaqResponse> publish(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.publish(id, ETags.parseIfMatch(ifMatch), user));
    }

    @Operation(summary = "アーカイブする（公開をやめる）")
    @PostMapping("/{id}/archive")
    ResponseEntity<FaqResponse> archive(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.archive(id, ETags.parseIfMatch(ifMatch), user));
    }

    private ResponseEntity<FaqResponse> withETag(ResponseEntity.BodyBuilder builder, Faq faq) {
        return builder.eTag(ETags.of(faq.getRowVersion())).body(toResponse(faq));
    }

    private FaqResponse toResponse(Faq faq) {
        return FaqResponse.of(
                faq,
                departments.findById(faq.getOwnerDepartmentId()).orElse(null),
                faq.getContactId() == null
                        ? null
                        : contacts.findById(faq.getContactId()).orElse(null),
                service.describeLinks(faq));
    }
}
