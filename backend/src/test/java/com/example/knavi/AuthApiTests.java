package com.example.knavi;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthApiTests {

    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        var dept = jdbc.sql("INSERT INTO departments (code, name) VALUES ('SALES', '営業部') RETURNING id")
                .query(java.util.UUID.class)
                .single();
        insertUser("sato@example.com", "佐藤 太郎", dept, true);
        insertUser("retired@example.com", "退職 済", dept, false);
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(login("sato@example.com", PASSWORD)).andExpect(status().isForbidden());
    }

    @Test
    void loginSucceedsAndMeReturnsTheUser() throws Exception {
        var session = new MockHttpSession();

        mockMvc.perform(login("sato@example.com", PASSWORD).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sato@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("佐藤 太郎"))
                .andExpect(jsonPath("$.department.code").value("SALES"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void loginIsCaseInsensitiveForEmail() throws Exception {
        mockMvc.perform(login("SATO@example.com", PASSWORD).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordReturnsGenericUnauthorized() throws Exception {
        mockMvc.perform(login("sato@example.com", "wrong-password").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("メールアドレスまたはパスワードが正しくありません。"));
    }

    @Test
    void unknownUserReturnsTheSameMessageAsWrongPassword() throws Exception {
        mockMvc.perform(login("nobody@example.com", PASSWORD).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("メールアドレスまたはパスワードが正しくありません。"));
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        mockMvc.perform(login("retired@example.com", PASSWORD).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailuresLockTheAccountForAWhile() throws Exception {
        // 他のテストと状態を共有しないよう、このテスト専用のアカウントを使う
        var dept = jdbc.sql("SELECT id FROM departments WHERE code = 'SALES'")
                .query(java.util.UUID.class)
                .single();
        insertUser("locked@example.com", "ロック 対象", dept, true);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("locked@example.com", "wrong-password").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }

        // ロック中は、正しいパスワードでもログインできない
        mockMvc.perform(login("locked@example.com", PASSWORD).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("ログインに続けて失敗したため")));
        // 他のアカウントには影響しない
        mockMvc.perform(login("sato@example.com", PASSWORD).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void nonexistentAccountsAreLockedTheSameWay() throws Exception {
        // 応答の違いからアカウントの有無が分からないよう、存在しないメールアドレスも同じように数える
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("ghost@example.com", "wrong-password").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("ghost@example.com", "wrong-password").with(csrf()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void invalidRequestBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        var session = new MockHttpSession();
        mockMvc.perform(login("sato@example.com", PASSWORD).with(csrf()).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionIsDiscardedWhenTheRoleChanges() throws Exception {
        var session = loggedIn();

        updateSato("role = 'MANAGER'");

        // 古いロールのまま使い続けられないよう、次のリクエストでセッションを破棄して再ログインを求める
        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("アカウントの権限や所属が変わった")));
        org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();

        // ログインし直せば、新しいロールで使える
        var again = loggedIn();
        mockMvc.perform(get("/api/v1/me").session(again))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void sessionIsDiscardedWhenTheUserIsDeactivatedOrMoved() throws Exception {
        var deactivated = loggedIn();
        updateSato("is_active = false");
        mockMvc.perform(get("/api/v1/me").session(deactivated)).andExpect(status().isUnauthorized());

        updateSato("is_active = true");
        var moved = loggedIn();
        // 部署限定の文書の見え方が変わるため、異動（所属の変更）でも再ログインしてもらう
        updateSato("department_id = NULL");
        mockMvc.perform(get("/api/v1/me").session(moved)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionSurvivesChangesThatDoNotAffectPermissions() throws Exception {
        var session = loggedIn();

        updateSato("display_name = '佐藤 太郎（営業）'");

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("佐藤 太郎（営業）"));
    }

    /** 管理画面などで更新された状態を作る（テストのトランザクション内の JPA のキャッシュも捨てる） */
    private void updateSato(String assignment) {
        jdbc.sql("UPDATE users SET " + assignment + " WHERE email = 'sato@example.com'")
                .update();
        entityManager.clear();
    }

    private MockHttpSession loggedIn() throws Exception {
        var session = new MockHttpSession();
        mockMvc.perform(login("sato@example.com", PASSWORD).with(csrf()).session(session))
                .andExpect(status().isOk());
        return session;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private void insertUser(String email, String name, java.util.UUID dept, boolean active) {
        jdbc.sql("""
                        INSERT INTO users (email, display_name, department_id, role, password_hash, is_active)
                        VALUES (:email, :name, :dept, 'EMPLOYEE', :hash, :active)
                        """)
                .param("email", email)
                .param("name", name)
                .param("dept", dept)
                .param("hash", passwordEncoder.encode(PASSWORD))
                .param("active", active)
                .update();
    }
}
