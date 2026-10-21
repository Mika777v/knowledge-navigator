package com.example.knavi.organization.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.shared.id.Ids;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 組織マスタ（部署・窓口・ユーザー）の管理（E09-T4、PRD A-06）。システム管理者だけが使う。
 *
 * <ul>
 *   <li>更新は楽観ロック（If-Match の版番号が一致しなければ 412）
 *   <li>窓口: 連絡手段（メール・内線・チャット）が1つは必要。部署の代表窓口は1つだけで、新しく代表にすると前の代表は外れる
 *   <li>ユーザー: パスワードは応答にも監査ログにも出さない。自分自身のロール変更・無効化と、最後のシステム管理者を外すことはできない
 *       （管理できる人がいなくなるのを防ぐ）。ロールの変更は、その人が次にログインしたときから効く
 * </ul>
 */
// API の型名（OpenAPI のスキーマ名）が content の ContactView などと重ならないよう、Org を付けている
@Service
@Transactional
public class OrganizationAdminService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;
    private final AuditLog auditLog;
    private final PasswordEncoder passwordEncoder;

    OrganizationAdminService(
            JdbcClient jdbc, AccessPolicy accessPolicy, AuditLog auditLog, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.auditLog = auditLog;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------ 部署

    public record OrgDepartmentView(
            UUID id,
            String code,
            String name,
            String description,
            String parentCode,
            int sortOrder,
            boolean active,
            int rowVersion) {}

    public record DepartmentInput(String name, String description, String parentCode, int sortOrder, boolean active) {}

    @Transactional(readOnly = true)
    public List<OrgDepartmentView> departments(AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        return jdbc.sql(DEPARTMENT_SELECT + " ORDER BY d.sort_order, d.code")
                .query((rs, i) -> department(rs))
                .list();
    }

    public OrgDepartmentView createDepartment(String code, DepartmentInput in, AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        UUID parent = parentId(in.parentCode(), null);
        if (exists("SELECT EXISTS (SELECT 1 FROM departments WHERE code = :v)", code)) {
            throw ApiErrors.conflict("部署 " + code + " は既にあります。");
        }
        UUID id = Ids.newId();
        try {
            jdbc.sql("""
                            INSERT INTO departments (id, code, name, description, parent_id, sort_order, is_active)
                            VALUES (:id, :code, :name, :description, :parent, :sort, :active)
                            """)
                    .param("id", id)
                    .param("code", code)
                    .param("name", in.name().strip())
                    .param("description", blankToNull(in.description()))
                    .param("parent", parent)
                    .param("sort", in.sortOrder())
                    .param("active", in.active())
                    .update();
        } catch (DuplicateKeyException e) {
            throw ApiErrors.conflict("部署 " + code + " は既にあります。");
        }
        auditLog.record(user.id(), "DEPARTMENT_CREATED", "DEPARTMENT", id, Map.of("code", code));
        return findDepartment(code);
    }

    public OrgDepartmentView updateDepartment(
            String code, int expectedRowVersion, DepartmentInput in, AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        OrgDepartmentView current = findDepartment(code);
        UUID parent = parentId(in.parentCode(), current.id());
        int updated = jdbc.sql("""
                        UPDATE departments SET name = :name, description = :description, parent_id = :parent,
                          sort_order = :sort, is_active = :active, row_version = row_version + 1, updated_at = now()
                        WHERE id = :id AND row_version = :version
                        """)
                .param("id", current.id())
                .param("version", expectedRowVersion)
                .param("name", in.name().strip())
                .param("description", blankToNull(in.description()))
                .param("parent", parent)
                .param("sort", in.sortOrder())
                .param("active", in.active())
                .update();
        if (updated == 0) {
            throw ApiErrors.preconditionFailed();
        }
        auditLog.record(user.id(), "DEPARTMENT_UPDATED", "DEPARTMENT", current.id(), Map.of("code", code));
        return findDepartment(code);
    }

    /** 上位の部署。自分自身や自分の下位の部署を上位にすると循環するため受け付けない */
    private UUID parentId(String parentCode, UUID self) {
        if (parentCode == null || parentCode.isBlank()) {
            return null;
        }
        UUID parent = departmentId(parentCode);
        if (self != null) {
            boolean cycle = jdbc.sql("""
                            WITH RECURSIVE ancestors(id) AS (
                              SELECT CAST(:parent AS uuid)
                              UNION SELECT d.parent_id FROM departments d JOIN ancestors a ON d.id = a.id WHERE d.parent_id IS NOT NULL
                            )
                            SELECT EXISTS (SELECT 1 FROM ancestors WHERE id = :self)
                            """)
                    .param("parent", parent)
                    .param("self", self)
                    .query(Boolean.class)
                    .single();
            if (cycle) {
                throw ApiErrors.badRequest("上位の部署に、自分自身や下位の部署は指定できません。");
            }
        }
        return parent;
    }

    private static final String DEPARTMENT_SELECT = """
            SELECT d.id, d.code, d.name, d.description, p.code AS parent_code, d.sort_order, d.is_active, d.row_version
            FROM departments d LEFT JOIN departments p ON p.id = d.parent_id
            """;

    private OrgDepartmentView findDepartment(String code) {
        return jdbc.sql(DEPARTMENT_SELECT + " WHERE d.code = :code")
                .param("code", code)
                .query((rs, i) -> department(rs))
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("部署が見つかりません: " + code));
    }

    private static OrgDepartmentView department(ResultSet rs) throws SQLException {
        return new OrgDepartmentView(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("parent_code"),
                rs.getInt("sort_order"),
                rs.getBoolean("is_active"),
                rs.getInt("row_version"));
    }

    // ------------------------------------------------------------------ 窓口

    public record OrgContactView(
            UUID id,
            String code,
            String departmentCode,
            String name,
            String description,
            String email,
            String phoneExt,
            String chatUrl,
            String officeHours,
            boolean primary,
            int sortOrder,
            int rowVersion) {}

    public record ContactInput(
            String departmentCode,
            String name,
            String description,
            String email,
            String phoneExt,
            String chatUrl,
            String officeHours,
            boolean primary,
            int sortOrder) {}

    @Transactional(readOnly = true)
    public List<OrgContactView> contacts(AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        return jdbc.sql(CONTACT_SELECT + " ORDER BY d.sort_order, c.sort_order, c.code")
                .query((rs, i) -> contact(rs))
                .list();
    }

    public OrgContactView createContact(String code, ContactInput in, AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        UUID department = checkContact(in);
        if (exists("SELECT EXISTS (SELECT 1 FROM contacts WHERE code = :v)", code)) {
            throw ApiErrors.conflict("窓口 " + code + " は既にあります。");
        }
        UUID id = Ids.newId();
        if (in.primary()) {
            clearPrimary(department);
        }
        try {
            jdbc.sql("""
                            INSERT INTO contacts (id, code, department_id, name, description, email, phone_ext, chat_url,
                              office_hours, is_primary, sort_order)
                            VALUES (:id, :code, :dept, :name, :description, :email, :phone, :chat, :hours, :primary, :sort)
                            """)
                    .params(contactParams(in, department))
                    .param("id", id)
                    .param("code", code)
                    .update();
        } catch (DuplicateKeyException e) {
            throw ApiErrors.conflict("窓口 " + code + " は既にあります。");
        }
        auditLog.record(user.id(), "CONTACT_CREATED", "CONTACT", id, Map.of("code", code));
        return findContact(code);
    }

    public OrgContactView updateContact(String code, int expectedRowVersion, ContactInput in, AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        OrgContactView current = findContact(code);
        UUID department = checkContact(in);
        if (in.primary()) {
            clearPrimary(department);
        }
        int updated = jdbc.sql("""
                        UPDATE contacts SET department_id = :dept, name = :name, description = :description, email = :email,
                          phone_ext = :phone, chat_url = :chat, office_hours = :hours, is_primary = :primary,
                          sort_order = :sort, row_version = row_version + 1, updated_at = now()
                        WHERE id = :id AND row_version = :version
                        """)
                .params(contactParams(in, department))
                .param("id", current.id())
                .param("version", expectedRowVersion)
                .update();
        if (updated == 0) {
            throw ApiErrors.preconditionFailed();
        }
        auditLog.record(user.id(), "CONTACT_UPDATED", "CONTACT", current.id(), Map.of("code", code));
        return findContact(code);
    }

    private UUID checkContact(ContactInput in) {
        if (isBlank(in.email()) && isBlank(in.phoneExt()) && isBlank(in.chatUrl())) {
            throw ApiErrors.badRequest("連絡手段（メール・内線・チャットのいずれか）を入力してください。");
        }
        if (!isBlank(in.chatUrl()) && !in.chatUrl().strip().startsWith("https://")) {
            throw ApiErrors.badRequest("チャットの URL は https:// で始めてください。");
        }
        return departmentId(in.departmentCode());
    }

    /** 部署の代表窓口は1つだけ。新しく代表にする窓口のために、今の代表を外す（版番号も上げる） */
    private void clearPrimary(UUID department) {
        jdbc.sql("""
                        UPDATE contacts SET is_primary = false, row_version = row_version + 1, updated_at = now()
                        WHERE department_id = :dept AND is_primary
                        """).param("dept", department).update();
    }

    private Map<String, Object> contactParams(ContactInput in, UUID department) {
        Map<String, Object> params = new HashMap<>();
        params.put("dept", department);
        params.put("name", in.name().strip());
        params.put("description", blankToNull(in.description()));
        params.put("email", blankToNull(in.email()));
        params.put("phone", blankToNull(in.phoneExt()));
        params.put("chat", blankToNull(in.chatUrl()));
        params.put("hours", blankToNull(in.officeHours()));
        params.put("primary", in.primary());
        params.put("sort", in.sortOrder());
        return params;
    }

    private static final String CONTACT_SELECT = """
            SELECT c.id, c.code, d.code AS dept_code, c.name, c.description, c.email, c.phone_ext, c.chat_url,
              c.office_hours, c.is_primary, c.sort_order, c.row_version
            FROM contacts c JOIN departments d ON d.id = c.department_id
            """;

    private OrgContactView findContact(String code) {
        return jdbc.sql(CONTACT_SELECT + " WHERE c.code = :code")
                .param("code", code)
                .query((rs, i) -> contact(rs))
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("窓口が見つかりません: " + code));
    }

    private static OrgContactView contact(ResultSet rs) throws SQLException {
        return new OrgContactView(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("dept_code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("email"),
                rs.getString("phone_ext"),
                rs.getString("chat_url"),
                rs.getString("office_hours"),
                rs.getBoolean("is_primary"),
                rs.getInt("sort_order"),
                rs.getInt("row_version"));
    }

    // ------------------------------------------------------------------ ユーザー

    public record OrgUserView(
            UUID id,
            String email,
            String displayName,
            String departmentCode,
            String role,
            String employmentType,
            LocalDate joinedOn,
            boolean active,
            int rowVersion) {}

    public record UserInput(
            String displayName,
            String departmentCode,
            String role,
            String employmentType,
            LocalDate joinedOn,
            boolean active) {}

    @Transactional(readOnly = true)
    public List<OrgUserView> users(AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        return jdbc.sql(USER_SELECT + " ORDER BY u.email")
                .query((rs, i) -> user(rs))
                .list();
    }

    public OrgUserView createUser(String email, String initialPassword, UserInput in, AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        if (initialPassword == null || initialPassword.length() < MIN_PASSWORD_LENGTH) {
            throw ApiErrors.badRequest("初期パスワードは" + MIN_PASSWORD_LENGTH + "文字以上にしてください。");
        }
        UUID id = Ids.newId();
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        if (exists("SELECT EXISTS (SELECT 1 FROM users WHERE email = :v)", normalizedEmail)) {
            throw ApiErrors.conflict("メールアドレス " + normalizedEmail + " のユーザーは既にいます。");
        }
        try {
            jdbc.sql("""
                            INSERT INTO users (id, email, display_name, department_id, role, employment_type, joined_on,
                              password_hash, is_active)
                            VALUES (:id, :email, :name, :dept, :role, :type, :joined, :hash, :active)
                            """)
                    .params(userParams(in))
                    .param("id", id)
                    .param("email", normalizedEmail)
                    .param("hash", passwordEncoder.encode(initialPassword))
                    .update();
        } catch (DuplicateKeyException e) {
            throw ApiErrors.conflict("メールアドレス " + normalizedEmail + " のユーザーは既にいます。");
        }
        // パスワードは監査ログに残さない
        auditLog.record(user.id(), "USER_CREATED", "USER", id, Map.of("email", normalizedEmail, "role", in.role()));
        return findUser(id);
    }

    public OrgUserView updateUser(UUID id, int expectedRowVersion, UserInput in, AuthenticatedUser user) {
        accessPolicy.checkIsSystemAdmin(user);
        OrgUserView current = findUser(id);
        if (id.equals(user.id()) && (!in.role().equals(current.role()) || !in.active())) {
            throw ApiErrors.badRequest("自分自身のロールの変更や無効化はできません（管理できる人がいなくなるのを防ぐため）。");
        }
        boolean losesAdmin = "SYSTEM_ADMIN".equals(current.role())
                && current.active()
                && (!"SYSTEM_ADMIN".equals(in.role()) || !in.active());
        if (losesAdmin && activeAdmins() <= 1) {
            throw ApiErrors.conflict("最後のシステム管理者は外せません。先に別のシステム管理者を設定してください。");
        }
        int updated = jdbc.sql("""
                        UPDATE users SET display_name = :name, department_id = :dept, role = :role, employment_type = :type,
                          joined_on = :joined, is_active = :active, row_version = row_version + 1, updated_at = now()
                        WHERE id = :id AND row_version = :version
                        """)
                .params(userParams(in))
                .param("id", id)
                .param("version", expectedRowVersion)
                .update();
        if (updated == 0) {
            throw ApiErrors.preconditionFailed();
        }
        Map<String, Object> details = new HashMap<>();
        details.put("email", current.email());
        if (!in.role().equals(current.role())) {
            details.put("role", current.role() + " → " + in.role());
        }
        if (in.active() != current.active()) {
            details.put("active", in.active());
        }
        auditLog.record(user.id(), "USER_UPDATED", "USER", id, details);
        return findUser(id);
    }

    private int activeAdmins() {
        return jdbc.sql("SELECT count(*) FROM users WHERE role = 'SYSTEM_ADMIN' AND is_active")
                .query(Integer.class)
                .single();
    }

    private Map<String, Object> userParams(UserInput in) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", in.displayName().strip());
        params.put("dept", isBlank(in.departmentCode()) ? null : departmentId(in.departmentCode()));
        params.put("role", in.role());
        params.put("type", in.employmentType());
        params.put("joined", in.joinedOn());
        params.put("active", in.active());
        return params;
    }

    private static final String USER_SELECT = """
            SELECT u.id, u.email, u.display_name, d.code AS dept_code, u.role, u.employment_type, u.joined_on,
              u.is_active, u.row_version
            FROM users u LEFT JOIN departments d ON d.id = u.department_id
            """;

    private OrgUserView findUser(UUID id) {
        return jdbc.sql(USER_SELECT + " WHERE u.id = :id")
                .param("id", id)
                .query((rs, i) -> user(rs))
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("ユーザーが見つかりません。"));
    }

    private static OrgUserView user(ResultSet rs) throws SQLException {
        return new OrgUserView(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("dept_code"),
                rs.getString("role"),
                rs.getString("employment_type"),
                rs.getObject("joined_on", LocalDate.class),
                rs.getBoolean("is_active"),
                rs.getInt("row_version"));
    }

    // ------------------------------------------------------------------ 共通

    private UUID departmentId(String code) {
        return jdbc.sql("SELECT id FROM departments WHERE code = :code")
                .param("code", code)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> ApiErrors.badRequest("部署 " + code + " が存在しません。"));
    }

    /** 重複を先に確かめる（一意制約の違反で失敗させるとトランザクションが使えなくなるため。競合時は一意制約が最後に守る） */
    private boolean exists(String sql, String value) {
        return jdbc.sql(sql).param("v", value).query(Boolean.class).single();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }
}
