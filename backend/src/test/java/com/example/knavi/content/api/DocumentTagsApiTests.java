package com.example.knavi.content.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.content.DocumentImport;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DocumentTagsApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    DocumentImport documentImport;

    @Autowired
    jakarta.persistence.EntityManager entityManager;

    AdminApiFixture f;
    UUID ga001;
    UUID managersOnly;

    @BeforeEach
    void setUp() throws Exception {
        f = new AdminApiFixture(mockMvc, jdbc, json, documentImport);
        ga001 = f.idOf("documents", "REG-GA-001");
        managersOnly = f.idOf("documents", "REG-X-MGR");
    }

    @Test
    void editorReplacesTagsWithNormalizedNamesAndANewVersion() throws Exception {
        String v0 = etag(ga001);
        // 全角の英数字・前後の空白・重複は正規化して1つにする。無いタグは自由タグとして作る
        String v1 = update(ga001, v0, List.of(" お金 ", "家族", "お金", "ＦＡＱ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags").value(org.hamcrest.Matchers.contains("FAQ", "お金", "家族")))
                .andReturn()
                .getResponse()
                .getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(v1).isNotEqualTo(v0);

        // タグだけを変えても版が上がるので、古い版での更新は 412 になる
        String v2 = update(ga001, v1, List.of("お金"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(v2).isNotEqualTo(v1);
        update(ga001, v1, List.of("家族")).andExpect(status().isPreconditionFailed());

        // 省略すると変えない。空の配列ですべて外す
        update(ga001, v2, null).andExpect(jsonPath("$.tags").value(org.hamcrest.Matchers.contains("お金")));
        update(ga001, etag(ga001), List.of()).andExpect(jsonPath("$.tags").isEmpty());
    }

    private String etag(UUID id) throws Exception {
        return mockMvc.perform(get("/api/v1/admin/documents/" + id).with(user(f.gaEditor)))
                .andReturn()
                .getResponse()
                .getHeader("ETag");
    }

    @Test
    void rejectsTooManyOrTooLongTags() throws Exception {
        update(ga001, etag(ga001), List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"))
                .andExpect(status().isBadRequest());
        update(ga001, etag(ga001), List.of("あ".repeat(51))).andExpect(status().isBadRequest());
    }

    @Test
    void readersSeeTagsAndCountsOnlyForDocumentsTheyCanView() throws Exception {
        setTags(ga001, "お金", "家族");
        setTags(managersOnly, "お金", "管理職");
        KnaviUserDetails manager = insertManager();

        mockMvc.perform(get("/api/v1/documents/" + ga001).with(user(f.employee)))
                .andExpect(jsonPath("$.document.tags").value(org.hamcrest.Matchers.contains("お金", "家族")));

        // 一般社員には、管理職限定の文書の分は数えない（件数から見えない文書の存在が分からないように）
        mockMvc.perform(get("/api/v1/tags").with(user(f.employee)))
                .andExpect(jsonPath("$[?(@.name == 'お金')].documentCount").value(1))
                .andExpect(jsonPath("$[?(@.name == '管理職')]").isEmpty());
        mockMvc.perform(get("/api/v1/tags").with(user(manager)))
                .andExpect(jsonPath("$[?(@.name == 'お金')].documentCount").value(2))
                .andExpect(jsonPath("$[?(@.name == '管理職')].documentCount").value(1));

        mockMvc.perform(get("/api/v1/documents").param("tag", "お金").with(user(f.employee)))
                .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.contains("REG-GA-001")));
        mockMvc.perform(get("/api/v1/documents").param("tag", "お金").with(user(manager)))
                .andExpect(jsonPath("$[*].code")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("REG-GA-001", "REG-X-MGR")));
    }

    @Test
    void onlyTheOwningDepartmentCanChangeTags() throws Exception {
        f.perform(
                        put("/api/v1/admin/documents/" + ga001).header("If-Match", etag(ga001)),
                        f.hrEditor,
                        body(List.of("お金")))
                .andExpect(status().isForbidden());
    }

    private ResultActions update(UUID id, String ifMatch, List<String> tags) throws Exception {
        // 本番ではリクエストごとに JPA の持続性コンテキストが分かれる。テストのトランザクションの中でも同じにする
        entityManager.flush();
        entityManager.clear();
        return f.perform(put("/api/v1/admin/documents/" + id).header("If-Match", ifMatch), f.gaEditor, body(tags));
    }

    private static Map<String, Object> body(List<String> tags) {
        var body = new HashMap<String, Object>();
        body.put("title", "慶弔見舞金規程");
        body.put("documentType", "REGULATION");
        body.put("categorySlug", "welfare-ceremony");
        body.put("visibility", "INTERNAL");
        body.put("tags", tags);
        return body;
    }

    private void setTags(UUID documentId, String... names) {
        for (String name : names) {
            jdbc.sql("INSERT INTO tags (name) VALUES (:n) ON CONFLICT (name) DO NOTHING")
                    .param("n", name)
                    .update();
            jdbc.sql("INSERT INTO document_tags (document_id, tag_id) SELECT :d, id FROM tags WHERE name = :n")
                    .param("d", documentId)
                    .param("n", name)
                    .update();
        }
    }

    private KnaviUserDetails insertManager() {
        String email = "manager-" + UUID.randomUUID() + "@example.com";
        UUID id = jdbc.sql("INSERT INTO users (email, display_name, role) VALUES (:e, :e, 'MANAGER') RETURNING id")
                .param("e", email)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, null, email, null, "MANAGER", true);
    }
}
