package com.example.knavi.content.api;

import com.example.knavi.content.application.LifeEventAdminService;
import com.example.knavi.content.application.LifeEventAdminService.Attributes;
import com.example.knavi.content.application.LifeEventAdminService.LifeEventView;
import com.example.knavi.content.application.LifeEventAdminService.ProgramLink;
import com.example.knavi.content.application.LifeEventAdminService.Relation;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ETags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
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

@Tag(name = "admin-life-events", description = "きっかけ（結婚・出産など）の管理（コンテンツ担当者）")
@RestController
@RequestMapping("/api/v1/admin/life-events")
class AdminLifeEventController {

    private final LifeEventAdminService service;

    AdminLifeEventController(LifeEventAdminService service) {
        this.service = service;
    }

    record ProgramLinkRequest(
            @NotBlank String programCode, @NotNull Relation relation) {}

    record LifeEventRequest(
            @NotBlank @Size(max = 50) String name,
            @Size(max = 20) String icon,
            @Size(max = 500) String description,
            @Min(0) @Max(1000) int sortOrder,
            @NotNull @Size(max = 50) List<@Valid ProgramLinkRequest> programs) {

        Attributes toAttributes() {
            return new Attributes(
                    name,
                    icon,
                    description,
                    sortOrder,
                    programs.stream()
                            .map(p -> new ProgramLink(p.programCode(), p.relation()))
                            .toList());
        }
    }

    record CreateLifeEventRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z0-9_-]+", message = "英大文字・数字・ハイフン・下線で入力してください")
            String code,

            @NotNull @Valid LifeEventRequest content) {}

    @Operation(summary = "きっかけの一覧（紐づく制度つき。下書きの制度も含む）")
    @GetMapping
    List<LifeEventView> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user);
    }

    @Operation(summary = "きっかけの詳細")
    @GetMapping("/{code}")
    ResponseEntity<LifeEventView> get(@PathVariable String code, @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(ResponseEntity.ok(), service.get(code, user));
    }

    @Operation(summary = "きっかけを作成する")
    @PostMapping
    ResponseEntity<LifeEventView> create(
            @Valid @RequestBody CreateLifeEventRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        LifeEventView created = service.create(body.code(), body.content().toAttributes(), user);
        return withETag(ResponseEntity.created(URI.create("/api/v1/admin/life-events/" + created.code())), created);
    }

    @Operation(summary = "きっかけを更新する（紐づける制度もまとめて置き換える。If-Match 必須）")
    @PutMapping("/{code}")
    ResponseEntity<LifeEventView> update(
            @PathVariable String code,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody LifeEventRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return withETag(
                ResponseEntity.ok(), service.update(code, ETags.parseIfMatch(ifMatch), body.toAttributes(), user));
    }

    private static ResponseEntity<LifeEventView> withETag(ResponseEntity.BodyBuilder builder, LifeEventView view) {
        return builder.eTag(ETags.of(view.rowVersion())).body(view);
    }
}
