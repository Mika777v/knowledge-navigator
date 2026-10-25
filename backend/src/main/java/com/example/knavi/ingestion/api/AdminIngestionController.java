package com.example.knavi.ingestion.api;

import com.example.knavi.ingestion.application.IngestionJobService;
import com.example.knavi.ingestion.application.IngestionJobService.IngestionJobView;
import com.example.knavi.ingestion.application.IngestionJobService.Submit;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PDF の取り込み（アップロード済みの PDF から、文書の下書きの版を作る）。コンテンツ担当者だけ。ADR-0010 */
@Tag(name = "admin-ingestion", description = "PDF の取り込み")
@RestController
@RequestMapping("/api/v1/admin")
class AdminIngestionController {

    private final IngestionJobService jobs;

    AdminIngestionController(IngestionJobService jobs) {
        this.jobs = jobs;
    }

    record IngestionRequest(
            @NotNull UUID fileId,
            @NotBlank @Size(max = 20) String versionLabel,
            @NotNull LocalDate effectiveFrom,
            String changeNote) {}

    @Operation(summary = "PDF の取り込みを依頼する（非同期。状況は返したジョブで確かめる）")
    @PostMapping("/documents/{documentId}/ingestion-jobs")
    ResponseEntity<IngestionJobView> submit(
            @PathVariable UUID documentId,
            @Valid @RequestBody IngestionRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        IngestionJobView job = jobs.submit(
                documentId,
                new Submit(body.fileId(), body.versionLabel(), body.effectiveFrom(), body.changeNote()),
                user);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/admin/ingestion-jobs/" + job.id()))
                .body(job);
    }

    @Operation(summary = "文書の取り込みの履歴（新しい順に20件）")
    @GetMapping("/documents/{documentId}/ingestion-jobs")
    List<IngestionJobView> list(@PathVariable UUID documentId, @AuthenticationPrincipal AuthenticatedUser user) {
        return jobs.listForDocument(documentId, user);
    }

    @Operation(summary = "取り込みの状況")
    @GetMapping("/ingestion-jobs/{id}")
    IngestionJobView get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return jobs.get(id, user);
    }
}
