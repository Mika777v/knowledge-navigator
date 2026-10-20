package com.example.knavi.content.api;

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
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminDocumentApiTests {

    static final String BASE = "/api/v1/admin/documents";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    jakarta.persistence.EntityManager entityManager;

    KnaviUserDetails gaEditor;
    KnaviUserDetails hrEditor;
    KnaviUserDetails employee;
    KnaviUserDetails admin;

    @BeforeEach
    void setUp() {
        UUID ga = insertDepartment("GA", "総務部");
        UUID hr = insertDepartment("HR", "人事部");
        jdbc.sql("INSERT INTO categories (slug, name) VALUES ('welfare-ceremony', '慶弔')")
                .update();
        gaEditor = insertUser(ga, "ito@example.com", "CONTENT_EDITOR");
        hrEditor = insertUser(hr, "takahashi@example.com", "CONTENT_EDITOR");
        employee = insertUser(ga, "sato@example.com", "EMPLOYEE");
        admin = insertUser(ga, "watanabe@example.com", "SYSTEM_ADMIN");
    }

    @Test
    void createsDraftDocumentWithETagAndAuditLog() throws Exception {
        perform(post(BASE), gaEditor, createBody("REG-GA-001"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.ownerDepartment.code").value("GA"))
                .andExpect(jsonPath("$.category.slug").value("welfare-ceremony"));

        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'DOCUMENT_CREATED'")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void splitsVersionBodyIntoSectionsWithNormalizedSearchText() throws Exception {
        String id = createDocument("REG-GA-001");
        String markdown = Files.readString(seedDocument("REG-GA-001_"));

        String versionId = createVersion(id, "v2.1", markdown)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sections.length()").value(16)) // 15条 + 附則
                .andExpect(jsonPath("$.sections[4].articleNo").value("第5条"))
                .andExpect(jsonPath("$.sections[4].heading").value("結婚祝金"))
                .andExpect(jsonPath("$.sections[4].path").value("2/5"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        entityManager.flush(); // JPA の未反映の変更を DB に書き出してから JDBC で読む
        String searchText = jdbc.sql("SELECT search_text FROM sections WHERE document_version_id = :v AND ordinal = 4")
                .param("v", UUID.fromString(JsonPath.read(versionId, "$.id")))
                .query(String.class)
                .single();
        assertThat(searchText).startsWith("慶弔見舞金規程 第5条 結婚祝金").contains("30,000円");
    }

    @Test
    void publishesVersionWithOptimisticLock() throws Exception {
        String id = createDocument("REG-GA-001");
        String versionId = versionId(createVersion(id, "v1.0", "### 第1条（目的）\n\n目的。\n"));

        perform(post(BASE + "/" + id + "/publish"), gaEditor, Map.of("versionId", versionId))
                .andExpect(status().isPreconditionRequired());
        perform(
                        post(BASE + "/" + id + "/publish").header("If-Match", "\"9\""),
                        gaEditor,
                        Map.of("versionId", versionId))
                .andExpect(status().isPreconditionFailed());

        perform(
                        post(BASE + "/" + id + "/publish").header("If-Match", "\"0\""),
                        gaEditor,
                        Map.of("versionId", versionId))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.currentVersionId").value(versionId));
    }

    @Test
    void publishedVersionCannotBeEditedButNewVersionCanReplaceIt() throws Exception {
        String id = createDocument("REG-GA-001");
        String v1 = versionId(createVersion(id, "v1.0", "### 第1条（目的）\n\n旧。\n"));
        publish(id, v1, 0);

        perform(
                        put(BASE + "/" + id + "/versions/" + v1),
                        gaEditor,
                        Map.of("effectiveFrom", "2026-04-01", "bodyMarkdown", "### 第1条（目的）\n\n改ざん。\n"))
                .andExpect(status().isConflict());

        String v2 = versionId(createVersion(id, "v2.0", "### 第1条（目的）\n\n新。\n\n### 第2条（定義）\n\n定義。\n"));
        publish(id, v2, 1);

        mockMvc.perform(get(BASE + "/" + id + "/versions").with(user(gaEditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '%s')].current".formatted(v2)).value(true))
                .andExpect(jsonPath("$[?(@.id == '%s')].current".formatted(v1)).value(false))
                .andExpect(
                        jsonPath("$[?(@.id == '%s')].publishedAt".formatted(v1)).isNotEmpty());
    }

    @Test
    void draftVersionCanBeEditedAndIsReSplit() throws Exception {
        String id = createDocument("REG-GA-001");
        String v1 = versionId(createVersion(id, "v1.0", "### 第1条（目的）\n\n目的。\n"));

        perform(
                        put(BASE + "/" + id + "/versions/" + v1),
                        gaEditor,
                        Map.of(
                                "effectiveFrom",
                                "2026-04-01",
                                "bodyMarkdown",
                                "### 第1条（目的）\n\n目的。\n\n### 第2条（対象者）\n\n全社員。\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andExpect(jsonPath("$.sections[1].heading").value("対象者"));
    }

    @Test
    void cannotPublishVersionOfAnotherDocument() throws Exception {
        String a = createDocument("REG-GA-001");
        String b = createDocument("REG-GA-002");
        String versionOfB = versionId(createVersion(b, "v1.0", "### 第1条（目的）\n\n目的。\n"));

        perform(
                        post(BASE + "/" + a + "/publish").header("If-Match", "\"0\""),
                        gaEditor,
                        Map.of("versionId", versionOfB))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyEditorsOfTheOwnerDepartmentCanAccess() throws Exception {
        String id = createDocument("REG-GA-001");

        mockMvc.perform(get(BASE + "/" + id).with(user(hrEditor))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id).with(user(employee))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id).with(user(admin))).andExpect(status().isForbidden());
        perform(post(BASE), hrEditor, createBody("REG-GA-009")).andExpect(status().isForbidden());

        mockMvc.perform(get(BASE).with(user(hrEditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(BASE).with(user(gaEditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("REG-GA-001"));
    }

    @Test
    void archiveRequiresPublishedDocument() throws Exception {
        String id = createDocument("REG-GA-001");
        perform(post(BASE + "/" + id + "/archive").header("If-Match", "\"0\""), gaEditor, Map.of())
                .andExpect(status().isConflict());

        String v1 = versionId(createVersion(id, "v1.0", "### 第1条（目的）\n\n目的。\n"));
        publish(id, v1, 0);
        perform(post(BASE + "/" + id + "/archive").header("If-Match", "\"1\""), gaEditor, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void rejectsDuplicateCodeAndUnknownCategory() throws Exception {
        createDocument("REG-GA-001");
        perform(post(BASE), gaEditor, createBody("REG-GA-001")).andExpect(status().isConflict());

        var body = new java.util.HashMap<>(createBody("REG-GA-002"));
        body.put("categorySlug", "no-such-category");
        perform(post(BASE), gaEditor, body).andExpect(status().isBadRequest());
    }

    private Map<String, Object> createBody(String code) {
        return Map.of(
                "code", code,
                "title", "慶弔見舞金規程",
                "documentType", "REGULATION",
                "categorySlug", "welfare-ceremony",
                "ownerDepartmentCode", "GA",
                "visibility", "INTERNAL");
    }

    private String createDocument(String code) throws Exception {
        String body = perform(post(BASE), gaEditor, createBody(code))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions createVersion(String documentId, String label, String markdown) throws Exception {
        return perform(
                post(BASE + "/" + documentId + "/versions"),
                gaEditor,
                Map.of("versionLabel", label, "effectiveFrom", "2026-04-01", "bodyMarkdown", markdown));
    }

    private String versionId(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void publish(String documentId, String versionId, int rowVersion) throws Exception {
        perform(
                        post(BASE + "/" + documentId + "/publish").header("If-Match", "\"" + rowVersion + "\""),
                        gaEditor,
                        Map.of("versionId", versionId))
                .andExpect(status().isOk());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, KnaviUserDetails as, Object body)
            throws Exception {
        return mockMvc.perform(request.with(user(as))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    /** ファイル名に日本語を含むため、OS のロケールに依存しないようにコードの接頭辞で探す */
    private static Path seedDocument(String prefix) throws java.io.IOException {
        try (var files = Files.list(Path.of("..", "data", "seed", "documents"))) {
            return files.filter(p -> p.getFileName().toString().startsWith(prefix))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private KnaviUserDetails insertUser(UUID department, String email, String role) {
        UUID id = jdbc.sql("""
                        INSERT INTO users (email, display_name, department_id, role)
                        VALUES (:email, :email, :dept, :role) RETURNING id
                        """)
                .param("email", email)
                .param("dept", department)
                .param("role", role)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, department, email, null, role, true);
    }

    private UUID insertDepartment(String code, String name) {
        return jdbc.sql("INSERT INTO departments (code, name) VALUES (:code, :name) RETURNING id")
                .param("code", code)
                .param("name", name)
                .query(UUID.class)
                .single();
    }
}
