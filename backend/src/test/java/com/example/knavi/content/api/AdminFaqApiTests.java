package com.example.knavi.content.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.content.DocumentImport;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminFaqApiTests {

    static final String BASE = "/api/v1/admin/faqs";

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
    void createsFaqWithLinksToSectionsAndPublishes() throws Exception {
        String id = create("FAQ-001", content("GA-GENERAL", "INTERNAL", "REG-GA-001", "第5条"));

        f.perform(post(BASE + "/" + id + "/publish").header("If-Match", "\"0\""), f.gaEditor, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.contactCode").value("GA-GENERAL"))
                .andExpect(jsonPath("$.links[0].targetType").value("SECTION"))
                // 管理画面で編集し直せるよう、更新時に送る形（文書コード・条番号）と表示名も返す
                .andExpect(jsonPath("$.links[0].documentCode").value("REG-GA-001"))
                .andExpect(jsonPath("$.links[0].articleNo").value("第5条"))
                .andExpect(jsonPath("$.links[0].label").value(org.hamcrest.Matchers.endsWith("第5条")));
    }

    @Test
    void publishRequiresContactOrRelatedProgram() throws Exception {
        String id = create("FAQ-002", content(null, "INTERNAL", "REG-GA-001", null));

        f.perform(post(BASE + "/" + id + "/publish").header("If-Match", "\"0\""), f.gaEditor, Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("公開する FAQ には窓口か関連制度のどちらかが必要です。"));
    }

    @Test
    void visibilityMustNotBeWiderThanLinkedDocuments() throws Exception {
        String id = create("FAQ-003", content("GA-GENERAL", "INTERNAL", "REG-X-MGR", "第1条"));
        f.perform(post(BASE + "/" + id + "/publish").header("If-Match", "\"0\""), f.gaEditor, Map.of())
                .andExpect(status().isConflict());

        f.perform(
                        put(BASE + "/" + id).header("If-Match", "\"0\""),
                        f.gaEditor,
                        content("GA-GENERAL", "MANAGERS", "REG-X-MGR", "第1条"))
                .andExpect(status().isOk());
        f.perform(post(BASE + "/" + id + "/publish").header("If-Match", "\"1\""), f.gaEditor, Map.of())
                .andExpect(status().isOk());
    }

    @Test
    void rejectsStaleRowVersionAndOtherDepartments() throws Exception {
        String id = create("FAQ-004", content("GA-GENERAL", "INTERNAL", "REG-GA-001", null));

        f.perform(
                        put(BASE + "/" + id).header("If-Match", "\"7\""),
                        f.gaEditor,
                        content("GA-GENERAL", "INTERNAL", "REG-GA-001", null))
                .andExpect(status().isPreconditionFailed());
        f.perform(
                        put(BASE + "/" + id).header("If-Match", "\"0\""),
                        f.hrEditor,
                        content("GA-GENERAL", "INTERNAL", "REG-GA-001", null))
                .andExpect(status().isForbidden());
    }

    private String create(String code, Map<String, Object> content) throws Exception {
        String body = f.perform(
                        post(BASE), f.gaEditor, Map.of("code", code, "ownerDepartmentCode", "GA", "content", content))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static Map<String, Object> content(String contact, String visibility, String document, String article) {
        var link = new HashMap<String, Object>();
        link.put("documentCode", document);
        link.put("articleNo", article);
        var body = new HashMap<String, Object>();
        body.put("question", "入籍前に結婚祝金を申請できますか？");
        body.put("answerMarkdown", "申請は入籍後に行います。");
        body.put("categorySlug", "welfare-ceremony");
        body.put("contactCode", contact);
        body.put("visibility", visibility);
        body.put("links", List.of(link));
        return body;
    }
}
