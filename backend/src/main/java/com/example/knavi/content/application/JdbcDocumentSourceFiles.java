package com.example.knavi.content.application;

import com.example.knavi.content.DocumentSourceFiles;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.security.SqlPredicate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 文書の詳細と同じ条件（公開範囲の絞り込み・公開済みの版だけ）で原本を探す */
@Service
@Transactional(readOnly = true)
class JdbcDocumentSourceFiles implements DocumentSourceFiles {

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;

    JdbcDocumentSourceFiles(JdbcClient jdbc, AccessPolicy accessPolicy) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public Optional<UUID> visibleSourceFile(AuthenticatedUser user, UUID documentId, UUID versionId) {
        SqlPredicate visible = accessPolicy.readPredicate(AccessScope.of(user), "documents", "d", Purpose.VIEW);
        return jdbc.sql("SELECT v.source_file_id FROM documents d JOIN document_versions v ON v.document_id = d.id"
                        + " WHERE d.id = :id AND v.published_at IS NOT NULL AND v.source_file_id IS NOT NULL"
                        + " AND v.id = COALESCE(CAST(:version AS uuid), d.current_version_id) AND "
                        + visible.sql())
                .params(visible.params())
                .param("id", documentId)
                .param("version", versionId)
                .query(UUID.class)
                .optional();
    }
}
