package com.example.knavi.assistant.application;

import com.example.knavi.assistant.ConversationRecorder;
import com.example.knavi.assistant.application.ConversationViews.ConversationDetail;
import com.example.knavi.assistant.application.ConversationViews.ConversationEvidence;
import com.example.knavi.assistant.application.ConversationViews.ConversationMessage;
import com.example.knavi.assistant.application.ConversationViews.ConversationSummary;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.security.SqlPredicate;
import com.example.knavi.shared.web.ApiErrors;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 会話の履歴（ADR-0014 の決定 3・E15-T1）。
 *
 * <ul>
 *   <li>どの操作も、ログイン中の利用者の会話だけを対象にする。他の人の会話は「無い」と同じ扱い（404）にする
 *   <li>保持期間は最後に話した日時から数える。過ぎた会話は、毎日の削除を待たずに見えなくする
 *   <li>根拠は条の ID だけを残し、開いた時点の権限で読み直す（ADR-0004）。見られない根拠は数だけ返す
 *   <li>質問・回答文はログに出さない
 * </ul>
 */
@Service
public class ConversationService implements ConversationRecorder {

    public static final int MAX_TITLE_LENGTH = 200;
    /** 一覧に出す会話の数 */
    static final int MAX_LIST = 50;

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;
    private final Clock clock;
    private final Duration retention;
    private final TransactionTemplate newTransaction;

    ConversationService(
            JdbcClient jdbc,
            AccessPolicy accessPolicy,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${knavi.assistant.retention-days:90}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalStateException("knavi.assistant.retention-days は1以上にしてください。");
        }
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
        this.retention = Duration.ofDays(retentionDays);
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** この日時より前に最後に話した会話は、保持期間を過ぎている */
    Instant cutoff() {
        return clock.instant().minus(retention);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID conversationId, UUID userId) {
        return conversationId != null && findOwn(conversationId, userId).isPresent();
    }

    /** 回答は返し終わっているため、記録は別のトランザクションで書き、失敗はここで受け止める */
    @Override
    public Optional<UUID> record(UUID userId, UUID conversationId, String question, Reply reply) {
        try {
            return Optional.ofNullable(
                    newTransaction.execute(status -> insert(userId, conversationId, question, reply)));
        } catch (RuntimeException e) {
            log.warn("会話の履歴を記録できませんでした: {}", e.toString());
            return Optional.empty();
        }
    }

    private UUID insert(UUID userId, UUID conversationId, String question, Reply reply) {
        Instant now = clock.instant();
        UUID sessionId;
        if (conversationId == null) {
            sessionId = jdbc.sql("INSERT INTO chat_sessions (user_id, title, last_message_at, created_at)"
                            + " VALUES (:user, :title, :now, :now) RETURNING id")
                    .param("user", userId)
                    .param("title", truncate(question, MAX_TITLE_LENGTH))
                    .param("now", Timestamp.from(now))
                    .query(UUID.class)
                    .single();
        } else {
            // 確かめてから話し始めるまでの間に消えた・期限を過ぎた会話には足さない
            int updated = jdbc.sql("UPDATE chat_sessions SET last_message_at = :now"
                            + " WHERE id = :id AND user_id = :user AND last_message_at >= :cutoff")
                    .param("now", Timestamp.from(now))
                    .param("id", conversationId)
                    .param("user", userId)
                    .param("cutoff", Timestamp.from(cutoff()))
                    .update();
            if (updated == 0) {
                throw new IllegalStateException("続きを記録する会話がありません");
            }
            sessionId = conversationId;
        }
        int seq = jdbc.sql("SELECT COALESCE(MAX(seq), 0) FROM chat_messages WHERE session_id = :id")
                .param("id", sessionId)
                .query(Integer.class)
                .single();
        insertMessage(sessionId, seq + 1, "USER", question, null, null, null, now);
        UUID answerId = insertMessage(
                sessionId,
                seq + 2,
                "ASSISTANT",
                reply.answerable() ? reply.answer() : "",
                reply.answerable(),
                reply.unanswerableReason(),
                reply.model(),
                now);
        for (Citation citation : reply.citations()) {
            jdbc.sql("INSERT INTO chat_citations (message_id, ref, section_id) VALUES (:m, :ref, :s)"
                            + " ON CONFLICT DO NOTHING")
                    .param("m", answerId)
                    .param("ref", citation.ref())
                    .param("s", citation.sectionId())
                    .update();
        }
        return sessionId;
    }

    private UUID insertMessage(
            UUID sessionId,
            int seq,
            String role,
            String content,
            Boolean answerable,
            String reason,
            String model,
            Instant now) {
        return jdbc.sql("INSERT INTO chat_messages"
                        + " (session_id, seq, role, content, answerable, unanswerable_reason, model, created_at)"
                        + " VALUES (:s, :seq, :role, :content, :answerable, :reason, :model, :now) RETURNING id")
                .param("s", sessionId)
                .param("seq", seq)
                .param("role", role)
                .param("content", content == null ? "" : content)
                .param("answerable", answerable)
                .param("reason", reason)
                .param("model", model)
                .param("now", Timestamp.from(now))
                .query(UUID.class)
                .single();
    }

