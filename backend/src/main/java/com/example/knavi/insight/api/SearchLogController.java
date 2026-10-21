package com.example.knavi.insight.api;

import com.example.knavi.insight.application.SearchLogService;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 検索結果のクリックとフィードバック（検索の改善に使う）。自分の検索に対してだけ記録できる */
@Tag(name = "insight", description = "検索ログへのクリック・フィードバックの記録")
@RestController
@RequestMapping("/api/v1/search-logs/{id}")
class SearchLogController {

    private final SearchLogService service;

    SearchLogController(SearchLogService service) {
        this.service = service;
    }

    /**
     * @param targetType クリックした結果の種類
     * @param rank ブロック内での表示順（1 始まり）
     */
    record ClickRequest(
            @NotNull @Pattern(regexp = "PROGRAM|DOCUMENT|SECTION|FAQ|CONTACT")
            String targetType,

            @NotNull UUID targetId,
            @Min(1) @Max(1000) int rank) {}

    record FeedbackRequest(
            @NotNull Boolean helpful,
            @Size(max = SearchLogService.MAX_COMMENT_LENGTH) String comment) {}

    @Operation(summary = "検索結果のクリックを記録する")
    @PostMapping("/clicks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void click(
            @PathVariable UUID id,
            @Valid @RequestBody ClickRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.recordClick(id, user.id(), body.targetType(), body.targetId(), body.rank());
    }

    @Operation(summary = "検索結果が役に立ったかを記録する（押し直すと上書き）")
    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void feedback(
            @PathVariable UUID id,
            @Valid @RequestBody FeedbackRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.recordFeedback(id, user.id(), body.helpful(), body.comment());
    }
}
