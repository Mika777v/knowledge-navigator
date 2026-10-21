package com.example.knavi.shared.security;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 認可の判断を1か所に集約する（ADR-0004）。
 *
 * <ul>
 *   <li>編集: 所管部署に所属する CONTENT_EDITOR だけ（SYSTEM_ADMIN は職務分離のため不可）
 *   <li>閲覧: {@link #readPredicate} が返す SQL 条件で、検索・一覧・詳細のすべてを絞り込む（プレフィルタ）。
 *       取得してから除外する（ポストフィルタ）と、件数や抜粋から見えてはいけないものの存在が漏れるため
 * </ul>
 *
 * <p>閲覧のルール（docs/PRD_KNOWLEDGE_NAVIGATOR.md §7）
 *
 * <table>
 *   <tr><th>公開範囲</th><th>見える人</th></tr>
 *   <tr><td>INTERNAL</td><td>ログインユーザー全員</td></tr>
 *   <tr><td>MANAGERS</td><td>MANAGER、所管部署の CONTENT_EDITOR</td></tr>
 *   <tr><td>RESTRICTED</td><td>access_rules で許可された部署・ロール・ユーザー、所管部署の CONTENT_EDITOR</td></tr>
 *   <tr><td>CONFIDENTIAL</td><td>access_rules で個別に許可されたユーザー、所管部署の CONTENT_EDITOR。
 *       ただし検索（{@link Purpose#SEARCH}）では誰にも出さない</td></tr>
 * </table>
 *
 * <p>どの公開範囲でも、一般利用者向けには公開中（PUBLISHED）のものだけを返す。
 */
@Component
public class AccessPolicy {

    static final String CONTENT_EDITOR = "CONTENT_EDITOR";
    static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    static final String MANAGER = "MANAGER";

    /** 閲覧条件の用途 */
    public enum Purpose {
        /** 詳細の表示・一覧 */
        VIEW,
        /** 検索・サジェスト・推薦（CONFIDENTIAL は対象外） */
        SEARCH
    }

    private static final Pattern ALIAS = Pattern.compile("[a-z][a-z0-9_]{0,29}");
    private static final Map<String, String> RESOURCE_TYPES =
            Map.of("documents", "DOCUMENT", "programs", "PROGRAM", "faqs", "FAQ");

    /**
     * 閲覧できる行だけに絞る SQL 条件を返す。対象テーブルには visibility / status / owner_department_id / id 列が必要。
     *
     * @param scope ログインユーザー
     * @param table 対象テーブル（documents / programs / faqs）
     * @param alias SQL 内でのテーブルの別名
     * @param purpose 用途
     */
    public SqlPredicate readPredicate(AccessScope scope, String table, String alias, Purpose purpose) {
        String resourceType = RESOURCE_TYPES.get(table);
        if (resourceType == null || !ALIAS.matcher(alias).matches()) {
            // SQL に埋め込むため、許可した値以外は受け付けない
            throw new IllegalArgumentException("unsupported table or alias: " + table + " " + alias);
        }
        String p = "acc_" + alias + "_";
        String ownerEditor = "(:" + p + "editor AND " + alias + ".owner_department_id = :" + p + "dept)";
        String rule = "EXISTS (SELECT 1 FROM access_rules r WHERE r.resource_type = '" + resourceType
                + "' AND r.resource_id = " + alias + ".id AND (%s))";
        String anyPrincipal = "(r.principal_type = 'DEPARTMENT' AND r.principal_id = :" + p + "dept_text)"
                + " OR (r.principal_type = 'ROLE' AND r.principal_id = :" + p + "role)"
                + " OR (r.principal_type = 'USER' AND r.principal_id = :" + p + "user_text)";
        String userOnly = "r.principal_type = 'USER' AND r.principal_id = :" + p + "user_text";

        StringBuilder sql = new StringBuilder("(")
                .append(alias)
                .append(".status = 'PUBLISHED' AND (")
                .append(alias)
                .append(".visibility = 'INTERNAL'")
                .append(" OR (")
                .append(alias)
                .append(".visibility = 'MANAGERS' AND (:")
                .append(p)
                .append("manager OR ")
                .append(ownerEditor)
                .append("))")
                .append(" OR (")
                .append(alias)
                .append(".visibility = 'RESTRICTED' AND (")
                .append(ownerEditor)
                .append(" OR ")
                .append(rule.formatted(anyPrincipal))
                .append("))");
        if (purpose == Purpose.VIEW) {
            sql.append(" OR (")
                    .append(alias)
                    .append(".visibility = 'CONFIDENTIAL' AND (")
                    .append(ownerEditor)
                    .append(" OR ")
                    .append(rule.formatted(userOnly))
                    .append("))");
        }
        sql.append("))");

        // 未所属のユーザーは、どの部署とも一致しない値（全ビット0の UUID）で照合する
        UUID dept = scope.departmentId() == null ? new UUID(0, 0) : scope.departmentId();
        Map<String, Object> params = Map.of(
                p + "editor", scope.isContentEditor(),
                p + "manager", scope.isManager(),
                p + "dept", dept,
                p + "dept_text", dept.toString(),
                p + "role", scope.role(),
                p + "user_text", scope.userId().toString());
        return new SqlPredicate(sql.toString(), params);
    }

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

    /** 検索の分析（ゼロヒット・人気クエリ・フィードバック）を見られるのは CONTENT_EDITOR と SYSTEM_ADMIN */
    public void checkCanViewAnalytics(AuthenticatedUser user) {
        if (!CONTENT_EDITOR.equals(user.role()) && !SYSTEM_ADMIN.equals(user.role())) {
            throw new AccessDeniedException("検索の分析を見られるのはコンテンツ担当者とシステム管理者だけです。");
        }
    }

    /** 管理画面のコンテンツ機能を使えるか */
    public void checkIsContentEditor(AuthenticatedUser user) {
        if (!CONTENT_EDITOR.equals(user.role())) {
            throw new AccessDeniedException("コンテンツ担当者だけが利用できます。");
        }
    }

    /** 監査ログを見られるのは SYSTEM_ADMIN だけ */
    public void checkIsSystemAdmin(AuthenticatedUser user) {
        if (!SYSTEM_ADMIN.equals(user.role())) {
            throw new AccessDeniedException("システム管理者だけが利用できます。");
        }
    }
}
