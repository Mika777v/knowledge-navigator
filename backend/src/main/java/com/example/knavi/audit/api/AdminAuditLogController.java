package com.example.knavi.audit.api;

import com.example.knavi.audit.application.AuditLogQueryService;
import com.example.knavi.audit.application.AuditLogQueryService.Filter;
import com.example.knavi.audit.application.AuditLogQueryService.Page;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 監査ログの閲覧（システム管理者だけ）。新しい順。続きは応答の nextCursor を cursor に渡して取得する */
@Tag(name = "admin-audit-logs", description = "監査ログの閲覧")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
class AdminAuditLogController {

    private final AuditLogQueryService service;

    AdminAuditLogController(AuditLogQueryService service) {
        this.service = service;
    }

    @Operation(summary = "監査ログの一覧（期間・操作・対象・操作者で絞り込み）")
    @GetMapping
    Page search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @Min(1) Long cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(AuditLogQueryService.MAX_SIZE) int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.search(new Filter(from, to, action, resourceType, resourceId, actorId), cursor, size, user);
    }
}
