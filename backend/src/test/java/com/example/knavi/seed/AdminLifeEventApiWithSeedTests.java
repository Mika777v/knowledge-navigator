package com.example.knavi.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** きっかけの管理 API（E06-T2） */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminLifeEventApiWithSeedTests {

    static final String BASE = "/api/v1/admin/life-events";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SeedLoader loader;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    KnaviUserDetails editor;

    @BeforeEach
    void setUp() {
        loader.load(Path.of("..", "data", "seed"), "demo-password-for-test");
        editor = demoUser("takahashi@example.com");
    }

    @Test
    void listsLifeEventsWithLinkedPrograms() throws Exception {
        String body = mockMvc.perform(get(BASE).with(user(editor)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[*].code"))
                .startsWith("MARRIAGE", "BIRTH")
                .hasSize(8);
        assertThat(JsonPath.<List<String>>read(body, "$[0].programs[?(@.relation == 'PRIMARY')].code"))
                .containsExactly("PRG-MARRIAGE-GIFT", "PRG-MARRIAGE-LEAVE");
    }

    @Test
    void createsLifeEventThatAppearsForEmployees() throws Exception {
        perform(
                        post(BASE),
                        Map.of(
                                "code",
                                "OVERSEAS",
                                "content",
                                content(
                                        "海外赴任",
                                        List.of(
                                                Map.of("programCode", "PRG-BUSINESS-TRIP", "relation", "PRIMARY"),
                                                Map.of(
                                                        "programCode",
                                                        "PRG-PERSONAL-INFO-CHANGE",
                                                        "relation",
                                                        "RELATED")))))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.programs.length()").value(2));

        mockMvc.perform(get("/api/v1/life-events/OVERSEAS").with(user(demoUser("sato@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifeEvent.name").value("海外赴任"))
                .andExpect(jsonPath("$.programs[0].program.code").value("PRG-BUSINESS-TRIP"));
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'LIFE_EVENT_CREATED'")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void updatesWithOptimisticLock() throws Exception {
        String etag = mockMvc.perform(get(BASE + "/MARRIAGE").with(user(editor)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("ETag");
        Map<String, Object> body =
                content("結婚・入籍", List.of(Map.of("programCode", "PRG-MARRIAGE-GIFT", "relation", "PRIMARY")));

        mockMvc.perform(withBody(put(BASE + "/MARRIAGE"), body)).andExpect(status().is(428));
        perform(put(BASE + "/MARRIAGE").header("If-Match", etag), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("結婚・入籍"))
                .andExpect(jsonPath("$.programs.length()").value(1));
        // 古い版での更新は上書きしない
        perform(put(BASE + "/MARRIAGE").header("If-Match", etag), body).andExpect(status().isPreconditionFailed());
        perform(put(BASE + "/NOPE").header("If-Match", "\"0\""), body).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidContent() throws Exception {
        perform(
                        post(BASE),
                        Map.of(
                                "code",
                                "MARRIAGE",
                                "content",
                                content(
                                        "重複",
                                        List.of(Map.of("programCode", "PRG-MARRIAGE-GIFT", "relation", "PRIMARY")))))
                .andExpect(status().isConflict());
        perform(
                        post(BASE),
                        Map.of(
                                "code",
                                "X1",
                                "content",
                                content(
                                        "関連だけ",
                                        List.of(Map.of("programCode", "PRG-MARRIAGE-GIFT", "relation", "RELATED")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("「まず確認する制度」を1つ以上選んでください。"));
        perform(
                        post(BASE),
                        Map.of(
                                "code",
                                "X2",
                                "content",
                                content("存在しない", List.of(Map.of("programCode", "PRG-NOPE", "relation", "PRIMARY")))))
                .andExpect(status().isBadRequest());
        perform(
                        post(BASE),
                        Map.of(
                                "code",
                                "lower",
                                "content",
                                content(
                                        "小文字",
                                        List.of(Map.of("programCode", "PRG-MARRIAGE-GIFT", "relation", "PRIMARY")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyContentEditorsCanManage() throws Exception {
        for (String email : List.of("sato@example.com", "tanaka@example.com", "watanabe@example.com")) {
            mockMvc.perform(get(BASE).with(user(demoUser(email)))).andExpect(status().isForbidden());
        }
    }

    private Map<String, Object> content(String name, List<Map<String, String>> programs) {
        return Map.of("name", name, "icon", "plane", "description", "説明", "sortOrder", 100, "programs", programs);
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(withBody(request, body));
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Object body) {
        return request.with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
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
