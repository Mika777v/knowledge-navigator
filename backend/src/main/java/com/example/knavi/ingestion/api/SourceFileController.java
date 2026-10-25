package com.example.knavi.ingestion.api;

import com.example.knavi.ingestion.application.SourceFileService;
import com.example.knavi.ingestion.application.SourceFileService.SourceFile;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 文書の原本（PDF）。画面からは「原本を開く」「p.3」のリンクで、ブラウザの PDF ビューアで開く（#page=3） */
@Tag(name = "content", description = "文書・制度・FAQ の閲覧")
@RestController
class SourceFileController {

    private final SourceFileService sources;

    SourceFileController(SourceFileService sources) {
        this.sources = sources;
    }

    @Operation(summary = "文書の原本（PDF）。version で過去の版の原本も開ける。原本の無い版・見えない文書は 404")
    @GetMapping("/api/v1/documents/{documentId}/source")
    ResponseEntity<byte[]> source(
            @PathVariable UUID documentId,
            @RequestParam(required = false) UUID version,
            @AuthenticationPrincipal AuthenticatedUser user) {
        SourceFile file = sources.open(documentId, version, user);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                // 公開範囲のある文書なので、共有のキャッシュには残さない
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }
}
