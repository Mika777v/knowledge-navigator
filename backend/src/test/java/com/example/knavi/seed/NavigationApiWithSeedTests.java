package com.example.knavi.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

/** GET /api/v1/navigate をダミーデータで確認する（#35） */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NavigationApiWithSeedTests {

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
    void marriageQuestionNavigatesToProgramsBasisAndContacts() throws Exception {
        String body = navigate("結婚したら何か制度ある？", sato);

        assertThat(JsonPath.<List<String>>read(body, "$.query.matchedLifeEvents[*].code"))
                .contains("MARRIAGE");
        assertThat(JsonPath.<List<String>>read(body, "$.programs[*].code"))
                .startsWith("PRG-MARRIAGE-GIFT", "PRG-MARRIAGE-LEAVE");
        assertThat(JsonPath.<List<String>>read(body, "$.programs[0].reasons")).contains("LIFE_EVENT:結婚");
        assertThat(JsonPath.<List<String>>read(body, "$.programs[0].basis[*].documentTitle"))
                .contains("慶弔見舞金規程");
        assertThat(JsonPath.<List<String>>read(body, "$.documents[*].code")).contains("REG-GA-001", "REG-HR-001");
        assertThat(JsonPath.<List<String>>read(body, "$.contacts[*].code")).contains("GA-GENERAL", "HR-LABOR");
        assertThat(JsonPath.<List<String>>read(body, "$.relatedPrograms[*].code"))
                .contains("PRG-PERSONAL-INFO-CHANGE");
        assertThat(JsonPath.<Boolean>read(body, "$.fallback")).isFalse();
    }

    @Test
    void basisSectionsAppearWithPlainTextSnippets() throws Exception {
        String body = navigate("結婚祝金", sato);

        List<String> articles = JsonPath.read(body, "$.documents[?(@.code == 'REG-GA-001')].sections[*].articleNo");
        assertThat(articles).contains("第5条");
        List<String> snippets = JsonPath.read(body, "$.documents[*].sections[*].snippet");
        assertThat(snippets).isNotEmpty().noneMatch(s -> s.contains("<"));
    }

    @Test
    void sectionsOfRegulationsWithAnOriginalShowTheirPages() throws Exception {
        // PDF の原本が付いた公開中の版の状態を作る（原本の結び付けは SeedSourcePdfsTests で確かめる）
        UUID file = jdbc.sql("""
                        INSERT INTO stored_files (storage_key, original_name, mime_type, size_bytes, sha256)
                        VALUES ('files/00/ga.pdf', '慶弔見舞金規程.pdf', 'application/pdf', 10, repeat('1', 64)) RETURNING id
                        """).query(UUID.class).single();
        jdbc.sql("""
                        UPDATE document_versions SET source_file_id = :f
                        WHERE id = (SELECT current_version_id FROM documents WHERE code = 'REG-GA-001')
                        """).param("f", file).update();
        jdbc.sql("""
                        UPDATE sections SET page_from = 1, page_to = 2 WHERE article_no = '第5条'
                        AND document_version_id = (SELECT current_version_id FROM documents WHERE code = 'REG-GA-001')
                        """).update();

        String body = navigate("結婚祝金", sato);

        assertThat(JsonPath.<List<Boolean>>read(body, "$.documents[?(@.code == 'REG-GA-001')].hasSourceFile"))
                .containsExactly(true);
        assertThat(JsonPath.<List<Integer>>read(
                        body, "$.documents[?(@.code == 'REG-GA-001')].sections[?(@.articleNo == '第5条')].pageFrom"))
                .containsExactly(1);
        // 原本の無い規程は、ページを出さない
        assertThat(JsonPath.<List<Boolean>>read(body, "$.documents[?(@.code != 'REG-GA-001')].hasSourceFile"))
                .doesNotContain(true);
    }

    @Test
    void keywordsMatchDifferentInflectionsByStem() throws Exception {
        // 辞書の「眠れない」に対して「眠れなくて」、「出勤時間をずらす」に対して「出勤時間を遅くしたい」
        String sleep = navigate("眠れなくて仕事がつらい", sato);
        assertThat(JsonPath.<List<String>>read(sleep, "$.programs[*].code")).contains("PRG-HEALTH-CONSULTATION");
        assertThat(JsonPath.<List<String>>read(sleep, "$.programs[?(@.code == 'PRG-HEALTH-CONSULTATION')].reasons[*]"))
                .contains("KEYWORD:眠れ");

        String shift = navigate("出勤時間を遅くしたい", sato);
        assertThat(JsonPath.<String>read(shift, "$.programs[0].code")).isEqualTo("PRG-TIME-SHIFT");
    }

    @Test
    void unknownQuestionFallsBackToTheGeneralContact() throws Exception {
        String body = navigate("ぴよぴよ", sato);

        assertThat(JsonPath.<Boolean>read(body, "$.fallback")).isTrue();
        assertThat(JsonPath.<List<String>>read(body, "$.contacts[*].code")).containsExactly("HR-REP");
        assertThat(JsonPath.<List<Object>>read(body, "$.programs")).isEmpty();
    }

    @Test
    void managerOnlyFaqsAreSearchableOnlyByManagers() throws Exception {
        String forEmployee = navigate("部下から妊娠の報告", sato);
        String forManager = navigate("部下から妊娠の報告", tanaka);

        assertThat(JsonPath.<List<String>>read(forEmployee, "$.faqs[*].code")).doesNotContain("FAQ-049");
        assertThat(JsonPath.<List<String>>read(forManager, "$.faqs[*].code")).contains("FAQ-049");
    }

    @Test
    void confidentialContentIsNeverSearchedEvenWhenTheUserMayViewIt() throws Exception {
        UUID programId = jdbc.sql("SELECT id FROM programs WHERE code = 'PRG-MARRIAGE-GIFT'")
                .query(UUID.class)
                .single();
        jdbc.sql("UPDATE programs SET visibility = 'CONFIDENTIAL' WHERE id = :id")
                .param("id", programId)
                .update();
        jdbc.sql("""
                        INSERT INTO access_rules (resource_type, resource_id, principal_type, principal_id)
                        VALUES ('PROGRAM', :id, 'USER', :user)
                        """).param("id", programId).param("user", sato.id().toString()).update();

        mockMvc.perform(get("/api/v1/programs/" + programId).with(user(sato))).andExpect(status().isOk());
        String body = navigate("結婚祝金", sato);
        assertThat(JsonPath.<List<String>>read(body, "$.programs[*].code")).doesNotContain("PRG-MARRIAGE-GIFT");
        assertThat(JsonPath.<List<String>>read(body, "$.relatedPrograms[*].code"))
                .doesNotContain("PRG-MARRIAGE-GIFT");
    }

    @Test
    void rejectsBlankOrTooLongQuestions() throws Exception {
        mockMvc.perform(get("/api/v1/navigate").param("q", " ").with(user(sato)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("知りたいことを入力してください。"));
        mockMvc.perform(get("/api/v1/navigate").param("q", "あ".repeat(201)).with(user(sato)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/navigate").param("q", "結婚")).andExpect(status().isUnauthorized());
    }

    private String navigate(String q, KnaviUserDetails as) throws Exception {
        return mockMvc.perform(get("/api/v1/navigate").param("q", q).with(user(as)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
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
