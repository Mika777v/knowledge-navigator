package com.example.knavi.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 組織マスタ（部署・窓口・ユーザー）の管理 API（E09-T4） */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminOrganizationApiWithSeedTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SeedLoader loader;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    PasswordEncoder passwordEncoder;

    KnaviUserDetails admin;

    @BeforeEach
    void setUp() {
        loader.load(Path.of("..", "data", "seed"), "demo-password-for-test");
        admin = demoUser("watanabe@example.com");
    }

    @Test
    void onlySystemAdminsCanManageTheOrganization() throws Exception {
        for (String email : List.of("sato@example.com", "tanaka@example.com", "takahashi@example.com")) {
            for (String path : List.of("departments", "contacts", "users")) {
                mockMvc.perform(get("/api/v1/admin/" + path).with(user(demoUser(email))))
                        .andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(get("/api/v1/admin/users").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void departmentsCanBeCreatedAndMovedButNotIntoTheirOwnSubtree() throws Exception {
        send(post("/api/v1/admin/departments"), Map.of("code", "HR-TALENT", "content", department("人材開発課", "HR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentCode").value("HR"));
        send(post("/api/v1/admin/departments"), Map.of("code", "HR", "content", department("重複", null)))
                .andExpect(status().isConflict());

        String hr = etag("/api/v1/admin/departments", "HR");
        send(put("/api/v1/admin/departments/HR").header("If-Match", hr), department("人事部", "HR-TALENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("上位の部署に、自分自身や下位の部署は指定できません。"));
        send(put("/api/v1/admin/departments/HR").header("If-Match", hr), department("人事・総務部", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("人事・総務部"));
        send(put("/api/v1/admin/departments/HR").header("If-Match", hr), department("古い版", null))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void contactsNeedAChannelAndOnlyOnePrimaryPerDepartment() throws Exception {
        Map<String, Object> noChannel = contact("HR", "連絡先なし", null, false);
        send(post("/api/v1/admin/contacts"), Map.of("code", "HR-NONE", "content", noChannel))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("連絡手段（メール・内線・チャットのいずれか）を入力してください。"));
        Map<String, Object> insecureChat = new HashMap<>(contact("HR", "チャット", null, false));
        insecureChat.put("chatUrl", "http://chat.example.com");
        send(post("/api/v1/admin/contacts"), Map.of("code", "HR-CHAT", "content", insecureChat))
                .andExpect(status().isBadRequest());

        String previousPrimary = jdbc.sql("""
                        SELECT c.code FROM contacts c JOIN departments d ON d.id = c.department_id
                        WHERE d.code = 'HR' AND c.is_primary
                        """).query(String.class).single();
        send(
                        post("/api/v1/admin/contacts"),
                        Map.of("code", "HR-NEW-DESK", "content", contact("HR", "人事部 新窓口", "2000", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary").value(true));

        List<String> primaries = jdbc.sql("""
                        SELECT c.code FROM contacts c JOIN departments d ON d.id = c.department_id
                        WHERE d.code = 'HR' AND c.is_primary
                        """).query(String.class).list();
        assertThat(primaries).containsExactly("HR-NEW-DESK").doesNotContain(previousPrimary);
    }

    @Test
    void usersAreCreatedWithoutExposingPasswordsAndRolesChangeWithAudit() throws Exception {
        String body = send(
                        post("/api/v1/admin/users"),
                        Map.of(
                                "email", "New.Hire@Example.com",
                                "initialPassword", "initial-pass-2026",
                                "content", userContent("新人 太郎", "SALES-EAST", "EMPLOYEE", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.hire@example.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("initial-pass-2026").doesNotContain("password");
        String hash = jdbc.sql("SELECT password_hash FROM users WHERE email = 'new.hire@example.com'")
                .query(String.class)
                .single();
        assertThat(passwordEncoder.matches("initial-pass-2026", hash)).isTrue();

        send(
                        post("/api/v1/admin/users"),
                        Map.of(
                                "email", "new.hire@example.com",
                                "initialPassword", "initial-pass-2026",
                                "content", userContent("重複", null, "EMPLOYEE", true)))
                .andExpect(status().isConflict());
        send(
                        post("/api/v1/admin/users"),
                        Map.of(
                                "email", "short@example.com",
                                "initialPassword", "short",
                                "content", userContent("短い", null, "EMPLOYEE", true)))
                .andExpect(status().isBadRequest());

        String id = JsonPath.read(body, "$.id");
        send(
                        put("/api/v1/admin/users/" + id).header("If-Match", "\"0\""),
                        userContent("新人 太郎", "SALES-EAST", "MANAGER", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
        String audit = jdbc.sql("SELECT after_data::text FROM audit_logs WHERE action = 'USER_UPDATED'")
                .query(String.class)
                .single();
        assertThat(audit).contains("EMPLOYEE → MANAGER");
        assertThat(jdbc.sql(
                                "SELECT string_agg(coalesce(after_data::text, ''), ' ') FROM audit_logs WHERE resource_type = 'USER'")
                        .query(String.class)
                        .single())
                .doesNotContain("initial-pass-2026");
    }

    @Test
    void adminsCannotLockThemselvesOutOrRemoveTheLastAdmin() throws Exception {
        String self = admin.id().toString();
        String version = etagOfUser(self);
        send(
                        put("/api/v1/admin/users/" + self).header("If-Match", version),
                        userContent("渡辺 翔", "IT", "EMPLOYEE", true))
                .andExpect(status().isBadRequest());
        send(
                        put("/api/v1/admin/users/" + self).header("If-Match", version),
                        userContent("渡辺 翔", "IT", "SYSTEM_ADMIN", false))
                .andExpect(status().isBadRequest());

        // 2人目の管理者を作り、その人が渡辺さんを無効にする（2人いるので外せる）
        String other = JsonPath.read(
                send(
                                post("/api/v1/admin/users"),
                                Map.of(
                                        "email", "admin2@example.com",
                                        "initialPassword", "initial-pass-2026",
                                        "content", userContent("管理者 二郎", "IT", "SYSTEM_ADMIN", true)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        KnaviUserDetails admin2 = new KnaviUserDetails(
                UUID.fromString(other), admin.departmentId(), "admin2@example.com", null, "SYSTEM_ADMIN", true);
        mockMvc.perform(put("/api/v1/admin/users/" + self)
                        .header("If-Match", version)
                        .with(user(admin2))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(userContent("渡辺 翔", "IT", "SYSTEM_ADMIN", false))))
                .andExpect(status().isOk());

        // 無効にされた渡辺さんのセッションが残っていても、最後の管理者（二郎さん）は外せない
        send(
                        put("/api/v1/admin/users/" + other).header("If-Match", "\"0\""),
                        userContent("管理者 二郎", "IT", "EMPLOYEE", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("最後のシステム管理者は外せません。先に別のシステム管理者を設定してください。"));
    }

    // ------------------------------------------------------------------ 補助

    private Map<String, Object> department(String name, String parentCode) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("parentCode", parentCode);
        m.put("sortOrder", 10);
        m.put("active", true);
        return m;
    }

    private Map<String, Object> contact(String dept, String name, String phone, boolean primary) {
        Map<String, Object> m = new HashMap<>();
        m.put("departmentCode", dept);
        m.put("name", name);
        m.put("phoneExt", phone);
        m.put("primary", primary);
        m.put("sortOrder", 0);
        return m;
    }

    private Map<String, Object> userContent(String name, String dept, String role, boolean active) {
        Map<String, Object> m = new HashMap<>();
        m.put("displayName", name);
        m.put("departmentCode", dept);
        m.put("role", role);
        m.put("employmentType", "REGULAR");
        m.put("active", active);
        return m;
    }

    private String etag(String listPath, String code) throws Exception {
        String body = mockMvc.perform(get(listPath).with(user(admin)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> versions = JsonPath.read(body, "$[?(@.code == '" + code + "')].rowVersion");
        return "\"" + versions.getFirst() + "\"";
    }

    private String etagOfUser(String id) throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/users").with(user(admin)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> versions = JsonPath.read(body, "$[?(@.id == '" + id + "')].rowVersion");
        return "\"" + versions.getFirst() + "\"";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(request.with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private KnaviUserDetails demoUser(String email) {
        return jdbc.sql("SELECT id, department_id, role FROM users WHERE email = :e")
                .param("e", email)
                .query((rs, i) -> new KnaviUserDetails(
                        rs.getObject("id", UUID.class),
                        rs.getObject("department_id", UUID.class),
                        email,
                        null,
                        rs.getString("role"),
                        true))
                .single();
    }
}
