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

/** ダミーデータを投入した状態で、一般利用者向けの閲覧 API が画面に必要な情報を返すことを確認する */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PublicApiWithSeedTests {

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
    void marriageLifeEventListsPrimaryAndRelatedPrograms() throws Exception {
        String body = mockMvc.perform(get("/api/v1/life-events/MARRIAGE").with(user(sato)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifeEvent.name").value("結婚"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> primary = JsonPath.read(body, "$.programs[?(@.relation == 'PRIMARY')].program.name");
        List<String> related = JsonPath.read(body, "$.programs[?(@.relation == 'RELATED')].program.name");
        assertThat(primary).containsExactly("結婚祝金", "結婚休暇");
        assertThat(related).contains("氏名・住所・扶養の変更届", "通勤手当");
    }

    @Test
    void programDetailHasBasisContactsAndFaqs() throws Exception {
        UUID id = jdbc.sql("SELECT id FROM programs WHERE code = 'PRG-MARRIAGE-GIFT'")
                .query(UUID.class)
                .single();

        mockMvc.perform(get("/api/v1/programs/" + id).with(user(sato)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program.benefit").value("30,000円（社員同士の結婚はそれぞれに支給）"))
                .andExpect(jsonPath("$.basis[0].documentTitle").value("慶弔見舞金規程"))
                .andExpect(jsonPath("$.basis[0].articleNo").value("第5条"))
                .andExpect(jsonPath("$.contacts[0].name").value("総務部 庶務グループ"))
                .andExpect(jsonPath("$.contacts[0].phoneExt").value("1310"))
                .andExpect(jsonPath("$.contacts[0].role").value("APPLY"))
                .andExpect(jsonPath("$.faqs[?(@.code == 'FAQ-001')]").exists())
                .andExpect(jsonPath("$.lifeEvents[0].code").value("MARRIAGE"));
    }

    @Test
    void faqDetailResolvesReferencesAndContact() throws Exception {
        UUID id = jdbc.sql("SELECT id FROM faqs WHERE code = 'FAQ-007'")
                .query(UUID.class)
                .single();

        mockMvc.perform(get("/api/v1/faqs/" + id).with(user(sato)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.code").value("GA-GENERAL"))
                .andExpect(jsonPath("$.programs.length()").value(2))
                .andExpect(jsonPath("$.references[?(@.articleNo == '第32条')].documentTitle")
                        .value("就業規則"));
    }

    @Test
    void managerOnlyFaqsAreHiddenFromEmployees() throws Exception {
        String forEmployee = mockMvc.perform(get("/api/v1/faqs").with(user(sato)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String forManager = mockMvc.perform(get("/api/v1/faqs").with(user(tanaka)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> employeeCodes = JsonPath.read(forEmployee, "$[*].code");
        List<String> managerCodes = JsonPath.read(forManager, "$[*].code");
        assertThat(employeeCodes).hasSize(48).doesNotContain("FAQ-049", "FAQ-050");
        assertThat(managerCodes).hasSize(50);
    }

    @Test
    void documentDetailListsArticles() throws Exception {
        UUID id = jdbc.sql("SELECT id FROM documents WHERE code = 'REG-HR-001'")
                .query(UUID.class)
                .single();

        mockMvc.perform(get("/api/v1/documents/" + id).with(user(sato)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.title").value("就業規則"))
                .andExpect(jsonPath("$.document.versionLabel").value("v5.0"))
                .andExpect(
                        jsonPath("$.sections[?(@.articleNo == '第32条')].heading").value("特別休暇"));
    }

    @Test
    void filtersByParentCategoryIncludingChildren() throws Exception {
        String body = mockMvc.perform(
                        get("/api/v1/documents").param("category", "hr").with(user(sato)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> codes = JsonPath.read(body, "$[*].code");
        assertThat(codes).contains("REG-HR-001", "REG-HR-002", "REG-HR-004").doesNotContain("REG-IT-001");
    }

    @Test
    void contactsAndCategoriesAreListed() throws Exception {
        mockMvc.perform(get("/api/v1/contacts").with(user(sato)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'IT')].contacts[0].code").value("IT-HELPDESK"));
        mockMvc.perform(get("/api/v1/categories").with(user(sato)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'hr')].children.length()").value(3));
        mockMvc.perform(get("/api/v1/life-events").with(user(sato)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
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
