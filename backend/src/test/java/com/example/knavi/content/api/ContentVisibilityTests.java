package com.example.knavi.content.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.DocumentImport.ImportedDocument;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公開範囲 × 利用者の閲覧マトリクス（ADR-0004）。
 *
 * <pre>
 * 文書（すべて総務部 GA の所管）
 *   DOC-INTERNAL      社内公開
 *   DOC-MANAGERS      管理職限定
 *   DOC-RESTRICTED    部署限定（access_rules で人事部 HR に許可）
 *   DOC-CONFIDENTIAL  機密（access_rules でユーザー「機密閲覧者」に許可）
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ContentVisibilityTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DocumentImport documentImport;

    @Autowired
    AccessPolicy accessPolicy;

    UUID ga;
    UUID hr;
    Map<String, KnaviUserDetails> users;
    Map<String, UUID> docs;

    @BeforeEach
    void setUp() {
        ga = insertId("INSERT INTO departments (code, name) VALUES ('GA', '総務部') RETURNING id");
        hr = insertId("INSERT INTO departments (code, name) VALUES ('HR', '人事部') RETURNING id");
        jdbc.sql("INSERT INTO categories (slug, name) VALUES ('general', '総務')").update();

        users = Map.of(
                "社員（総務部）", insertUser(ga, "employee@example.com", "EMPLOYEE"),
                "管理職（総務部）", insertUser(ga, "manager@example.com", "MANAGER"),
                "社員（人事部）", insertUser(hr, "hr-employee@example.com", "EMPLOYEE"),
                "所管部署の担当者", insertUser(ga, "ga-editor@example.com", "CONTENT_EDITOR"),
                "他部署の担当者", insertUser(hr, "hr-editor@example.com", "CONTENT_EDITOR"),
                "システム管理者", insertUser(ga, "admin@example.com", "SYSTEM_ADMIN"),
                "機密閲覧者", insertUser(ga, "confidential@example.com", "EMPLOYEE"));

        docs = Map.of(
                "DOC-INTERNAL", importDocument("DOC-INTERNAL", "INTERNAL"),
                "DOC-MANAGERS", importDocument("DOC-MANAGERS", "MANAGERS"),
                "DOC-RESTRICTED", importDocument("DOC-RESTRICTED", "RESTRICTED"),
                "DOC-CONFIDENTIAL", importDocument("DOC-CONFIDENTIAL", "CONFIDENTIAL"));
        accessRule("DOCUMENT", docs.get("DOC-RESTRICTED"), "DEPARTMENT", hr.toString());
        accessRule(
                "DOCUMENT",
                docs.get("DOC-CONFIDENTIAL"),
                "USER",
                users.get("機密閲覧者").id().toString());
    }

    static Stream<Arguments> viewMatrix() {
        return Stream.of(
                Arguments.of("社員（総務部）", List.of("DOC-INTERNAL")),
                Arguments.of("管理職（総務部）", List.of("DOC-INTERNAL", "DOC-MANAGERS")),
                Arguments.of("社員（人事部）", List.of("DOC-INTERNAL", "DOC-RESTRICTED")),
                Arguments.of("所管部署の担当者", List.of("DOC-CONFIDENTIAL", "DOC-INTERNAL", "DOC-MANAGERS", "DOC-RESTRICTED")),
                Arguments.of("他部署の担当者", List.of("DOC-INTERNAL", "DOC-RESTRICTED")),
                Arguments.of("システム管理者", List.of("DOC-INTERNAL")),
                Arguments.of("機密閲覧者", List.of("DOC-CONFIDENTIAL", "DOC-INTERNAL")));
    }

    @ParameterizedTest(name = "{0} に見える文書: {1}")
    @MethodSource("viewMatrix")
    void listShowsOnlyVisibleDocuments(String who, List<String> expected) throws Exception {
        String body = mockMvc.perform(get("/api/v1/documents").with(user(users.get(who))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> codes = JsonPath.read(body, "$[*].code");
        assertThat(codes).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "{0} は見えない文書の詳細を開けない（404）")
    @MethodSource("viewMatrix")
    void detailOfInvisibleDocumentIsNotFound(String who, List<String> visible) throws Exception {
        for (var entry : docs.entrySet()) {
            var expected = visible.contains(entry.getKey()) ? status().isOk() : status().isNotFound();
            mockMvc.perform(get("/api/v1/documents/" + entry.getValue()).with(user(users.get(who))))
                    .andExpect(expected);
        }
    }

    @Test
    void searchPurposeNeverIncludesConfidential() {
        for (var who : List.of("所管部署の担当者", "機密閲覧者")) {
            var predicate =
                    accessPolicy.readPredicate(AccessScope.of(users.get(who)), "documents", "d", Purpose.SEARCH);
            List<String> codes = jdbc.sql(
                            "SELECT d.code FROM documents d WHERE " + predicate.sql() + " ORDER BY d.code")
                    .params(predicate.params())
                    .query(String.class)
                    .list();
            assertThat(codes).as(who).doesNotContain("DOC-CONFIDENTIAL").contains("DOC-INTERNAL");
        }
    }

    @Test
    void draftDocumentsAreNotVisibleEvenToEveryone() {
        jdbc.sql("UPDATE documents SET status = 'ARCHIVED' WHERE code = 'DOC-INTERNAL'")
                .update();
        var predicate =
                accessPolicy.readPredicate(AccessScope.of(users.get("所管部署の担当者")), "documents", "d", Purpose.VIEW);
        List<String> codes = jdbc.sql("SELECT d.code FROM documents d WHERE " + predicate.sql())
                .params(predicate.params())
                .query(String.class)
                .list();
        assertThat(codes).doesNotContain("DOC-INTERNAL");
    }

    @Test
    void programBasisIsFilteredPerDocument() throws Exception {
        // 人事部に限定した制度が、人事部には見えない（総務部限定の）文書を根拠にしている場合、
        // 制度は見えても根拠の文書名・条は表示しない
        UUID gaOnlyDoc = importDocument("DOC-GA-ONLY", "RESTRICTED");
        accessRule("DOCUMENT", gaOnlyDoc, "DEPARTMENT", ga.toString());
        UUID program = insertId("""
                INSERT INTO programs (code, name, summary, owner_department_id, visibility, status, search_text)
                VALUES ('PRG-HR-ONLY', '人事部限定の制度', '説明', '%s', 'RESTRICTED', 'PUBLISHED', '人事部限定の制度')
                RETURNING id
                """.formatted(ga));
        accessRule("PROGRAM", program, "DEPARTMENT", hr.toString());
        for (var doc : List.of(docs.get("DOC-INTERNAL"), gaOnlyDoc)) {
            jdbc.sql("INSERT INTO program_basis (program_id, document_id) VALUES (:p, :d)")
                    .param("p", program)
                    .param("d", doc)
                    .update();
        }

        mockMvc.perform(get("/api/v1/programs/" + program).with(user(users.get("社員（人事部）"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis.length()").value(1))
                .andExpect(jsonPath("$.basis[0].documentCode").value("DOC-INTERNAL"));
        mockMvc.perform(get("/api/v1/programs/" + program).with(user(users.get("社員（総務部）"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void sectionOfInvisibleDocumentIsNotFound() throws Exception {
        UUID section = jdbc.sql("""
                        SELECT s.id FROM sections s JOIN documents d ON d.current_version_id = s.document_version_id
                        WHERE d.code = 'DOC-MANAGERS' AND s.ordinal = 0
                        """).query(UUID.class).single();

        mockMvc.perform(get("/api/v1/sections/" + section).with(user(users.get("社員（総務部）"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sections/" + section).with(user(users.get("管理職（総務部）"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.section.articleNo").value("第1条"))
                .andExpect(jsonPath("$.currentVersion").value(true));
    }

    private UUID importDocument(String code, String visibility) {
        return documentImport
                .importPublished(new ImportedDocument(
                        code,
                        code,
                        null,
                        "RULE",
                        "general",
                        ga,
                        visibility,
                        "v1.0",
                        LocalDate.of(2026, 4, 1),
                        "### 第1条（目的）\n\n" + code + " の目的。\n"))
                .documentId();
    }

    private void accessRule(String resourceType, UUID resourceId, String principalType, String principalId) {
        jdbc.sql("""
                        INSERT INTO access_rules (resource_type, resource_id, principal_type, principal_id)
                        VALUES (:rt, :rid, :pt, :pid)
                        """)
                .param("rt", resourceType)
                .param("rid", resourceId)
                .param("pt", principalType)
                .param("pid", principalId)
                .update();
    }

    private KnaviUserDetails insertUser(UUID department, String email, String role) {
        UUID id = jdbc.sql(
                        "INSERT INTO users (email, display_name, department_id, role) VALUES (:e, :e, :d, :r) RETURNING id")
                .param("e", email)
                .param("d", department)
                .param("r", role)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, department, email, null, role, true);
    }

    private UUID insertId(String sql) {
        return jdbc.sql(sql).query(UUID.class).single();
    }
}
