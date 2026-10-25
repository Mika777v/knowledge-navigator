package com.example.knavi.ingestion.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.content.DocumentPdfImport;
import com.example.knavi.content.DocumentPdfImport.DraftVersion;
import com.example.knavi.content.DocumentPdfImport.PageRange;
import com.example.knavi.ingestion.domain.PdfExtractionException;
import com.example.knavi.ingestion.domain.RegulationPdfParser.ParsedRegulation;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.ErrorResponseException;

/**
 * 取り込みジョブを1件ずつ処理する（ADR-0010）。定期実行は {@link IngestionScheduler}、テストからは {@link #runOnce()} を直接呼ぶ。
 *
 * <ol>
 *   <li>待ちのジョブを {@code FOR UPDATE SKIP LOCKED} で1件取り出し、実行中にする（複数のワーカーが同じジョブを取らない）
 *   <li>PDF を解析して下書きの版を作り、成功にする（版の作成と状態の更新は同じトランザクション）
 *   <li>失敗したら理由を残す。PDF の内容や版ラベルの重複など、やり直しても変わらないものはすぐ失敗にし、
 *       それ以外（ストレージに一時的につながらないなど）は間をあけて {@value #MAX_ATTEMPTS} 回まで試す
 * </ol>
 */
@Component
public class IngestionWorker {

    static final int MAX_ATTEMPTS = 3;
    /** 実行中のまま止まったジョブ（backend の再起動など）を、待ちに戻すまでの時間 */
    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final JdbcClient jdbc;
    private final PdfParseService parser;
    private final DocumentPdfImport documents;
    private final AuditLog auditLog;
    private final TransactionTemplate tx;

    IngestionWorker(
            JdbcClient jdbc,
            PdfParseService parser,
            DocumentPdfImport documents,
            AuditLog auditLog,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.parser = parser;
        this.documents = documents;
        this.auditLog = auditLog;
        this.tx = new TransactionTemplate(transactionManager);
    }

    private record ClaimedJob(
            UUID id,
            UUID documentId,
            UUID fileId,
            String versionLabel,
            LocalDate effectiveFrom,
            String changeNote,
            int attempts,
            UUID createdBy) {}

    /**
     * 待ちのジョブを1件処理する。
     *
     * @return 処理したら true（待ちのジョブが無ければ false）
     */
    public boolean runOnce() {
        requeueStaleJobs();
        Optional<ClaimedJob> claimed = tx.execute(status -> claim());
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        ClaimedJob job = claimed.get();
        try {
            tx.executeWithoutResult(status -> process(job));
        } catch (PdfExtractionException e) {
            fail(job, e.getMessage());
        } catch (ErrorResponseException e) {
            // 版ラベルの重複・文書が無いなど、やり直しても変わらないもの
            String detail = e.getBody().getDetail();
            fail(job, detail != null ? detail : "取り込めませんでした。");
        } catch (RuntimeException e) {
            log.warn("取り込みジョブ {} が失敗しました（{} 回目）", job.id(), job.attempts(), e);
            retryOrFail(job);
        }
        return true;
    }

    private Optional<ClaimedJob> claim() {
        return jdbc.sql("""
                        UPDATE ingestion_jobs SET status = 'RUNNING', attempts = attempts + 1, started_at = now()
                        WHERE id = (
                          SELECT id FROM ingestion_jobs WHERE status = 'QUEUED' AND next_run_at <= now()
                          ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1)
                        RETURNING id, document_id, file_id, version_label, effective_from, change_note, attempts, created_by
                        """)
                .query((rs, i) -> new ClaimedJob(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getObject("file_id", UUID.class),
                        rs.getString("version_label"),
                        rs.getObject("effective_from", LocalDate.class),
                        rs.getString("change_note"),
                        rs.getInt("attempts"),
                        rs.getObject("created_by", UUID.class)))
                .optional();
    }

    private void process(ClaimedJob job) {
        ParsedRegulation parsed = parser.parseForJob(job.fileId());
        UUID versionId = documents.createDraftVersion(new DraftVersion(
                job.documentId(),
                job.versionLabel(),
                job.effectiveFrom(),
                job.changeNote(),
                parsed.markdown(),
                job.fileId(),
                parsed.sections().stream()
                        .map(s -> new PageRange(
                                s.articleNo() != null ? s.articleNo() : s.heading() != null ? s.heading() : "前文",
                                s.pageFrom(),
                                s.pageTo()))
                        .toList(),
                job.createdBy()));
        jdbc.sql("""
                        UPDATE ingestion_jobs SET status = 'SUCCEEDED', result_version_id = :version, error_message = NULL,
                          finished_at = now()
                        WHERE id = :id
                        """).param("version", versionId).param("id", job.id()).update();
        auditLog.record(
                job.createdBy(),
                "INGESTION_JOB_SUCCEEDED",
                "DOCUMENT",
                job.documentId(),
                Map.of("jobId", job.id(), "versionId", versionId, "pages", parsed.pageCount()));
    }

    private void fail(ClaimedJob job, String reason) {
        tx.executeWithoutResult(status -> {
            jdbc.sql("UPDATE ingestion_jobs SET status = 'FAILED', error_message = :reason, finished_at = now()"
                            + " WHERE id = :id")
                    .param("reason", reason)
                    .param("id", job.id())
                    .update();
            auditLog.record(
                    job.createdBy(),
                    "INGESTION_JOB_FAILED",
                    "DOCUMENT",
                    job.documentId(),
                    Map.of("jobId", job.id(), "reason", reason));
        });
    }

    private void retryOrFail(ClaimedJob job) {
        if (job.attempts() >= MAX_ATTEMPTS) {
            fail(job, "取り込み中にエラーが発生しました（" + MAX_ATTEMPTS + "回試しました）。時間をおいて、もう一度取り込んでください。");
            return;
        }
        tx.executeWithoutResult(status -> jdbc.sql("""
                        UPDATE ingestion_jobs SET status = 'QUEUED', next_run_at = now() + make_interval(secs => :wait),
                          error_message = :reason
                        WHERE id = :id
                        """)
                .param("wait", 30 * job.attempts())
                .param("reason", "取り込み中にエラーが発生したため、もう一度試します（" + job.attempts() + "回目）。")
                .param("id", job.id())
                .update());
    }

    /** 実行中のまま長く止まっているジョブを待ちに戻す（試行回数はそのまま数える） */
    private void requeueStaleJobs() {
        tx.executeWithoutResult(status ->
                jdbc.sql("""
                        UPDATE ingestion_jobs SET status = 'QUEUED', next_run_at = now()
                        WHERE status = 'RUNNING' AND started_at < now() - make_interval(secs => :stale)
                        """).param("stale", (int) STALE_AFTER.toSeconds()).update());
    }
}
