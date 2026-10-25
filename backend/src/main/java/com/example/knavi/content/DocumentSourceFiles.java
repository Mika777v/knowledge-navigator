package com.example.knavi.content;

import com.example.knavi.shared.security.AuthenticatedUser;
import java.util.Optional;
import java.util.UUID;

/** 利用者が閲覧できる版の原本（PDF）を探す公開 API（原本の配信は ingestion モジュールが行う。ADR-0010） */
public interface DocumentSourceFiles {

    /**
     * 文書の公開済みの版の原本ファイルの ID。文書が見えない（ADR-0004）・版が無い・原本が無いときは空。
     *
     * @param versionId 版（null なら公開中の版）
     */
    Optional<UUID> visibleSourceFile(AuthenticatedUser user, UUID documentId, UUID versionId);
}
