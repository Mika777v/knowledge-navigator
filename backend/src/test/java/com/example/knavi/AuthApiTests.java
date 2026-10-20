package com.example.knavi;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void csrfEndpointIssuesXsrfCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
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
