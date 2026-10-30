package com.example.knavi.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** ダミーデータで、結婚のきっかけの案内（ADR-0014 の決定 1・E15-T2〜T4）が画面に必要な情報を返すことを確かめる */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GuideApiWithSeedTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SeedLoader loader;

    @Autowired
    JdbcClient jdbc;

    KnaviUserDetails sato;

    @BeforeEach
    void setUp() {
        loader.load(Path.of("..", "data", "seed"), "demo-password-for-test");
        sato = jdbc.sql("SELECT id, department_id, role FROM users WHERE email = 'sato@example.com'")
                .query((rs, i) -> new KnaviUserDetails(
                        rs.getObject("id", UUID.class),
                        rs.getObject("department_id", UUID.class),
                        "sato@example.com",
                        null,
                        rs.getString("role"),
                        true))
                .single();
    }

    @Test
    void marriageAsksBothQuestionsAndPrefillsTheEmploymentType() throws Exception {
        String body = guide("{\"lifeEvent\":\"MARRIAGE\"}");

        List<String> codes = JsonPath.read(body, "$.questions[*].code");
        assertThat(codes).containsExactly("EMPLOYMENT_TYPE", "TENURE_6M");
        assertThat((String) JsonPath.read(body, "$.questions[0].answer")).isEqualTo("REGULAR");
        assertThat((String) JsonPath.read(body, "$.questions[0].answerSource")).isEqualTo("PROFILE");
        assertThat(programStatus(body, "PRG-MARRIAGE-GIFT")).containsExactly("UNKNOWN");
        assertThat(programStatus(body, "PRG-MARRIAGE-LEAVE")).containsExactly("ELIGIBLE");
        assertThat(programStatus(body, "PRG-HOUSING-ALLOWANCE")).containsExactly("ELIGIBLE");
    }

    @Test
    void partTimeEmployeeIsNotEligibleForHousingAllowanceButGetsTheGiftChecklist() throws Exception {
        String body = guide(
                "{\"lifeEvent\":\"MARRIAGE\",\"answers\":{\"EMPLOYMENT_TYPE\":\"PART_TIME\",\"TENURE_6M\":\"YES\"}}");

        assertThat(programStatus(body, "PRG-HOUSING-ALLOWANCE")).containsExactly("NOT_ELIGIBLE");
        assertThat(programStatus(body, "PRG-MARRIAGE-GIFT")).containsExactly("ELIGIBLE");
        List<String> checklist = JsonPath.read(body, "$.checklist[*].programName");
        assertThat(checklist).contains("結婚祝金").doesNotContain("住宅手当");
        List<String> documents = JsonPath.read(body, "$.checklist[?(@.programName == '結婚祝金')].requiredDocuments");
        assertThat(documents).containsExactly("戸籍謄本または婚姻届受理証明書の写し");
        // 根拠と申請先は DB の値（ADR-0009）
        List<String> articles = JsonPath.read(body, "$.programs[?(@.code == 'PRG-MARRIAGE-GIFT')].basis[*].articleNo");
        assertThat(articles).isNotEmpty();
        List<String> roles = JsonPath.read(body, "$.programs[?(@.code == 'PRG-MARRIAGE-GIFT')].contacts[*].role");
        assertThat(roles).contains("APPLY");
    }

    @Test
    void invalidAnswersAreBadRequestsAndLoginAndCsrfAreRequired() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/guides")
                        .with(user(sato))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lifeEvent\":\"MARRIAGE\",\"answers\":{\"TENURE_6M\":\"MAYBE\"}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/assistant/guides")
                        .with(user(sato))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lifeEvent\":\"NO-SUCH-EVENT\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/assistant/guides")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lifeEvent\":\"MARRIAGE\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/assistant/guides")
                        .with(user(sato))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lifeEvent\":\"MARRIAGE\"}"))
                .andExpect(status().isForbidden());
    }

    private String guide(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/assistant/guides")
                        .with(user(sato))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifeEvent.name").value("結婚"))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<String> programStatus(String body, String programCode) {
        return JsonPath.read(body, "$.programs[?(@.code == '" + programCode + "')].status");
    }
}
