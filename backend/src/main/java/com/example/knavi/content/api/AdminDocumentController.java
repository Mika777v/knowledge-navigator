package com.example.knavi.content.api;

import com.example.knavi.content.api.DocumentDtos.CreateDocumentRequest;
import com.example.knavi.content.api.DocumentDtos.CreateVersionRequest;
import com.example.knavi.content.api.DocumentDtos.DocumentResponse;
import com.example.knavi.content.api.DocumentDtos.PublishRequest;
import com.example.knavi.content.api.DocumentDtos.UpdateDocumentRequest;
import com.example.knavi.content.api.DocumentDtos.UpdateVersionRequest;
import com.example.knavi.content.api.DocumentDtos.VersionResponse;
import com.example.knavi.content.api.DocumentDtos.VersionSummaryResponse;
import com.example.knavi.content.application.DocumentCommands;
import com.example.knavi.content.application.DocumentService;
import com.example.knavi.content.domain.Document;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文書の管理 API（所管部署の CONTENT_EDITOR のみ）。
 *
 * <p>更新系は楽観ロック: レスポンスの ETag を If-Match に指定する。
 */
@Tag(name = "admin-documents", description = "文書・版の管理（コンテンツ担当者）")
@RestController
@RequestMapping("/api/v1/admin/documents")
class AdminDocumentController {

    private final DocumentService service;
    private final DepartmentDirectory departments;

    AdminDocumentController(DocumentService service, DepartmentDirectory departments) {
        this.service = service;
        this.departments = departments;
    }

    @Operation(summary = "自部署が所管する文書の一覧")
    @GetMapping
    List<DocumentResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.listOwnedBy(user).stream().map(this::toResponse).toList();
    }

    @Operation(summary = "文書を作成する（下書き）")
    @PostMapping
    ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        Document document = service.create(
                new DocumentCommands.Create(
                        body.code(),
                        body.title(),
                        body.summary(),
                        body.documentType(),
                        body.categorySlug(),
                        body.ownerDepartmentCode(),
                        body.visibility(),
                        body.reviewDueOn()),
                user);
        return withETag(ResponseEntity.created(URI.create("/api/v1/admin/documents/" + document.getId())), document);
    }

    @Operation(summary = "文書の詳細")
    @GetMapping("/{id}")
    ResponseEntity<DocumentResponse> get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.get(id, user));
    }

    @Operation(summary = "文書の属性を更新する")
    @PutMapping("/{id}")
    ResponseEntity<DocumentResponse> update(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateDocumentRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Document document = service.update(
                id,
                ETags.parseIfMatch(ifMatch),
                new DocumentCommands.Update(
                        body.title(),
                        body.summary(),
                        body.documentType(),
                        body.categorySlug(),
                        body.visibility(),
                        body.reviewDueOn()),
                user);
        return withETag(ResponseEntity.ok(), document);
    }

    @Operation(summary = "版の一覧（新しい順）")
    @GetMapping("/{id}/versions")
    List<VersionSummaryResponse> versions(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        UUID current = service.get(id, user).getCurrentVersionId();
        return service.listVersions(id, user).stream()
                .map(v -> VersionSummaryResponse.of(v, current))
                .toList();
    }

    @Operation(summary = "新しい版を作成する（本文は条・見出し単位のセクションに自動分割される）")
    @PostMapping("/{id}/versions")
    ResponseEntity<VersionResponse> createVersion(
            @PathVariable UUID id,
            @Valid @RequestBody CreateVersionRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        var version = service.createVersion(
                id,
                new DocumentCommands.CreateVersion(
                        body.versionLabel(), body.effectiveFrom(), body.bodyMarkdown(), body.changeNote()),
                user);
        return ResponseEntity.created(URI.create("/api/v1/admin/documents/" + id + "/versions/" + version.getId()))
                .body(VersionResponse.of(version));
    }

    @Operation(summary = "版の詳細（本文と分割されたセクション）")
    @GetMapping("/{id}/versions/{versionId}")
    VersionResponse version(
            @PathVariable UUID id, @PathVariable UUID versionId, @AuthenticationPrincipal AuthenticatedUser user) {
        return VersionResponse.of(service.getVersion(id, versionId, user));
    }

    @Operation(summary = "下書きの版を更新する（公開済みの版は変更できない）")
    @PutMapping("/{id}/versions/{versionId}")
    VersionResponse updateVersion(
            @PathVariable UUID id,
            @PathVariable UUID versionId,
            @Valid @RequestBody UpdateVersionRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return VersionResponse.of(service.updateVersion(
                id,
                versionId,
                new DocumentCommands.UpdateVersion(body.effectiveFrom(), body.bodyMarkdown(), body.changeNote()),
                user));
    }

    @Operation(summary = "指定した版を公開する")
    @PostMapping("/{id}/publish")
    ResponseEntity<DocumentResponse> publish(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody PublishRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Document document =
                service.publish(id, ETags.parseIfMatch(ifMatch), new DocumentCommands.Publish(body.versionId()), user);
        return withETag(ResponseEntity.ok(), document);
    }

    @Operation(summary = "文書をアーカイブする（公開をやめる）")
    @PostMapping("/{id}/archive")
    ResponseEntity<DocumentResponse> archive(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.archive(id, ETags.parseIfMatch(ifMatch), user));
    }

    private ResponseEntity<DocumentResponse> withETag(ResponseEntity.BodyBuilder builder, Document document) {
        return builder.eTag(ETags.of(document.getRowVersion())).body(toResponse(document));
    }

    private DocumentResponse toResponse(Document document) {
        return DocumentResponse.of(
                document, departments.findById(document.getOwnerDepartmentId()).orElse(null));
    }
}
