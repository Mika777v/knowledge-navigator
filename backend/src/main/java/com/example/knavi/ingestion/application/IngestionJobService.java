package com.example.knavi.ingestion.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.content.DocumentPdfImport;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PDF の取り込みの受付と状況の確認（ADR-0010）。処理は {@link IngestionWorker} が非同期に行う。
 *
 * <p>受付時に、文書を所管する部署のコンテンツ担当者かを確かめる。成功すると、その文書に下書きの版ができる
 */
@Service
@Transactional
public class IngestionJobService {

    static final String JOB_COLUMNS = """
            j.id, j.document_id, j.file_id, f.original_name, j.version_label, j.effective_from, j.change_note,
            j.status, j.attempts, j.error_message, j.result_version_id, j.created_at, j.started_at, j.finished_at
            """;

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;
    private final DocumentPdfImport documents;
    private final AuditLog auditLog;

    IngestionJobService(JdbcClient jdbc, AccessPolicy accessPolicy, DocumentPdfImport documents, AuditLog auditLog) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.documents = documents;
        this.auditLog = auditLog;
    }

    /**
     * @param status QUEUED（待ち）・RUNNING（実行中）・SUCCEEDED（成功）・FAILED（失敗）
     * @param errorMessage 失敗・再試行の理由（利用者に見せる文）
     * @param resultVersionId 成功したときに作った下書きの版
     */
    public record IngestionJobView(
            UUID id,
            UUID documentId,
            UUID fileId,
            String fileName,
            String versionLabel,
            LocalDate effectiveFrom,
            String changeNote,
            String status,
            int attempts,
            String errorMessage,
            UUID resultVersionId,
            OffsetDateTime createdAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt) {}

    public record Submit(UUID fileId, String versionLabel, LocalDate effectiveFrom, String changeNote) {}

    public IngestionJobView submit(UUID documentId, Submit in, AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        documents.checkCanEdit(documentId, user);
        boolean fileExists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM stored_files WHERE id = :id)")
                .param("id", in.fileId())
                .query(Boolean.class)
                .single();
        if (!fileExists) {
            throw ApiErrors.notFound("ファイルが見つかりません。先に PDF をアップロードしてください。");
        }
        boolean running = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM ingestion_jobs WHERE document_id = :doc AND version_label = :label
                          AND status IN ('QUEUED', 'RUNNING'))
                        """)
                .param("doc", documentId)
                .param("label", in.versionLabel())
                .query(Boolean.class)
                .single();
        if (running) {
            throw ApiErrors.conflict("同じ版ラベルの取り込みが実行中です。終わるまでお待ちください。");
        }
        UUID id = jdbc.sql("""
                        INSERT INTO ingestion_jobs (file_id, document_id, version_label, effective_from, change_note, created_by)
                        VALUES (:file, :doc, :label, :from, :note, :user) RETURNING id
                        """)
                .param("file", in.fileId())
                .param("doc", documentId)
                .param("label", in.versionLabel())
                .param("from", in.effectiveFrom())
                .param("note", in.changeNote())
                .param("user", user.id())
                .query(UUID.class)
                .single();
        auditLog.record(
                user.id(),
                "INGESTION_JOB_SUBMITTED",
                "DOCUMENT",
                documentId,
                Map.of("jobId", id, "fileId", in.fileId(), "versionLabel", in.versionLabel()));
        return find(id);
    }

    @Transactional(readOnly = true)
    public IngestionJobView get(UUID jobId, AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        IngestionJobView job = find(jobId);
        documents.checkCanEdit(job.documentId(), user);
        return job;
    }

    /** 文書の取り込みの履歴（新しい順） */
    @Transactional(readOnly = true)
    public List<IngestionJobView> listForDocument(UUID documentId, AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        documents.checkCanEdit(documentId, user);
        return jdbc.sql("SELECT " + JOB_COLUMNS
                        + " FROM ingestion_jobs j JOIN stored_files f ON f.id = j.file_id"
                        + " WHERE j.document_id = :doc ORDER BY j.created_at DESC LIMIT 20")
                .param("doc", documentId)
                .query(IngestionJobService::jobView)
                .list();
    }

    private IngestionJobView find(UUID jobId) {
        return jdbc.sql("SELECT " + JOB_COLUMNS
                        + " FROM ingestion_jobs j JOIN stored_files f ON f.id = j.file_id WHERE j.id = :id")
                .param("id", jobId)
                .query(IngestionJobService::jobView)
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("取り込みが見つかりません。"));
    }

    static IngestionJobView jobView(ResultSet rs, int i) throws SQLException {
        return new IngestionJobView(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getObject("file_id", UUID.class),
                rs.getString("original_name"),
                rs.getString("version_label"),
                rs.getObject("effective_from", LocalDate.class),
                rs.getString("change_note"),
                rs.getString("status"),
                rs.getInt("attempts"),
                rs.getString("error_message"),
                rs.getObject("result_version_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class));
    }
}