    /** 自分の会話（新しい順） */
    @Transactional(readOnly = true)
    public List<ConversationSummary> list(AuthenticatedUser user) {
        return jdbc.sql("SELECT id, title, last_message_at FROM chat_sessions"
                        + " WHERE user_id = :user AND last_message_at >= :cutoff"
                        + " ORDER BY last_message_at DESC, id LIMIT :limit")
                .param("user", user.id())
                .param("cutoff", Timestamp.from(cutoff()))
                .param("limit", MAX_LIST)
                .query((rs, i) -> new ConversationSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getTimestamp("last_message_at").toInstant()))
                .list();
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(UUID conversationId, AuthenticatedUser user) {
        ConversationSummary summary =
                findOwn(conversationId, user.id()).orElseThrow(() -> ApiErrors.notFound("会話が見つかりません。"));
        record MessageRow(
                UUID id,
                String role,
                String content,
                Boolean answerable,
                String reason,
                String model,
                Instant createdAt) {}
        List<MessageRow> rows = jdbc.sql("SELECT id, role, content, answerable, unanswerable_reason, model, created_at"
                        + " FROM chat_messages WHERE session_id = :id ORDER BY seq")
                .param("id", conversationId)
                .query((rs, i) -> new MessageRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getObject("answerable", Boolean.class),
                        rs.getString("unanswerable_reason"),
                        rs.getString("model"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
        record CitationRow(UUID messageId, int ref, UUID sectionId) {}
        List<CitationRow> citations = jdbc.sql("SELECT c.message_id, c.ref, c.section_id FROM chat_citations c"
                        + " JOIN chat_messages m ON m.id = c.message_id WHERE m.session_id = :id ORDER BY c.ref")
                .param("id", conversationId)
                .query((rs, i) -> new CitationRow(
                        rs.getObject("message_id", UUID.class),
                        rs.getInt("ref"),
                        rs.getObject("section_id", UUID.class)))
                .list();
        Map<UUID, SectionRow> visible = visibleSections(
                citations.stream()
                        .map(CitationRow::sectionId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()),
                AccessScope.of(user));
        Map<UUID, List<CitationRow>> byMessage =
                citations.stream().collect(Collectors.groupingBy(CitationRow::messageId));
        List<ConversationMessage> messages = new ArrayList<>();
        for (MessageRow row : rows) {
            List<ConversationEvidence> evidence = new ArrayList<>();
            int hidden = 0;
            for (CitationRow citation : byMessage.getOrDefault(row.id(), List.of())) {
                SectionRow section = citation.sectionId() == null ? null : visible.get(citation.sectionId());
                if (section == null) {
                    hidden++;
                } else {
                    evidence.add(section.toEvidence(citation.ref()));
                }
            }
            messages.add(new ConversationMessage(
                    row.role(),
                    row.content(),
                    row.answerable(),
                    row.reason(),
                    row.model(),
                    row.createdAt(),
                    evidence,
                    hidden));
        }
        return new ConversationDetail(
                summary.id(),
                summary.title(),
                summary.lastMessageAt(),
                summary.lastMessageAt().plus(retention),
                messages);
    }

    @Transactional
    public void delete(UUID conversationId, AuthenticatedUser user) {
        int deleted = jdbc.sql("DELETE FROM chat_sessions WHERE id = :id AND user_id = :user")
                .param("id", conversationId)
                .param("user", user.id())
                .update();
        if (deleted == 0) {
            throw ApiErrors.notFound("会話が見つかりません。");
        }
    }

    /** 保持期間を過ぎた会話を削除し、削除した件数を返す（メッセージと根拠も一緒に消える） */
    @Transactional
    public int purge() {
        return jdbc.sql("DELETE FROM chat_sessions WHERE last_message_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff()))
                .update();
    }

    long retentionDays() {
        return retention.toDays();
    }

    private Optional<ConversationSummary> findOwn(UUID conversationId, UUID userId) {
        return jdbc.sql("SELECT id, title, last_message_at FROM chat_sessions"
                        + " WHERE id = :id AND user_id = :user AND last_message_at >= :cutoff")
                .param("id", conversationId)
                .param("user", userId)
                .param("cutoff", Timestamp.from(cutoff()))
                .query((rs, i) -> new ConversationSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getTimestamp("last_message_at").toInstant()))
                .optional();
    }

    private record SectionRow(
            UUID sectionId,
            UUID documentId,
            String documentTitle,
            String versionLabel,
            boolean current,
            String articleNo,
            String heading,
            Integer pageFrom,
            Integer pageTo) {

        ConversationEvidence toEvidence(int ref) {
            return new ConversationEvidence(
                    ref,
                    documentId,
                    documentTitle,
                    versionLabel,
                    current,
                    sectionId,
                    articleNo,
                    heading,
                    pageFrom,
                    pageTo);
        }
    }

    /** 条を、条の画面と同じ権限（VIEW）で読み直す。引用した版が古くても、文書が見られれば出す */
    private Map<UUID, SectionRow> visibleSections(Set<UUID> sectionIds, AccessScope scope) {
        if (sectionIds.isEmpty()) {
            return Map.of();
        }
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.VIEW);
        return jdbc
                .sql("SELECT s.id, d.id AS document_id, d.title, sv.version_label,"
                        + " (sv.id = d.current_version_id) AS is_current, s.article_no, s.heading, s.page_from, s.page_to"
                        + " FROM sections s JOIN document_versions sv ON sv.id = s.document_version_id"
                        + " JOIN documents d ON d.id = sv.document_id WHERE s.id IN (:ids) AND "
                        + visible.sql())
                .params(visible.params())
                .param("ids", sectionIds)
                .query((rs, i) -> new SectionRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("title"),
                        rs.getString("version_label"),
                        rs.getBoolean("is_current"),
                        rs.getString("article_no"),
                        rs.getString("heading"),
                        rs.getObject("page_from", Integer.class),
                        rs.getObject("page_to", Integer.class)))
                .list()
                .stream()
                .collect(Collectors.toMap(SectionRow::sectionId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static String truncate(String text, int max) {
        String value = text == null ? "" : text.strip();
        return value.length() <= max ? value : value.substring(0, max);
    }
}
