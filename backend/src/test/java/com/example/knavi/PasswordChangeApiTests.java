package com.example.knavi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PasswordChangeApiTests {

    static final String PASSWORD = "correct-horse-battery";
    static final String NEW_PASSWORD = "new-staple-2026";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EntityManager entityManager;

    String email;
    UUID userId;

    @BeforeEach
    void setUp() {
        // ログインの回数制限はアカウントごとに数えるため、テストごとに別のアカウントを使う
        email = "pw-" + UUID.randomUUID() + "@example.com";
        userId = jdbc.sql("""
                        INSERT INTO users (email, display_name, role, password_hash)
                        VALUES (:email, 'パスワード 変更', 'EMPLOYEE', :hash) RETURNING id
                        """)
                .param("email", email)
                .param("hash", passwordEncoder.encode(PASSWORD))
                .query(UUID.class)
                .single();
    }

    @Test
    void changesThePasswordAndRecordsItWithoutThePassword() throws Exception {
        var session = loggedIn();
        String oldSessionId = session.getId();

        change(session, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // ログインに使った JPA のキャッシュを捨て、DB の値で確かめる
        entityManager.clear();
        mockMvc.perform(login(NEW_PASSWORD).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(login(PASSWORD).with(csrf())).andExpect(status().isUnauthorized());

        var details = jdbc.sql("""
                        SELECT coalesce(after_data::text, '') || coalesce(before_data::text, '') FROM audit_logs
                        WHERE action = 'USER_PASSWORD_CHANGED' AND resource_id = :id
                        """).param("id", userId).query(String.class).single();
        assertThat(details).doesNotContain(NEW_PASSWORD).doesNotContain(PASSWORD);
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
    }

    @Test
    void rejectsAWrongCurrentPasswordWithoutLoggingOut() throws Exception {
        var session = loggedIn();

        // 401 だと画面がログイン画面へ移動してしまうため、入力の誤りとして 400 を返す
        change(session, "wrong-password", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("今のパスワードが正しくありません。"));

        entityManager.clear();
        mockMvc.perform(login(PASSWORD).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void rejectsAShortOrUnchangedNewPassword() throws Exception {
        var session = loggedIn();

        change(session, PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("新しいパスワードは8文字以上にしてください。"));
        change(session, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("今と同じパスワードには変更できません。"));
    }

    @Test
    void repeatedWrongCurrentPasswordsLockLikeLogin() throws Exception {
        var session = loggedIn();
        for (int i = 0; i < 5; i++) {
            change(session, "wrong-password", NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        // ロック中は、正しいパスワードでも受け付けない
        change(session, PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void requiresLoginAndCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/me/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/password")
                        .session(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    private ResultActions change(MockHttpSession session, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/v1/me/password")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(current, next)));
    }

    private MockHttpSession loggedIn() throws Exception {
        var session = new MockHttpSession();
        mockMvc.perform(login(PASSWORD).with(csrf()).session(session)).andExpect(status().isOk());
        return session;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private static String body(String current, String next) {
        return "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next);
    }
}
