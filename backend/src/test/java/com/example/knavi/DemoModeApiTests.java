package com.example.knavi;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.organization.infrastructure.KnaviUserDetails;
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
import org.springframework.transaction.annotation.Transactional;

/** デモ環境（knavi.demo.enabled=true）では、他の人を締め出せる操作を 403 で断る（ADR-0015） */
@SpringBootTest(properties = "knavi.demo.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DemoModeApiTests {

    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    KnaviUserDetails admin;
    UUID otherUserId;

    @BeforeEach
    void setUp() {
        admin = insertUser("SYSTEM_ADMIN");
        otherUserId = insertUser("EMPLOYEE").id();
    }

    @Test
    void rejectsChangingTheOwnPassword() throws Exception {
        mockMvc.perform(post("/api/v1/me/password")
                        .with(user(admin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"currentPassword\":\"%s\",\"newPassword\":\"new-staple-2026\"}".formatted(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("デモ環境ではパスワードを変更できません（データは毎日初期化されます）。"));
    }

    @Test
    void rejectsUpdatingAUserEvenAsSystemAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/" + otherUserId)
                        .with(user(admin))
                        .with(csrf())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"締め出し 太郎","departmentCode":null,"role":"EMPLOYEE",
                                 "employmentType":"REGULAR","active":false}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("デモ環境ではユーザーを更新できません（データは毎日初期化されます）。"));
    }

    private KnaviUserDetails insertUser(String role) {
        // セッションのユーザーはリクエストごとに DB と照合されるため、実際に登録する
        String email = "demo-" + UUID.randomUUID() + "@example.com";
        UUID id = jdbc.sql("""
                        INSERT INTO users (email, display_name, role, password_hash)
                        VALUES (:email, 'デモ 確認', :role, :hash) RETURNING id
                        """)
                .param("email", email)
                .param("role", role)
                .param("hash", passwordEncoder.encode(PASSWORD))
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, null, email, null, role, true);
    }
}
