package com.example.knavi.ingestion.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.api.AdminApiFixture;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/** 社員が文書の原本（PDF）を開く。文書と同じ公開範囲で絞り込まれ、見えない文書の原本は 404 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers
@Transactional
class SourceFileApiTests {

    static final String ACCESS_KEY = "knavi-test";
    static final String SECRET_KEY = "knavi-test-secret";

    @Container
    static final GenericContainer<?> STORAGE = new GenericContainer<>("chrislusf/seaweedfs:4.48")
            .withCommand("server", "-s3", "-dir=/data", "-ip.bind=0.0.0.0", "-volume.max=0")
            .withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
            .withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCode(403));

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("knavi.storage.endpoint", () -> "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(8333));
        registry.add("knavi.storage.access-key", () -> ACCESS_KEY);
        registry.add("knavi.storage.secret-key", () -> SECRET_KEY);
        registry.add("knavi.storage.create-bucket", () -> "true");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    DocumentImport documentImport;

    AdminApiFixture f;
    UUID internal;
    UUID managersOnly;
    byte[] pdf;

    @BeforeEach
    void setUp() throws Exception {
        f = new AdminApiFixture(mockMvc, jdbc, json, documentImport);
        internal = f.idOf("documents", "REG-GA-001");
        managersOnly = f.idOf("documents", "REG-X-MGR");
        pdf = ("%PDF-1.4\n% 原本 " + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
        String fileId = JsonPath.read(
                mockMvc.perform(multipart("/api/v1/admin/files")
                                .file(new MockMultipartFile("file", "慶弔見舞金規程.pdf", "application/pdf", pdf))
                                .with(user(f.gaEditor))
                                .with(csrf()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        // PDF から取り込んだ公開版の状態を作る（取り込みジョブは IngestionJobTests で確かめる）
        for (UUID document : new UUID[] {internal, managersOnly}) {
            jdbc.sql("UPDATE document_versions SET source_file_id = :f::uuid"
                            + " WHERE id = (SELECT current_version_id FROM documents WHERE id = :d)")
                    .param("f", fileId)
                    .param("d", document)
                    .update();
        }
        jdbc.sql("UPDATE sections SET page_from = 1, page_to = 2 WHERE article_no = '第5条'"
                        + " AND document_version_id = (SELECT current_version_id FROM documents WHERE id = :d)")
                .param("d", internal)
                .update();
    }

    @Test
    void employeesOpenTheOriginalOfDocumentsTheyCanView() throws Exception {
        byte[] served = mockMvc.perform(
                        get("/api/v1/documents/" + internal + "/source").with(user(f.employee)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(served).isEqualTo(pdf);

        // 詳細には、原本があることと条ごとのページが出る
        mockMvc.perform(get("/api/v1/documents/" + internal).with(user(f.employee)))
                .andExpect(jsonPath("$.version.hasSourceFile").value(true))
                .andExpect(
                        jsonPath("$.sections[?(@.articleNo == '第5条')].pageFrom").value(1))
                .andExpect(
                        jsonPath("$.sections[?(@.articleNo == '第5条')].pageTo").value(2));
    }

    @Test
    void theOriginalOfAnInvisibleDocumentIsNotFound() throws Exception {
        // 管理職限定の文書の原本は、一般社員には「存在しない」と同じ 404（ADR-0004）
        mockMvc.perform(get("/api/v1/documents/" + managersOnly + "/source").with(user(f.employee)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/documents/" + managersOnly + "/source").with(user(manager())))
                .andExpect(status().isOk());
    }

    @Test
    void versionsWithoutAnOriginalAreNotFound() throws Exception {
        jdbc.sql("UPDATE document_versions SET source_file_id = NULL WHERE document_id = :d")
                .param("d", internal)
                .update();

        mockMvc.perform(get("/api/v1/documents/" + internal + "/source").with(user(f.employee)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/documents/" + internal).with(user(f.employee)))
                .andExpect(jsonPath("$.version.hasSourceFile").value(false));
    }

    private KnaviUserDetails manager() {
        String email = "manager-" + UUID.randomUUID() + "@example.com";
        UUID id = jdbc.sql("INSERT INTO users (email, display_name, role) VALUES (:e, :e, 'MANAGER') RETURNING id")
                .param("e", email)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, null, email, null, "MANAGER", true);
    }
}
