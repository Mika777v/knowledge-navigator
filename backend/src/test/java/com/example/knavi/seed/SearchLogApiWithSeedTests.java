package com.example.knavi.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
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
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 検索ログ・クリック・フィードバック（#40） */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SearchLogApiWithSeedTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SeedLoader loader;

    @Autowired
    JdbcClient jdbc;

    KnaviUserDetails sato;
    KnaviUserDetails tanaka;

    @BeforeEach
    void setUp() {
        loader.load(Path.of("..", "data", "seed"), "demo-password-for-test");
        sato = demoUser("sato@example.com");
        tanaka = demoUser("tanaka@example.com");
    }

    // 検索ログは検索とは別のトランザクションでコミットされるため、テストのトランザクションの外で消す
    @AfterTransaction
    void cleanUp() {
        jdbc.sql("DELETE FROM search_logs WHERE query LIKE 'テスト:%'").update();
    }

    @Test
    void searchIsLoggedWithPseudonymizedUser() throws Exception {
        UUID logId = search("テスト:結婚", sato);

        Map<String, Object> row = jdbc.sql("""
                        SELECT user_hash, department_id, query, normalized_query, result_counts::text AS counts,
                          top_hits::text AS hits, latency_ms
                        FROM search_logs WHERE id = :id
                        """).param("id", logId).query().singleRow();
        assertThat((String) row.get("user_hash"))
                .hasSize(64)
                .doesNotContain(sato.id().toString());
        assertThat(row.get("department_id")).isEqualTo(sato.departmentId());
        assertThat(row.get("query")).isEqualTo("テスト:結婚");
        assertThat((String) row.get("counts")).contains("\"programs\"");
        assertThat((String) row.get("hits")).contains("PRG-MARRIAGE-GIFT");
        assertThat((Integer) row.get("latency_ms")).isNotNegative();

        UUID second = search("テスト:育休", sato);
        String hashes = jdbc.sql("SELECT count(DISTINCT user_hash) FROM search_logs WHERE id IN (:a, :b)")
                .param("a", logId)
                .param("b", second)
                .query(String.class)
                .single();
        assertThat(hashes).as("同じ人の検索は同じ仮名になる").isEqualTo("1");
    }

    @Test
    void clicksAndFeedbackAreRecordedForOwnSearch() throws Exception {
        UUID logId = search("テスト:結婚", sato);
        UUID programId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/search-logs/" + logId + "/clicks")
                        .with(user(sato))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"PROGRAM\",\"targetId\":\"" + programId + "\",\"rank\":1}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/search-logs/" + logId + "/feedback")
                        .with(user(sato))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"helpful\":false,\"comment\":\"最初は役に立たなかった\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/search-logs/" + logId + "/feedback")
                        .with(user(sato))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"helpful\":true,\"comment\":\"  \"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql("SELECT target_id FROM search_clicks WHERE search_log_id = :id")
                        .param("id", logId)
                        .query(UUID.class)
                        .single())
                .isEqualTo(programId);
        Map<String, Object> feedback = jdbc.sql("SELECT helpful, comment FROM feedbacks WHERE search_log_id = :id")
                .param("id", logId)
                .query()
                .singleRow();
        assertThat(feedback).containsEntry("helpful", true).containsEntry("comment", null);
    }

    @Test
    void othersSearchLogsCannotBeTouched() throws Exception {
        UUID logId = search("テスト:結婚", sato);

        mockMvc.perform(post("/api/v1/search-logs/" + logId + "/feedback")
                        .with(user(tanaka))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"helpful\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/search-logs/" + UUID.randomUUID() + "/clicks")
                        .with(user(sato))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"FAQ\",\"targetId\":\"" + UUID.randomUUID() + "\",\"rank\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        UUID logId = search("テスト:結婚", sato);

        mockMvc.perform(post("/api/v1/search-logs/" + logId + "/clicks")
                        .with(user(sato))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + UUID.randomUUID() + "\",\"rank\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/search-logs/" + logId + "/feedback")
                        .with(user(sato))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"helpful\":true,\"comment\":\"" + "あ".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/search-logs/" + logId + "/feedback")
                        .with(user(sato))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"helpful\":true}"))
                .andExpect(status().isForbidden());
    }

    private UUID search(String q, KnaviUserDetails as) throws Exception {
        String body = mockMvc.perform(get("/api/v1/navigate").param("q", q).with(user(as)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.searchLogId"));
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
