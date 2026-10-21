package com.example.knavi.navigation.api;

import com.example.knavi.navigation.application.NavigationKeywordService;
import com.example.knavi.navigation.domain.KeywordTargetType;
import com.example.knavi.navigation.domain.NavigationKeyword;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "admin-navigation-keywords", description = "ナビゲーションキーワード（利用者の言葉 → 案内先）の管理")
@RestController
@RequestMapping("/api/v1/admin/navigation-keywords")
class AdminNavigationKeywordController {

    private final NavigationKeywordService service;

    AdminNavigationKeywordController(NavigationKeywordService service) {
        this.service = service;
    }

    record KeywordResponse(
            UUID id, String term, String normalizedTerm, KeywordTargetType targetType, UUID targetId, float weight) {
        static KeywordResponse of(NavigationKeyword k) {
            return new KeywordResponse(
                    k.getId(), k.getTerm(), k.getNormalizedTerm(), k.getTargetType(), k.getTargetId(), k.getWeight());
        }
    }

    record AddKeywordRequest(
            @NotBlank @Size(max = 100) String term,
            @NotNull KeywordTargetType targetType,
            @NotNull UUID targetId,
            @DecimalMin("0.1") @DecimalMax("5.0") Float weight) {}

    @Operation(summary = "案内先ごとのキーワード一覧、または言葉からの逆引き（term）")
    @GetMapping
    List<KeywordResponse> list(
            @RequestParam(required = false) KeywordTargetType targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) String term,
            @AuthenticationPrincipal AuthenticatedUser user) {
        List<NavigationKeyword> result;
        if (term != null) {
            result = service.findByTerm(term, user);
        } else if (targetType != null && targetId != null) {
            result = service.findByTarget(targetType, targetId, user);
        } else {
            throw ApiErrors.badRequest("targetType と targetId、または term を指定してください。");
        }
        return result.stream().map(KeywordResponse::of).toList();
    }

    @Operation(summary = "キーワードを追加する（全角半角などは自動で正規化される）")
    @PostMapping
    ResponseEntity<KeywordResponse> add(
            @Valid @RequestBody AddKeywordRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        var keyword = service.add(
                body.term(), body.targetType(), body.targetId(), body.weight() == null ? 1.0f : body.weight(), user);
        return ResponseEntity.created(URI.create("/api/v1/admin/navigation-keywords/" + keyword.getId()))
                .body(KeywordResponse.of(keyword));
    }

    @Operation(summary = "キーワードを削除する")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
