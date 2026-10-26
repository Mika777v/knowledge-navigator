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

/** GET /api/v1/search/suggest をダミーデータで確認する（E12-T6） */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SuggestionApiWithSeedTests {

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

    @Test
    void suggestsLifeEventsAndProgramsWhoseNamesStartWithTheInputFirst() throws Exception {
        String body = suggest("結婚", sato);

        assertThat(JsonPath.<List<String>>read(body, "$[*].label")).startsWith("結婚", "結婚祝金", "結婚休暇");
        assertThat(JsonPath.<String>read(body, "$[0].type")).isEqualTo("LIFE_EVENT");
        assertThat(JsonPath.<String>read(body, "$[1].type")).isEqualTo("PROGRAM");
    }

    @Test
    void matchesTheMiddleOfNamesAndKeywordsIgnoringWidthAndCase() throws Exception {
        // 「育休」は「産後パパ育休（出生時育児休業）」の名前の途中、「育児休業」のキーワードに一致する
        assertThat(JsonPath.<List<String>>read(suggest("育休", sato), "$[*].code"))
                .contains("PRG-PATERNITY-LEAVE", "PRG-CHILDCARE-LEAVE");
        // 全角・大文字でも同じ
        assertThat(JsonPath.<List<String>>read(suggest("ＰＣ", sato), "$[*].code"))
                .contains("PRG-PC-REPAIR");
    }

    @Test
    void neverSuggestsProgramsTheUserCannotSearch() throws Exception {
        assertThat(JsonPath.<List<String>>read(suggest("部下", sato), "$[*].code"))
                .doesNotContain("PRG-MANAGER-CHILDCARE-SUPPORT");
        assertThat(JsonPath.<List<String>>read(suggest("部下", tanaka), "$[*].code"))
                .contains("PRG-MANAGER-CHILDCARE-SUPPORT");

        // 閲覧できても、機密（CONFIDENTIAL）の制度は検索と同じく候補に出さない
        jdbc.sql("UPDATE programs SET visibility = 'CONFIDENTIAL' WHERE code = 'PRG-MARRIAGE-GIFT'")
                .update();
        assertThat(JsonPath.<List<String>>read(suggest("結婚", tanaka), "$[*].code"))
                .doesNotContain("PRG-MARRIAGE-GIFT");
    }

    @Test
    void returnsNothingForBlankOrTooLongInputAndRequiresLogin() throws Exception {
        assertThat(JsonPath.<List<Object>>read(suggest(" ", sato), "$")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(suggest("あ".repeat(51), sato), "$"))
                .isEmpty();
        assertThat(JsonPath.<List<Object>>read(suggest("ぴよぴよ", sato), "$")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(suggest("結婚", sato), "$")).hasSizeLessThanOrEqualTo(8);
        mockMvc.perform(get("/api/v1/search/suggest").param("q", "結婚")).andExpect(status().isUnauthorized());
    }

    private String suggest(String q, KnaviUserDetails as) throws Exception {
        return mockMvc.perform(get("/api/v1/search/suggest").param("q", q).with(user(as)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private KnaviUserDetails demoUser(String email) {
        return jdbc.sql("SELECT id, department_id, email, role FROM users WHERE email = :e")
                .param("e", email)
                .query((rs, i) -> new KnaviUserDetails(
                        rs.getObject("id", UUID.class),
                        rs.getObject("department_id", UUID.class),
                        rs.getString("email"),
                        null,
                        rs.getString("role"),
                        true))
                .single();
    }
}
