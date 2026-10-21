package com.example.knavi.organization.infrastructure;

import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.security.SessionPrincipalChecker;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * セッションのユーザーが、今も有効で、ロールと所属が変わっていないかを users テーブルで確かめる。
 * 表示名などの変更ではログアウトさせない（最新の値は /me で読み直す）
 */
@Component
class DatabaseSessionPrincipalChecker implements SessionPrincipalChecker {

    private final JdbcClient jdbc;

    DatabaseSessionPrincipalChecker(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isCurrent(AuthenticatedUser user) {
        return jdbc.sql("""
                        SELECT 1 FROM users
                        WHERE id = :id AND is_active AND role = :role
                          AND department_id IS NOT DISTINCT FROM CAST(:dept AS uuid)
                        """)
                .param("id", user.id())
                .param("role", user.role())
                .param("dept", user.departmentId())
                .query(Integer.class)
                .optional()
                .isPresent();
    }
}
