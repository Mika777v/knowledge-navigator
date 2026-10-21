package com.example.knavi.content.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AdminProgramApiTests {

    static final String BASE = "/api/v1/admin/programs";

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
    void createsDraftProgram() throws Exception {
        f.perform(post(BASE), f.gaEditor, createBody("PRG-MARRIAGE-GIFT", "INTERNAL"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.ownerDepartment.code").value("GA"))
                .andExpect(jsonPath("$.basis.length()").value(0));
    }

    @Test
    void resolvesBasisArticlesFromThePublishedVersion() throws Exception {
        String id = create("PRG-MARRIAGE-GIFT", "INTERNAL");

        String etag = etag(f.perform(
                        put(BASE + "/" + id + "/basis").header("If-Match", "\"0\""),
                        f.gaEditor,
                        basis("REG-GA-001", "第5条", "第3条"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis[0].articleNo").value("第5条"))
                .andExpect(jsonPath("$.basis[0].heading").value("結婚祝金"))
                .andExpect(jsonPath("$.basis[0].sectionId").isNotEmpty())
                .andExpect(jsonPath("$.basis[1].heading").value("申請方法")));
        // 根拠の変更でも版が上がる（同時編集を検知するため）
        org.assertj.core.api.Assertions.assertThat(etag).isNotEqualTo("\"0\"");

        f.perform(put(BASE + "/" + id + "/basis").header("If-Match", etag), f.gaEditor, basis("REG-GA-001", "第99条"))
                .andExpect(status().isBadRequest());
        f.perform(put(BASE + "/" + id + "/basis").header("If-Match", etag), f.gaEditor, basis("REG-NOPE", "第1条"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishRequiresContactsAndBasis() throws Exception {
        String id = create("PRG-MARRIAGE-GIFT", "INTERNAL");
        f.perform(post(BASE + "/" + id + "/publish").header("If-Match", "\"0\""), f.gaEditor, Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("公開する制度には窓口が1件以上必要です。"));

        String etag = etag(f.perform(
                        put(BASE + "/" + id + "/contacts").header("If-Match", "\"0\""),
                        f.gaEditor,
                        contacts("GA-GENERAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts[0].name").value("総務部 庶務グループ"))
                .andExpect(jsonPath("$.contacts[0].role").value("APPLY")));
        f.perform(post(BASE + "/" + id + "/publish").header("If-Match", etag), f.gaEditor, Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("公開する制度には根拠（文書の条）が1件以上必要です。"));

        etag = etag(f.perform(
                        put(BASE + "/" + id + "/basis").header("If-Match", etag),
                        f.gaEditor,
                        basis("REG-GA-001", "第5条"))
                .andExpect(status().isOk()));
        f.perform(post(BASE + "/" + id + "/publish").header("If-Match", etag), f.gaEditor, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void visibilityMustNotBeWiderThanTheBasisDocument() throws Exception {
        String id = create("PRG-MGR", "INTERNAL");
        String etag = etag(f.perform(
                put(BASE + "/" + id + "/contacts").header("If-Match", "\"0\""), f.gaEditor, contacts("GA-GENERAL")));
        etag = etag(f.perform(
                put(BASE + "/" + id + "/basis").header("If-Match", etag), f.gaEditor, basis("REG-X-MGR", "第1条")));

        // 社内公開の制度が管理職限定の文書を根拠にすると、制度経由で文書の存在が見えてしまう
        f.perform(post(BASE + "/" + id + "/publish").header("If-Match", etag), f.gaEditor, Map.of())
                .andExpect(status().isConflict());

        etag = etag(f.perform(
                        put(BASE + "/" + id).header("If-Match", etag),
                        f.gaEditor,
                        new HashMap<>(attributes("MANAGERS")))
                .andExpect(status().isOk()));
        f.perform(post(BASE + "/" + id + "/publish").header("If-Match", etag), f.gaEditor, Map.of())
                .andExpect(status().isOk());
    }

    @Test
    void publishedProgramMustKeepAtLeastOneContact() throws Exception {
        String[] published = publishedProgram("PRG-MARRIAGE-GIFT");

        f.perform(
                        put(BASE + "/" + published[0] + "/contacts").header("If-Match", published[1]),
                        f.gaEditor,
                        Map.of("contacts", List.of()))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyEditorsOfTheOwnerDepartmentCanAccess() throws Exception {
        String id = create("PRG-MARRIAGE-GIFT", "INTERNAL");

        mockMvc.perform(get(BASE + "/" + id).with(user(f.hrEditor))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id).with(user(f.employee))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id).with(user(f.admin))).andExpect(status().isForbidden());
        f.perform(post(BASE), f.hrEditor, createBody("PRG-X", "INTERNAL")).andExpect(status().isForbidden());
    }

    @Test
    void rejectsStaleRowVersionAndArchiveOfDraft() throws Exception {
        String id = create("PRG-MARRIAGE-GIFT", "INTERNAL");

        f.perform(put(BASE + "/" + id).header("If-Match", "\"5\""), f.gaEditor, attributes("INTERNAL"))
                .andExpect(status().isPreconditionFailed());
        f.perform(post(BASE + "/" + id + "/archive").header("If-Match", "\"0\""), f.gaEditor, Map.of())
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDuplicateCodeAndUnknownContact() throws Exception {
        String id = create("PRG-MARRIAGE-GIFT", "INTERNAL");
        f.perform(post(BASE), f.gaEditor, createBody("PRG-MARRIAGE-GIFT", "INTERNAL"))
                .andExpect(status().isConflict());
        f.perform(put(BASE + "/" + id + "/contacts").header("If-Match", "\"0\""), f.gaEditor, contacts("NO-SUCH"))
                .andExpect(status().isBadRequest());
    }

    /** @return [id, 公開後の ETag] */
    String[] publishedProgram(String code) throws Exception {
        String id = create(code, "INTERNAL");
        String etag = etag(f.perform(
                put(BASE + "/" + id + "/contacts").header("If-Match", "\"0\""), f.gaEditor, contacts("GA-GENERAL")));
        etag = etag(f.perform(
                put(BASE + "/" + id + "/basis").header("If-Match", etag), f.gaEditor, basis("REG-GA-001", "第5条")));
        etag = etag(f.perform(post(BASE + "/" + id + "/publish").header("If-Match", etag), f.gaEditor, Map.of())
                .andExpect(status().isOk()));
        return new String[] {id, etag};
    }

    private static String etag(org.springframework.test.web.servlet.ResultActions result) {
        return result.andReturn().getResponse().getHeader("ETag");
    }

    private String create(String code, String visibility) throws Exception {
        String body = f.perform(post(BASE), f.gaEditor, createBody(code, visibility))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static Map<String, Object> createBody(String code, String visibility) {
        return Map.of("code", code, "ownerDepartmentCode", "GA", "attributes", attributes(visibility));
    }

    private static Map<String, Object> attributes(String visibility) {
        return Map.of(
                "name", "結婚祝金",
                "summary", "社員が結婚したときに支給されるお祝い金。",
                "benefit", "30,000円",
                "categorySlug", "welfare-ceremony",
                "visibility", visibility);
    }

    private static Map<String, Object> basis(String documentCode, String... articles) {
        return Map.of(
                "basis",
                java.util.Arrays.stream(articles)
                        .map(a -> Map.of("documentCode", documentCode, "articleNo", a))
                        .toList());
    }

    private static Map<String, Object> contacts(String code) {
        return Map.of("contacts", List.of(Map.of("contactCode", code, "role", "APPLY")));
    }
}
