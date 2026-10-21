package com.example.knavi.shared.security;

import java.util.UUID;

/**
 * 閲覧範囲の判定に使う、ログインユーザーの属性。ADR-0004
 *
 * @param userId ユーザー ID
 * @param departmentId 所属部署（未所属なら null）
 * @param role ロール名
 */
public record AccessScope(UUID userId, UUID departmentId, String role) {

    public static AccessScope of(AuthenticatedUser user) {
        return new AccessScope(user.id(), user.departmentId(), user.role());
    }

    boolean isManager() {
        return AccessPolicy.MANAGER.equals(role);
    }

    boolean isContentEditor() {
        return AccessPolicy.CONTENT_EDITOR.equals(role);
    }
}
