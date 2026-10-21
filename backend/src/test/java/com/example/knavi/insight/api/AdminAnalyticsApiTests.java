package com.example.knavi.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 検索の分析 API（#74） */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminAnalyticsApiTests {

    // 集計期間: 2026-10-01〜2026-10-21（日本時間）
    static final String FROM = "2026-10-01";
    static final String TO = "2026-10-21";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    KnaviUserDetails editor = as("CONTENT_EDITOR");

    // 検索ログは検索とは別のトランザクションで書かれるため、他のテストの検索が残っている。
    // このテストのトランザクションの中で消して（終了時に巻き戻る）、ここで入れたログだけを集計する
    @BeforeEach
    void clearSearchLogs() {
        jdbc.sql("DELETE FROM search_logs").update();
    }

    @Test
    void zeroHitsShowOnlyQueriesSearchedByEnoughPeople() throws Exception {
        log("user-a", "社員食堂", 0, "2026-10-10T03:00:00Z");
        log("user-b", "社員食堂", 0, "2026-10-11T03:00:00Z");
        log("user-a", "社員食堂", 0, "2026-10-12T03:00:00Z");
        // 1人だけの質問は個人の検索履歴になるため出さない
        log("user-c", "ひとりだけの質問", 0, "2026-10-10T03:00:00Z");
        // 結果があった質問はゼロヒットに出さない
        log("user-a", "結婚", 3, "2026-10-10T03:00:00Z");
        log("user-b", "結婚", 3, "2026-10-10T03:00:00Z");

        String body = analytics("zero-hits");
        assertThat(JsonPath.<List<String>>read(body, "$.queries[*].query")).containsExactly("社員食堂");
        assertThat(JsonPath.<Integer>read(body, "$.queries[0].searches")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(body, "$.queries[0].users")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(body, "$.minDistinctUsers")).isEqualTo(2);
        assertThat(body).doesNotContain("user-a").doesNotContain("userHash");
    }

    @Test
    void topQueriesCountZeroHitsAndClicks() throws Exception {
        UUID clicked = log("user-a", "結婚", 3, "2026-10-10T03:00:00Z");
        log("user-b", "結婚", 3, "2026-10-11T03:00:00Z");
        log("user-c", "結婚", 0, "2026-10-12T03:00:00Z");
        log("user-a", "育休", 2, "2026-10-12T03:00:00Z");
        log("user-b", "育休", 2, "2026-10-12T04:00:00Z");
        jdbc.sql(
                        "INSERT INTO search_clicks (search_log_id, target_type, target_id, rank) VALUES (:id, 'PROGRAM', :t, 1)")
                .param("id", clicked)
                .param("t", UUID.randomUUID())
                .update();

        String body = analytics("top-queries");
        assertThat(JsonPath.<List<String>>read(body, "$.queries[*].query")).containsExactly("結婚", "育休");
        Map<String, Object> marriage = JsonPath.read(body, "$.queries[0]");
        assertThat(marriage)
                .containsEntry("searches", 3)
                .containsEntry("users", 3)
                .containsEntry("zeroHitSearches", 1)
                .containsEntry("clickedSearches", 1);
    }

    @Test
    void periodIsInJapanTimeAndIncludesTheEndDate() throws Exception {
        // 2026-10-21 23:30 JST（= 14:30 UTC）は期間内、2026-10-22 00:30 JST（= 10-21 15:30 UTC）は期間外
        log("user-a", "境界", 0, "2026-10-21T14:30:00Z");
        log("user-b", "境界", 0, "2026-10-21T14:31:00Z");
        log("user-c", "境界", 0, "2026-10-21T15:30:00Z");
        // 期間の前
        log("user-d", "境界", 0, "2026-09-30T14:59:00Z");

        String body = analytics("zero-hits");
        assertThat(JsonPath.<Integer>read(body, "$.queries[0].searches")).isEqualTo(2);
        assertThat(JsonPath.<String>read(body, "$.period.from")).isEqualTo(FROM);
    }

    @Test
    void feedbackSummaryHasCountsAndCommentsWithoutWhoWroteThem() throws Exception {
        UUID a = log("user-a", "結婚", 3, "2026-10-10T03:00:00Z");
        UUID b = log("user-b", "社員食堂", 0, "2026-10-10T03:00:00Z");
        feedback(a, true, null, "2026-10-10T03:01:00Z");
        feedback(b, false, "食堂の営業時間が知りたい", "2026-10-10T03:02:00Z");

        String body = analytics("feedback");
        assertThat(JsonPath.<Integer>read(body, "$.helpful")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.notHelpful")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(body, "$.comments[*].comment")).containsExactly("食堂の営業時間が知りたい");
        assertThat(JsonPath.<List<String>>read(body, "$.comments[*].query")).containsExactly("社員食堂");
        assertThat(body).doesNotContain("user-b");
    }

    @ParameterizedTest(name = "{0} は分析を見られる: {1}")
    @CsvSource({"EMPLOYEE, false", "MANAGER, false", "CONTENT_EDITOR, true", "SYSTEM_ADMIN, true"})
    void onlyEditorsAndAdminsCanViewAnalytics(String role, boolean allowed) throws Exception {
        for (String path : List.of("zero-hits", "top-queries", "feedback")) {
            mockMvc.perform(get("/api/v1/admin/analytics/" + path).with(user(as(role))))
                    .andExpect(allowed ? status().isOk() : status().isForbidden());
        }
    }

    @Test
    void rejectsInvalidPeriodAndLimit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/zero-hits")
                        .param("from", "2026-10-21")
                        .param("to", "2026-10-01")
                        .with(user(editor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("開始日は終了日以前にしてください。"));
        mockMvc.perform(get("/api/v1/admin/analytics/zero-hits")
                        .param("from", "2024-01-01")
                        .param("to", "2026-10-01")
                        .with(user(editor)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/analytics/top-queries")
                        .param("limit", "0")
                        .with(user(editor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultPeriodIsTheLast30Days() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/zero-hits").with(user(editor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").exists())
                .andExpect(jsonPath("$.period.to").exists());
    }

    private String analytics(String path) throws Exception {
        return mockMvc.perform(get("/api/v1/admin/analytics/" + path)
                        .param("from", FROM)
                        .param("to", TO)
                        .with(user(editor)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private UUID log(String user, String query, int programs, String createdAt) {
        return jdbc.sql("""
                        INSERT INTO search_logs (user_hash, query, normalized_query, result_counts, created_at)
                        VALUES (rpad(:user, 64, '0'), :q, :q,
                          jsonb_build_object('programs', :programs, 'documents', 0, 'faqs', 0, 'contacts', 1),
                          CAST(:at AS timestamptz))
                        RETURNING id
                        """)
                .param("user", user)
                .param("q", query)
                .param("programs", programs)
                .param("at", createdAt)
                .query(UUID.class)
                .single();
    }

    private void feedback(UUID logId, boolean helpful, String comment, String createdAt) {
        jdbc.sql("""
                        INSERT INTO feedbacks (search_log_id, helpful, comment, created_at)
                        VALUES (:log, :helpful, :comment, CAST(:at AS timestamptz))
                        """)
                .param("log", logId)
                .param("helpful", helpful)
                .param("comment", comment)
                .param("at", createdAt)
                .update();
    }

    private static KnaviUserDetails as(String role) {
        return new KnaviUserDetails(UUID.randomUUID(), null, role.toLowerCase() + "@example.com", null, role, true);
    }
}
