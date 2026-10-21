package com.example.knavi.navigation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.api.AdminApiFixture;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminNavigationKeywordApiTests {

    static final String BASE = "/api/v1/admin/navigation-keywords";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    DocumentImport documentImport;

    AdminApiFixture f;
    UUID contactId;

    @BeforeEach
    void setUp() throws Exception {
        f = new AdminApiFixture(mockMvc, jdbc, json, documentImport);
        contactId = f.idOf("contacts", "GA-GENERAL");
    }

    @Test
    void addsNormalizedKeywordAndFindsItByTerm() throws Exception {
        f.perform(post(BASE), f.gaEditor, Map.of("term", "ＰＣ 備品", "targetType", "CONTACT", "targetId", contactId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.normalizedTerm").value("pc 備品"))
                .andExpect(jsonPath("$.weight").value(1.0));

        mockMvc.perform(get(BASE).param("term", "PC 備品").with(user(f.gaEditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetId").value(contactId.toString()))
                .andExpect(jsonPath("$[0].targetLabel").value("総務部 庶務グループ"));
    }

    @Test
    void listsTargetsByType() throws Exception {
        String body = mockMvc.perform(
                        get(BASE + "/targets").param("targetType", "CONTACT").with(user(f.gaEditor)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[*].code")).contains("GA-GENERAL");
        mockMvc.perform(get(BASE + "/targets").param("targetType", "CONTACT").with(user(f.employee)))
                .andExpect(status().isForbidden());
    }

    @Test
    void namesOfTargetsTheUserCannotViewAreHidden() throws Exception {
        // 総務部が所管する機密の制度。総務部のコンテンツ担当者には見えるが、システム管理者には見えない
        UUID secret = jdbc.sql("""
                        INSERT INTO programs (code, name, summary, owner_department_id, visibility, status, search_text)
                        VALUES ('PRG-SECRET', '機密の制度', '説明', :dept, 'CONFIDENTIAL', 'PUBLISHED', '機密の制度')
                        RETURNING id
                        """)
                .param("dept", f.idOf("departments", "GA"))
                .query(UUID.class)
                .single();
        f.perform(post(BASE), f.gaEditor, Map.of("term", "ひみつ", "targetType", "PROGRAM", "targetId", secret))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetLabel").value("機密の制度"));

        mockMvc.perform(get(BASE).param("term", "ひみつ").with(user(f.admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetId").value(secret.toString()))
                .andExpect(jsonPath("$[0].targetLabel").doesNotExist());
        String targets = mockMvc.perform(
                        get(BASE + "/targets").param("targetType", "PROGRAM").with(user(f.admin)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(targets).doesNotContain("機密の制度");
    }

    @Test
    void rejectsDuplicatesAndUnknownTargets() throws Exception {
        f.perform(post(BASE), f.gaEditor, Map.of("term", "慶弔", "targetType", "CONTACT", "targetId", contactId))
                .andExpect(status().isCreated());
        f.perform(post(BASE), f.gaEditor, Map.of("term", "慶弔", "targetType", "CONTACT", "targetId", contactId))
                .andExpect(status().isConflict());
        f.perform(post(BASE), f.gaEditor, Map.of("term", "慶弔", "targetType", "PROGRAM", "targetId", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editorsAndAdminsCanManageButEmployeesCannot() throws Exception {
        f.perform(post(BASE), f.employee, Map.of("term", "x", "targetType", "CONTACT", "targetId", contactId))
                .andExpect(status().isForbidden());

        String body = f.perform(
                        post(BASE), f.admin, Map.of("term", "名刺", "targetType", "CONTACT", "targetId", contactId))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mockMvc.perform(delete(BASE + "/" + id).with(user(f.gaEditor)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE)
                        .param("targetType", "CONTACT")
                        .param("targetId", contactId.toString())
                        .with(user(f.gaEditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
