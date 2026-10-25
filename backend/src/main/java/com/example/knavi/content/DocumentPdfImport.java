package com.example.knavi.content;

import com.example.knavi.shared.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * PDF の取り込み（ingestion モジュール）から、文書の下書きの版を作る公開 API（ADR-0010）。
 *
 * <p>権限は取り込みの受付時に {@link #checkCanEdit} で確かめ、版の作成は取り込みジョブ（システム処理）が行う。
 */
public interface DocumentPdfImport {

    /** 文書を所管する部署のコンテンツ担当者か確かめる（文書が無ければ 404、権限が無ければ 403） */
    void checkCanEdit(UUID documentId, AuthenticatedUser user);

    /**
     * PDF の解析結果から下書きの版を作る。同じ版ラベルがあれば 409。
     *
     * @return 作った版の ID
     */
    UUID createDraftVersion(DraftVersion draft);

    /**
     * 見出しごとの原本のページ範囲。
     *
     * @param key 条なら条番号（「第5条」）、それ以外は見出し（「附則」。前文は「前文」）
     */
    record PageRange(String key, int pageFrom, int pageTo) {}

    /**
     * @param bodyMarkdown 版の本文（章は {@code ##}、条は {@code ###}）
     * @param sourceFileId 原本の PDF（stored_files）
     * @param actorId 取り込みを依頼した人（監査ログに記録する）
     */
    record DraftVersion(
            UUID documentId,
            String versionLabel,
            LocalDate effectiveFrom,
            String changeNote,
            String bodyMarkdown,
            UUID sourceFileId,
            List<PageRange> pages,
            UUID actorId) {}
}
