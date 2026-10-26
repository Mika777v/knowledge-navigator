package com.example.knavi.navigation.api;

import com.example.knavi.navigation.application.SynonymService;
import com.example.knavi.navigation.application.SynonymService.SynonymGroupView;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ETags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "admin-synonyms", description = "類義語辞書（同じ意味の語のグループ）の管理。更新は If-Match 必須")
@RestController
@RequestMapping("/api/v1/admin/synonyms")
class AdminSynonymController {

    private final SynonymService service;

    AdminSynonymController(SynonymService service) {
        this.service = service;
    }

    /**
     * @param canonical 代表の語（グループの語に自動で含める）
     * @param terms 同じ意味の語（全角半角・大文字小文字のちがいは同じ語とみなす）
     */
    record SynonymGroupRequest(
            @NotBlank @Size(max = 100) String canonical,
            @NotNull @Size(max = 20) List<@Size(max = 100) String> terms) {}

    @Operation(summary = "類義語のグループの一覧（q を指定すると、その語を含むグループだけ）")
    @GetMapping
    List<SynonymGroupView> list(
            @RequestParam(required = false) String q, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(q, user);
    }

    @Operation(summary = "類義語のグループ（ETag に版番号）")
    @GetMapping("/{id}")
    ResponseEntity<SynonymGroupView> get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        SynonymGroupView group = service.get(id, user);
        return ResponseEntity.ok().eTag(ETags.of(group.rowVersion())).body(group);
    }

    @Operation(summary = "類義語のグループを追加する（ほかのグループに登録済みの語があれば 409）")
    @PostMapping
    ResponseEntity<SynonymGroupView> create(
            @Valid @RequestBody SynonymGroupRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        SynonymGroupView created = service.create(body.canonical(), body.terms(), user);
        return ResponseEntity.created(URI.create("/api/v1/admin/synonyms/" + created.id()))
                .eTag(ETags.of(created.rowVersion()))
                .body(created);
    }

    @Operation(summary = "類義語のグループを置き換える（If-Match 必須）")
    @PutMapping("/{id}")
    ResponseEntity<SynonymGroupView> update(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody SynonymGroupRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        SynonymGroupView updated =
                service.update(id, ETags.parseIfMatch(ifMatch), body.canonical(), body.terms(), user);
        return ResponseEntity.ok().eTag(ETags.of(updated.rowVersion())).body(updated);
    }

    @Operation(summary = "類義語のグループを削除する")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
