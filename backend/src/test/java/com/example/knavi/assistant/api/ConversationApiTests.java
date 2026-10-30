package com.example.knavi.assistant.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 会話の履歴の API（ADR-0014 の決定 3）。本人の会話だけを扱い、他の人の会話は 404 にする（管理者でも同じ） */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ConversationApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    UUID dept;
    KnaviUserDetails sato;
    KnaviUserDetails admin;

    @BeforeEach
    void setUp() {
        dept = jdbc.sql("INSERT INTO departments (code, name) VALUES (:c, 'テスト部') RETURNING id")
                .param("c", "CHAT-" + UUID.randomUUID().toString().substring(0, 8))
                .query(UUID.class)
                .single();
        sato = newUser("sato", "EMPLOYEE");
        admin = newUser("admin", "SYSTEM_ADMIN");
    }

    @Test
    void ownerCanListReadAndDeleteTheirConversation() throws Exception {
        UUID mine = conversation(sato, "結婚祝金はいくら？");
        jdbc.sql("INSERT INTO chat_messages (session_id, seq, role, content) VALUES (:s, 1, 'USER', '結婚祝金はいくら？')")
                .param("s", mine)
                .update();

        mockMvc.perform(get("/api/v1/assistant/conversations").with(user(sato)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.toString()))
                .andExpect(jsonPath("$[0].title").value("結婚祝金はいくら？"));
        mockMvc.perform(get("/api/v1/assistant/conversations/{id}", mine).with(user(sato)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].content").value("結婚祝金はいくら？"))
                .andExpect(jsonPath("$.expiresAt").exists());
        mockMvc.perform(delete("/api/v1/assistant/conversations/{id}", mine)
                        .with(user(sato))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/assistant/conversations/{id}", mine).with(user(sato)))
                .andExpect(status().isNotFound());
    }

    @Test
    void evenAnAdminCannotSeeOrDeleteSomeoneElsesConversation() throws Exception {
        UUID theirs = conversation(sato, "佐藤さんの質問");

        mockMvc.perform(get("/api/v1/assistant/conversations").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/assistant/conversations/{id}", theirs).with(user(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/assistant/conversations/{id}", theirs)
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void needsLoginAndCsrf() throws Exception {
        UUID mine = conversation(sato, "結婚祝金はいくら？");

        mockMvc.perform(get("/api/v1/assistant/conversations")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/assistant/conversations/{id}", mine).with(user(sato)))
                .andExpect(status().isForbidden());
    }

    private KnaviUserDetails newUser(String name, String role) {
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        UUID id = jdbc.sql("INSERT INTO users (email, display_name, department_id, role)"
                        + " VALUES (:e, :n, :d, :r) RETURNING id")
                .param("e", email)
                .param("n", name)
                .param("d", dept)
                .param("r", role)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, dept, email, null, role, true);
    }

    private UUID conversation(KnaviUserDetails owner, String title) {
        return jdbc.sql("INSERT INTO chat_sessions (user_id, title) VALUES (:u, :t) RETURNING id")
                .param("u", owner.id())
                .param("t", title)
                .query(UUID.class)
                .single();
    }
}
