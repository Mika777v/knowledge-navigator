package com.example.knavi.ingestion.application;

import com.example.knavi.content.DocumentSourceFiles;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 社員が文書の原本（PDF）を開く。文書が見えるかは content モジュールが公開範囲で判定する（ADR-0004）。
 * 見えない・原本が無いときは、存在しない場合と区別できないよう 404 にする
 */
@Service
@Transactional(readOnly = true)
public class SourceFileService {

    private final DocumentSourceFiles documents;
    private final JdbcClient jdbc;
    private final FileStorage storage;

    SourceFileService(DocumentSourceFiles documents, JdbcClient jdbc, FileStorage storage) {
        this.documents = documents;
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public record SourceFile(String fileName, byte[] content) {}

    /** @param versionId 版（null なら公開中の版） */
    public SourceFile open(UUID documentId, UUID versionId, AuthenticatedUser user) {
        UUID fileId = documents
                .visibleSourceFile(user, documentId, versionId)
                .orElseThrow(() -> ApiErrors.notFound("原本が見つかりません。"));
        record Row(String key, String name) {}
        Row row = jdbc.sql("SELECT storage_key, original_name FROM stored_files WHERE id = :id")
                .param("id", fileId)
                .query((rs, i) -> new Row(rs.getString("storage_key"), rs.getString("original_name")))
                .single();
        byte[] content = storage.get(row.key()).orElseThrow(() -> ApiErrors.notFound("原本が見つかりません。"));
        return new SourceFile(row.name(), content);
    }
}
