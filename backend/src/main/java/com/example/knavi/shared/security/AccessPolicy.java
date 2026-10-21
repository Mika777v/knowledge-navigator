package com.example.knavi.shared.security;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 認可の判断を1か所に集約する（ADR-0004）。
 *
 * <p>現時点ではコンテンツの編集権限のみ。閲覧範囲（公開範囲による検索のプレフィルタ）は #34 で追加する。
 */
@Component
public class AccessPolicy {

    static final String CONTENT_EDITOR = "CONTENT_EDITOR";
    static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    /** コンテンツを編集できるのは、所管部署に所属する CONTENT_EDITOR だけ（SYSTEM_ADMIN は職務分離のため不可） */
    public boolean canEditContent(AuthenticatedUser user, UUID ownerDepartmentId) {
        return CONTENT_EDITOR.equals(user.role())
                && user.departmentId() != null
                && user.departmentId().equals(ownerDepartmentId);
    }

    public void checkCanEditContent(AuthenticatedUser user, UUID ownerDepartmentId) {
        if (!canEditContent(user, ownerDepartmentId)) {
            throw new AccessDeniedException("所管部署のコンテンツ担当者だけが編集できます。");
        }
    }

    /** ナビゲーション辞書（キーワード・類義語）を管理できるのは CONTENT_EDITOR と SYSTEM_ADMIN */
    public void checkCanManageDictionary(AuthenticatedUser user) {
        if (!CONTENT_EDITOR.equals(user.role()) && !SYSTEM_ADMIN.equals(user.role())) {
            throw new AccessDeniedException("辞書を管理できるのはコンテンツ担当者とシステム管理者だけです。");
        }
    }

    /** 管理画面のコンテンツ機能を使えるか */
    public void checkIsContentEditor(AuthenticatedUser user) {
        if (!CONTENT_EDITOR.equals(user.role())) {
            throw new AccessDeniedException("コンテンツ担当者だけが利用できます。");
        }
    }
}
