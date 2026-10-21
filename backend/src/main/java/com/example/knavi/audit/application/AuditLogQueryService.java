package com.example.knavi.audit.application;

import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 監査ログの閲覧（システム管理者だけ）。新しい順に返し、続きはカーソル（最後に返した ID）で取得する。
 * 件数の多いログでも同じ速さで続きを取れるよう、OFFSET ではなく ID より前を条件にする。
 */
@Service
@Transactional(readOnly = true)
public class AuditLogQueryService {

    static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    public static final int MAX_SIZE = 200;

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;
    private final JsonMapper json;

    AuditLogQueryService(JdbcClient jdbc, AccessPolicy accessPolicy, JsonMapper json) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.json = json;
    }

    /**
     * @param from 開始日（日本時間、含む）
     * @param to 終了日（日本時間、含む）
     * @param action 操作（例: DOCUMENT_PUBLISHED）
     * @param resourceType 対象の種類（例: DOCUMENT）
     * @param resourceId 対象の ID
     * @param actorId 操作したユーザー
     */
    public record Filter(
            LocalDate from, LocalDate to, String action, String resourceType, UUID resourceId, UUID actorId) {}

    public record Actor(UUID id, String displayName, String email) {}

    /** @param details 変更後の主な値 */
    public record Entry(
            long id,
            Instant occurredAt,
            Actor actor,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> details) {}

    /** @param nextCursor 続きがあるときに、次のページを取得するための値（cursor に渡す） */
    public record Page(List<Entry> items, Long nextCursor) {}

    public Page search(Filter filter, Long cursor, int size, AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ApiErrors.badRequest("開始日は終了日以前にしてください。");
        }
        List<Entry> rows = jdbc.sql("""
                        SELECT a.id, a.occurred_at, a.actor_id, u.display_name, u.email, a.action, a.resource_type,
                          a.resource_id, a.after_data::text AS details
                        FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_id
                        WHERE (CAST(:cursor AS bigint) IS NULL OR a.id < :cursor)
                          AND (CAST(:start AS timestamptz) IS NULL OR a.occurred_at >= :start)
                          AND (CAST(:end AS timestamptz) IS NULL OR a.occurred_at < :end)
                          AND (CAST(:action AS text) IS NULL OR a.action = :action)
                          AND (CAST(:type AS text) IS NULL OR a.resource_type = :type)
                          AND (CAST(:resource AS uuid) IS NULL OR a.resource_id = :resource)
                          AND (CAST(:actor AS uuid) IS NULL OR a.actor_id = :actor)
                        ORDER BY a.id DESC LIMIT :limit
                        """)
                .param("cursor", cursor)
                .param("start", filter.from() == null ? null : Timestamp.from(startOf(filter.from())))
                .param(
                        "end",
                        filter.to() == null
                                ? null
                                : Timestamp.from(startOf(filter.to().plusDays(1))))
                .param("action", blankToNull(filter.action()))
                .param("type", blankToNull(filter.resourceType()))
                .param("resource", filter.resourceId())
                .param("actor", filter.actorId())
                // 続きがあるかを知るため1件多く取る
                .param("limit", size + 1)
                .query((rs, i) -> {
                    UUID actorId = rs.getObject("actor_id", UUID.class);
                    return new Entry(
                            rs.getLong("id"),
                            rs.getTimestamp("occurred_at").toInstant(),
                            actorId == null
                                    ? null
                                    : new Actor(actorId, rs.getString("display_name"), rs.getString("email")),
                            rs.getString("action"),
                            rs.getString("resource_type"),
                            rs.getObject("resource_id", UUID.class),
                            details(rs.getString("details")));
                })
                .list();
        boolean hasMore = rows.size() > size;
        List<Entry> items = hasMore ? rows.subList(0, size) : rows;
        return new Page(List.copyOf(items), hasMore ? items.getLast().id() : null);
    }

    private Map<String, Object> details(String text) {
        return text == null ? Map.of() : json.readValue(text, new TypeReference<Map<String, Object>>() {});
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
