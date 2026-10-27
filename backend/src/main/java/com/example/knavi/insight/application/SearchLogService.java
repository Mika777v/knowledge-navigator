package com.example.knavi.insight.application;

import com.example.knavi.insight.SearchLogRecorder;
import com.example.knavi.shared.id.Ids;
import com.example.knavi.shared.web.ApiErrors;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 検索ログ・クリック・フィードバックの記録。
 *
 * <p>クリックとフィードバックは、その検索をした本人（仮名が一致する人）からだけ受け付ける。
 * 他人の検索ログの ID を指定された場合は、存在しない場合と同じく 404 にする。
 */
@Service
public class SearchLogService implements SearchLogRecorder {

    public static final int MAX_COMMENT_LENGTH = 1000;
    private static final Logger log = LoggerFactory.getLogger(SearchLogService.class);

    private final JdbcClient jdbc;
    private final UserHasher hasher;
    private final JsonMapper json;

    private final TransactionTemplate newTransaction;

    SearchLogService(
            JdbcClient jdbc, UserHasher hasher, JsonMapper json, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.hasher = hasher;
        this.json = json;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 検索は読み取り専用のトランザクションで動くため、ログは別のトランザクションで書く。
     * 失敗（コミット時の失敗を含む）はここで受け止め、検索の結果には影響させない。
     */
    @Override
    public Optional<UUID> record(SearchLogEntry entry) {
        try {
            return Optional.ofNullable(newTransaction.execute(status -> insert(entry)));
        } catch (RuntimeException e) {
            log.warn("検索ログを記録できませんでした", e);
            return Optional.empty();
        }
    }

    private UUID insert(SearchLogEntry entry) {
        UUID id = Ids.newId();
        jdbc.sql("""
                            INSERT INTO search_logs (id, user_hash, department_id, query, normalized_query, mode,
                              result_counts, top_hits, latency_ms)
                            VALUES (:id, :user, :dept, :query, :normalized, :mode, CAST(:counts AS jsonb),
                              CAST(:hits AS jsonb), :latency)
                            """)
                .param("id", id)
                .param("user", hasher.hash(entry.userId()))
                .param("dept", entry.departmentId())
                .param("query", entry.query())
                .param("normalized", entry.normalizedQuery())
                .param("counts", json.writeValueAsString(entry.resultCounts()))
                .param("hits", json.writeValueAsString(entry.topHits()))
                .param("latency", (int) Math.min(entry.latencyMs(), Integer.MAX_VALUE))
                .param("mode", entry.mode().name())
                .update();
        return id;
    }

    @Transactional
    public void recordClick(UUID searchLogId, UUID userId, String targetType, UUID targetId, int rank) {
        checkOwnLog(searchLogId, userId);
        jdbc.sql("""
                        INSERT INTO search_clicks (search_log_id, target_type, target_id, rank)
                        VALUES (:log, :type, :target, :rank)
                        """)
                .param("log", searchLogId)
                .param("type", targetType)
                .param("target", targetId)
                .param("rank", rank)
                .update();
    }

    /** 1回の検索につき最新のフィードバックだけを残す（押し直しは上書き） */
    @Transactional
    public void recordFeedback(UUID searchLogId, UUID userId, boolean helpful, String comment) {
        checkOwnLog(searchLogId, userId);
        String trimmed = comment == null || comment.isBlank() ? null : comment.strip();
        jdbc.sql("DELETE FROM feedbacks WHERE search_log_id = :log")
                .param("log", searchLogId)
                .update();
        jdbc.sql("""
                        INSERT INTO feedbacks (id, search_log_id, helpful, comment)
                        VALUES (:id, :log, :helpful, :comment)
                        """)
                .param("id", Ids.newId())
                .param("log", searchLogId)
                .param("helpful", helpful)
                .param("comment", trimmed)
                .update();
    }

    private void checkOwnLog(UUID searchLogId, UUID userId) {
        boolean own = jdbc.sql("SELECT EXISTS (SELECT 1 FROM search_logs WHERE id = :id AND user_hash = :user)")
                .param("id", searchLogId)
                .param("user", hasher.hash(userId))
                .query(Boolean.class)
                .single();
        if (!own) {
            throw ApiErrors.notFound("検索ログが見つかりません。");
        }
    }
}
