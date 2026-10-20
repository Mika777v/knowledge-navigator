package com.example.knavi.audit;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 監査ログを記録する公開 API。呼び出し元のトランザクションに参加するため、業務処理がロールバックされれば記録も残らない。
 *
 * <p>audit_logs は追記専用（更新・削除は DB のトリガーで拒否される）。
 */
@Component
public class AuditLog {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditLog(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * @param actorId 操作したユーザー
     * @param action 操作（例: DOCUMENT_PUBLISHED）
     * @param resourceType 対象の種類（例: DOCUMENT）
     * @param resourceId 対象の ID
     * @param details 変更後の主な値（任意）
     */
    public void record(UUID actorId, String action, String resourceType, UUID resourceId, Map<String, ?> details) {
        jdbc.sql("""
                        INSERT INTO audit_logs (actor_id, action, resource_type, resource_id, after_data)
                        VALUES (:actor, :action, :type, :id, CAST(:after AS jsonb))
                        """)
                .param("actor", actorId)
                .param("action", action)
                .param("type", resourceType)
                .param("id", resourceId)
                .param("after", details == null ? null : json.writeValueAsString(details))
                .update();
    }
}
