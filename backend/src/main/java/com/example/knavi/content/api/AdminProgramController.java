package com.example.knavi.content.api;

import com.example.knavi.content.api.ProgramDtos.CreateProgramRequest;
import com.example.knavi.content.api.ProgramDtos.ProgramAttributesRequest;
import com.example.knavi.content.api.ProgramDtos.ProgramResponse;
import com.example.knavi.content.api.ProgramDtos.ReplaceBasisRequest;
import com.example.knavi.content.api.ProgramDtos.ReplaceContactsRequest;
import com.example.knavi.content.application.ProgramService;
import com.example.knavi.content.domain.Program;
import com.example.knavi.content.domain.ProgramContact;
import com.example.knavi.organization.ContactDirectory;
import com.example.knavi.organization.ContactDirectory.ContactRef;
import com.example.knavi.organization.DepartmentDirectory;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ETags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

/** 制度の管理 API（所管部署の CONTENT_EDITOR のみ）。更新系は If-Match 必須 */
@Tag(name = "admin-programs", description = "制度の管理（コンテンツ担当者）")
@RestController
// 応答への変換（関連先・根拠・カテゴリなどの遅延読み込み）もサービスと同じトランザクションの中で行う。
// サービスは保存後に flush するため、応答の版番号（ETag）は更新後の値になる
@Transactional
@RequestMapping("/api/v1/admin/programs")
class AdminProgramController {

    private final ProgramService service;
    private final DepartmentDirectory departments;
    private final ContactDirectory contacts;

    AdminProgramController(ProgramService service, DepartmentDirectory departments, ContactDirectory contacts) {
        this.service = service;
        this.departments = departments;
        this.contacts = contacts;
    }

    @Operation(summary = "自部署が所管する制度の一覧")
    @GetMapping
    List<ProgramResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.listOwnedBy(user).stream().map(this::toResponse).toList();
    }

    @Operation(summary = "制度を作成する（下書き）")
    @PostMapping
    ResponseEntity<ProgramResponse> create(
            @Valid @RequestBody CreateProgramRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        Program program = service.create(
                body.code(), body.ownerDepartmentCode(), body.attributes().toAttributes(), user);
        return withETag(ResponseEntity.created(URI.create("/api/v1/admin/programs/" + program.getId())), program);
    }

    @Operation(summary = "制度の詳細")
    @GetMapping("/{id}")
    ResponseEntity<ProgramResponse> get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.get(id, user));
    }

    @Operation(summary = "制度の説明を更新する")
    @PutMapping("/{id}")
    ResponseEntity<ProgramResponse> update(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ProgramAttributesRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(
                ResponseEntity.ok(), service.update(id, ETags.parseIfMatch(ifMatch), body.toAttributes(), user));
    }

    @Operation(summary = "根拠の条をすべて置き換える（条は文書の公開版から探す）")
    @PutMapping("/{id}/basis")
    ResponseEntity<ProgramResponse> replaceBasis(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReplaceBasisRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        var items = body.basis().stream()
                .map(b -> new ProgramService.BasisItem(b.documentCode(), b.articleNo()))
                .toList();
        return withETag(ResponseEntity.ok(), service.replaceBasis(id, ETags.parseIfMatch(ifMatch), items, user));
    }

    @Operation(summary = "窓口をすべて置き換える")
    @PutMapping("/{id}/contacts")
    ResponseEntity<ProgramResponse> replaceContacts(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReplaceContactsRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        var items = body.contacts().stream()
                .map(c -> new ProgramService.ContactItem(c.contactCode(), c.role()))
                .toList();
        return withETag(ResponseEntity.ok(), service.replaceContacts(id, ETags.parseIfMatch(ifMatch), items, user));
    }

    @Operation(summary = "公開する（窓口1件以上・根拠1件以上・公開範囲が根拠文書より広くないこと）")
    @PostMapping("/{id}/publish")
    ResponseEntity<ProgramResponse> publish(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.publish(id, ETags.parseIfMatch(ifMatch), user));
    }

    @Operation(summary = "アーカイブする（公開をやめる）")
    @PostMapping("/{id}/archive")
    ResponseEntity<ProgramResponse> archive(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.archive(id, ETags.parseIfMatch(ifMatch), user));
    }

    private ResponseEntity<ProgramResponse> withETag(ResponseEntity.BodyBuilder builder, Program program) {
        return builder.eTag(ETags.of(program.getRowVersion())).body(toResponse(program));
    }

    private ProgramResponse toResponse(Program program) {
        Map<UUID, ContactRef> contactsById =
                contacts
                        .findByIds(program.getContacts().stream()
                                .map(ProgramContact::contactId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(ContactRef::id, Function.identity()));
        return ProgramResponse.of(
                program, departments.findById(program.getOwnerDepartmentId()).orElse(null), contactsById);
    }
}
