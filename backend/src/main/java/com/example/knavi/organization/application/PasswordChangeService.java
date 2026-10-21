package com.example.knavi.organization.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.shared.web.ApiErrors;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログイン中のユーザーが自分のパスワードを変える。今のパスワードを確かめてから変える。
 * パスワード（新旧とも）は監査ログにも出さない
 */
@Service
public class PasswordChangeService {

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuditLog auditLog;

    PasswordChangeService(JdbcClient jdbc, PasswordEncoder passwordEncoder, AuditLog auditLog) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.auditLog = auditLog;
    }

    /**
     * パスワードを変える。今のパスワードが違うときは何も変えずに false を返す
     * （ログインと同じく、続けて間違えたらロックするため、呼び出し側で失敗を数える）
     */
    @Transactional
    public boolean change(UUID userId, String currentPassword, String newPassword) {
        String hash = jdbc.sql("SELECT password_hash FROM users WHERE id = :id AND is_active")
                .param("id", userId)
                .query(String.class)
                .optional()
                .orElse(null);
        if (hash == null) {
            throw ApiErrors.badRequest("このアカウントはパスワードでログインしていないため、パスワードを変更できません。");
        }
        if (!passwordEncoder.matches(currentPassword, hash)) {
            return false;
        }
        if (newPassword.length() < OrganizationAdminService.MIN_PASSWORD_LENGTH) {
            throw ApiErrors.badRequest("新しいパスワードは" + OrganizationAdminService.MIN_PASSWORD_LENGTH + "文字以上にしてください。");
        }
        if (newPassword.equals(currentPassword)) {
            throw ApiErrors.badRequest("今と同じパスワードには変更できません。");
        }
        jdbc.sql("""
                        UPDATE users SET password_hash = :hash, row_version = row_version + 1, updated_at = now()
                        WHERE id = :id
                        """)
                .param("hash", passwordEncoder.encode(newPassword))
                .param("id", userId)
                .update();
        auditLog.record(userId, "USER_PASSWORD_CHANGED", "USER", userId, null);
        return true;
    }
}
