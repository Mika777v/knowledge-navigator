package com.example.knavi.navigation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.api.AdminApiFixture;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminSynonymApiTests {

    static final String BASE = "/api/v1/admin/synonyms";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    DocumentImport documentImport;

    AdminApiFixture f;

    @BeforeEach
    void setUp() throws Exception {
        f = new AdminApiFixture(mockMvc, jdbc, json, documentImport);
    }

    @Test
    void createsAGroupWithTheCanonicalFirstAndNormalizedDuplicatesRemoved() throws Exception {
        f.perform(post(BASE), f.gaEditor, Map.of("canonical", "PC", "terms", List.of("パソコン", "ＰＣ", " ノートPC ")))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.canonical").value("PC"))
                .andExpect(jsonPath("$.terms").value(org.hamcrest.Matchers.contains("PC", "ノートPC", "パソコン")));

        // 語の一部で探せる（全角半角のちがいは無視する）
        mockMvc.perform(get(BASE).param("q", "ﾊﾟｿｺﾝ").with(user(f.gaEditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].canonical").value(org.hamcrest.Matchers.contains("PC")));
        mockMvc.perform(get(BASE).param("q", "スマホ").with(user(f.gaEditor)))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aTermBelongsToOnlyOneGroup() throws Exception {
        create("育児休業", "育休");

        f.perform(post(BASE), f.gaEditor, Map.of("canonical", "育児休暇", "terms", List.of("育休")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("「育休」は類義語「育児休業」のグループに登録されています。"));
        assertThat(jdbc.sql("SELECT count(*) FROM synonym_groups")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void rejectsGroupsWithoutAnotherTermOrWithTooManyTerms() throws Exception {
        f.perform(post(BASE), f.gaEditor, Map.of("canonical", "PC", "terms", List.of("ＰＣ")))
                .andExpect(status().isBadRequest());
        f.perform(post(BASE), f.gaEditor, Map.of("canonical", "PC", "terms", List.of("p".repeat(101))))
                .andExpect(status().isBadRequest());
        var many =
                java.util.stream.IntStream.range(0, 21).mapToObj(i -> "語" + i).toList();
        f.perform(post(BASE), f.gaEditor, Map.of("canonical", "PC", "terms", many))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesWithIfMatchAndRejectsStaleVersions() throws Exception {
        String id = JsonPath.read(create("在宅勤務", "テレワーク").getResponse().getContentAsString(), "$.id");

        f.perform(put(BASE + "/" + id), f.gaEditor, Map.of("canonical", "在宅勤務", "terms", List.of("リモートワーク")))
                .andExpect(status().isPreconditionRequired());
        f.perform(
                        put(BASE + "/" + id).header("If-Match", "\"0\""),
                        f.gaEditor,
                        Map.of("canonical", "在宅勤務", "terms", List.of("リモートワーク", "テレワーク")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.terms").value(org.hamcrest.Matchers.contains("在宅勤務", "テレワーク", "リモートワーク")));
        f.perform(
                        put(BASE + "/" + id).header("If-Match", "\"0\""),
                        f.gaEditor,
                        Map.of("canonical", "在宅勤務", "terms", List.of("在宅")))
                .andExpect(status().isPreconditionFailed());

        mockMvc.perform(get(BASE + "/" + id).with(user(f.gaEditor)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    void editorsAndAdminsCanManageButEmployeesCannot() throws Exception {
        f.perform(post(BASE), f.employee, Map.of("canonical", "PC", "terms", List.of("パソコン")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE).with(user(f.employee))).andExpect(status().isForbidden());

        String id = JsonPath.read(
                f.perform(post(BASE), f.admin, Map.of("canonical", "PC", "terms", List.of("パソコン")))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        mockMvc.perform(delete(BASE + "/" + id).with(user(f.employee)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/" + id).with(user(f.gaEditor)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id).with(user(f.gaEditor))).andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT count(*) FROM synonym_terms")
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(jdbc.sql("SELECT action FROM audit_logs WHERE resource_type = 'SYNONYM_GROUP' ORDER BY id")
                        .query(String.class)
                        .list())
                .containsExactly("SYNONYM_GROUP_CREATED", "SYNONYM_GROUP_DELETED");
    }

    private MvcResult create(String canonical, String... terms) throws Exception {
        return f.perform(post(BASE), f.gaEditor, Map.of("canonical", canonical, "terms", List.of(terms)))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
