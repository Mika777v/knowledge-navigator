package com.example.knavi.insight.api;

import com.example.knavi.insight.application.AnalyticsService;
import com.example.knavi.insight.application.AnalyticsService.FeedbackSummary;
import com.example.knavi.insight.application.AnalyticsService.QueryReport;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 検索の分析（コンテンツ担当者・システム管理者）。期間は日本時間の日付で指定し、省略時は今日までの30日間 */
@Tag(name = "admin-analytics", description = "ゼロヒット・人気クエリ・フィードバックの集計")
@RestController
@RequestMapping("/api/v1/admin/analytics")
class AdminAnalyticsController {

    private final AnalyticsService service;

    AdminAnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @Operation(summary = "何も見つからなかった質問（FAQ・辞書の改善候補）")
    @GetMapping("/zero-hits")
    QueryReport zeroHits(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "20") @Min(1) @Max(AnalyticsService.MAX_LIMIT) int limit,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.zeroHits(from, to, limit, user);
    }

    @Operation(summary = "よく検索された質問（ゼロヒット・クリックの件数つき）")
    @GetMapping("/top-queries")
    QueryReport topQueries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "20") @Min(1) @Max(AnalyticsService.MAX_LIMIT) int limit,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.topQueries(from, to, limit, user);
    }

    @Operation(summary = "検索結果へのフィードバック（件数とコメント）")
    @GetMapping("/feedback")
    FeedbackSummary feedback(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "20") @Min(1) @Max(AnalyticsService.MAX_LIMIT) int limit,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.feedback(from, to, limit, user);
    }
}
