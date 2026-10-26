package com.example.knavi.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
import java.util.List;
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

/** GET /api/v1/programs/{id}/recommendations をダミーデータで確認する（E12-T5） */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RecommendationApiWithSeedTests {

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
        jdbc.sql("DELETE FROM search_logs").update();
        sato = demoUser("sato@example.com");
        tanaka = demoUser("tanaka@example.com");
    }

    @Test
    void recommendsProgramsOfTheSameLifeEvent() throws Exception {
        String body = recommend("PRG-MARRIAGE-GIFT", sato);

        assertThat(JsonPath.<List<String>>read(body, "$[*].code"))
                .startsWith("PRG-MARRIAGE-LEAVE", "PRG-PERSONAL-INFO-CHANGE")
                .doesNotContain("PRG-MARRIAGE-GIFT")
                .hasSizeLessThanOrEqualTo(5);
        assertThat(JsonPath.<String>read(body, "$[0].reason")).isEqualTo("LIFE_EVENT");
        assertThat(JsonPath.<String>read(body, "$[0].lifeEvent")).isEqualTo("結婚");
    }

    @Test
    void programsOpenedTogetherByEnoughPeopleComeFirst() throws Exception {
        // 結婚祝金と PC の故障を、2人があわせて開いた → まだ出さない（少人数の行動が分からないように）
        clickedTogether("1", "PRG-MARRIAGE-GIFT", "PRG-PC-REPAIR");
        clickedTogether("2", "PRG-MARRIAGE-GIFT", "PRG-PC-REPAIR");
        assertThat(JsonPath.<List<String>>read(recommend("PRG-MARRIAGE-GIFT", sato), "$[*].code"))
                .doesNotContain("PRG-PC-REPAIR");

        // 3人目で出す
        clickedTogether("3", "PRG-MARRIAGE-GIFT", "PRG-PC-REPAIR");
        String body = recommend("PRG-MARRIAGE-GIFT", sato);
        assertThat(JsonPath.<String>read(body, "$[0].code")).isEqualTo("PRG-PC-REPAIR");
        assertThat(JsonPath.<String>read(body, "$[0].reason")).isEqualTo("CO_CLICK");
    }

    @Test
    void neverRecommendsProgramsTheUserCannotSearch() throws Exception {
        for (String person : List.of("1", "2", "3")) {
            clickedTogether(person, "PRG-CHILDCARE-LEAVE", "PRG-MANAGER-CHILDCARE-SUPPORT");
        }
        assertThat(JsonPath.<List<String>>read(recommend("PRG-CHILDCARE-LEAVE", sato), "$[*].code"))
                .doesNotContain("PRG-MANAGER-CHILDCARE-SUPPORT");
        assertThat(JsonPath.<List<String>>read(recommend("PRG-CHILDCARE-LEAVE", tanaka), "$[*].code"))
                .contains("PRG-MANAGER-CHILDCARE-SUPPORT");

        // 見えない制度の推薦は、存在しない制度と同じく空
        assertThat(JsonPath.<List<Object>>read(recommend("PRG-MANAGER-CHILDCARE-SUPPORT", sato), "$"))
                .isEmpty();
        String unknown = mockMvc.perform(get("/api/v1/programs/" + UUID.randomUUID() + "/recommendations")
                        .with(user(sato)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<Object>>read(unknown, "$")).isEmpty();
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/programs/" + programId("PRG-MARRIAGE-GIFT") + "/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    /** 仮名化したユーザー（person）が、1回の検索の結果から2つの制度を開いた */
    private void clickedTogether(String person, String... programCodes) {
        UUID log =
                jdbc.sql("""
                        INSERT INTO search_logs (user_hash, query, normalized_query, result_counts)
                        VALUES (:hash, 'q', 'q', '{}') RETURNING id
                        """).param("hash", person.repeat(64)).query(UUID.class).single();
        int rank = 1;
        for (String code : programCodes) {
            jdbc.sql("""
                            INSERT INTO search_clicks (search_log_id, target_type, target_id, rank)
                            VALUES (:log, 'PROGRAM', :id, :rank)
                            """)
                    .param("log", log)
                    .param("id", programId(code))
                    .param("rank", rank++)
                    .update();
        }
    }

    private String recommend(String code, KnaviUserDetails as) throws Exception {
        return mockMvc.perform(get("/api/v1/programs/" + programId(code) + "/recommendations")
                        .with(user(as)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private UUID programId(String code) {
        return jdbc.sql("SELECT id FROM programs WHERE code = :c")
                .param("c", code)
                .query(UUID.class)
                .single();
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
