package com.example.knavi.ingestion.api;

import com.example.knavi.ingestion.application.FileUploadService;
import com.example.knavi.ingestion.application.FileUploadService.StoredFileView;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 原本ファイル（PDF）のアップロードと取得（コンテンツ担当者だけ）。ADR-0010 */
@Tag(name = "admin-files", description = "原本ファイル（PDF）")
@RestController
@RequestMapping("/api/v1/admin/files")
class AdminFileController {

    private final FileUploadService files;

    AdminFileController(FileUploadService files) {
        this.files = files;
    }

    @Operation(summary = "PDF をアップロードする（同じ内容のファイルが既にあれば、それを返す）")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<StoredFileView> upload(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AuthenticatedUser user)
            throws IOException {
        StoredFileView stored = files.upload(file.getOriginalFilename(), file.getBytes(), user);
        return ResponseEntity.status(stored.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(stored);
    }

    @Operation(summary = "ファイルの情報")
    @GetMapping("/{id}")
    StoredFileView get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return files.get(id, user);
    }

    @Operation(summary = "ファイルの中身（PDF）")
    @GetMapping("/{id}/content")
    ResponseEntity<byte[]> content(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        StoredFileView file = files.get(id, user);
        byte[] content = files.content(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // ブラウザの中で表示する（ファイル名は日本語でも崩れないよう RFC 5987 の形で付ける）
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.originalName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }
}
