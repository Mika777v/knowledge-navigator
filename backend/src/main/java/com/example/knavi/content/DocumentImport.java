package com.example.knavi.content;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 文書を「公開済み」の状態で取り込む公開 API（ダミーデータ投入・データ移行用）。
 *
 * <p>管理画面の操作とは違い、利用者の権限チェックは行わない。呼び出し元（seed など）はシステム処理に限ること。
 */
public interface DocumentImport {

    /**
     * 文書がなければ作成し、属性を更新する。同じ版ラベルの版がなければ作成して公開版にする。
     * 同じ版ラベルの版が既にあれば本文は変更しない（公開済みの版は不変のため）。
     */
    ImportResult importPublished(ImportedDocument document);

    /** 文書（コードで指定）のタグを置き換える。無いタグは自由タグとして作る */
    void replaceTags(String documentCode, List<String> tagNames);

    /**
     * 文書（コードで指定）の公開中の版に、同じ内容の原本（PDF）と、条ごとの原本のページを結び付ける。
     * 公開済みの版は不変だが、本文は変えずに原本の情報だけを足すデータ投入用の操作（ダミーデータの規程と PDF は同じ内容）。
     *
     * @return 結び付けた条・見出しの数
     */
    int attachSourceToCurrentVersion(String documentCode, UUID sourceFileId, List<DocumentPdfImport.PageRange> pages);

    record ImportedDocument(
            String code,
            String title,
            String summary,
            String documentType,
            String categorySlug,
            UUID ownerDepartmentId,
            String visibility,
            String versionLabel,
            LocalDate effectiveFrom,
            String bodyMarkdown) {}

    record ImportResult(UUID documentId, UUID currentVersionId, boolean versionCreated) {}
}
