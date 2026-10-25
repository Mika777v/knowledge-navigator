package com.example.knavi.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/**
 * PDF の取り込みを、受付からワーカーの処理まで通して確かめる。
 * ワーカーは別のトランザクションでジョブを取り出すため、テスト用のトランザクションは使わずに実際にコミットし、終わったら消す。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers
class IngestionJobTests {

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
    IngestionWorker worker;

    /** 一時的なエラーを起こすために、解析だけを差し替えられるようにする（ふだんは本物の処理を呼ぶ） */
    @MockitoSpyBean
    PdfParseService parser;

    UUID dept;
    UUID otherDept;
    UUID category;
    UUID document;
    KnaviUserDetails editor;
    KnaviUserDetails otherEditor;

    @BeforeEach
    void setUp() {
        dept = id("INSERT INTO departments (code, name) VALUES ('IG', '取込テスト部') RETURNING id");
        otherDept = id("INSERT INTO departments (code, name) VALUES ('IG2', '取込テスト二部') RETURNING id");
        category = id("INSERT INTO categories (slug, name) VALUES ('ingest-test', '取込テスト') RETURNING id");
        editor = insertEditor(dept, "ig-editor@example.com");
        otherEditor = insertEditor(otherDept, "ig-other@example.com");
        document = id("""
                INSERT INTO documents (code, title, document_type, category_id, owner_department_id)
                VALUES ('REG-IG-001', '慶弔見舞金規程（取込）', 'REGULATION', '%s', '%s') RETURNING id
                """.formatted(category, dept));
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM ingestion_jobs WHERE document_id = :d")
                .param("d", document)
                .update();
        jdbc.sql("UPDATE documents SET current_version_id = NULL WHERE id = :d")
                .param("d", document)
                .update();
        jdbc.sql("DELETE FROM document_versions WHERE document_id = :d")
                .param("d", document)
                .update();
        jdbc.sql("DELETE FROM documents WHERE id = :d").param("d", document).update();
        jdbc.sql("DELETE FROM stored_files WHERE uploaded_by IN (:u)")
                .param("u", List.of(editor.id(), otherEditor.id()))
                .update();
        jdbc.sql("DELETE FROM users WHERE id IN (:u)")
                .param("u", List.of(editor.id(), otherEditor.id()))
                .update();
        jdbc.sql("DELETE FROM categories WHERE id = :c").param("c", category).update();
        jdbc.sql("DELETE FROM departments WHERE id IN (:d)")
                .param("d", List.of(dept, otherDept))
                .update();
    }

    @Test
    void createsADraftVersionWithPageNumbersFromAnUploadedPdf() throws Exception {
        String fileId = upload(regulationPdf("REG-GA-001_"));

        String jobId = JsonPath.read(
                submit(fileId, "v1.0", editor)
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.status").value("QUEUED"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        assertThat(worker.runOnce()).isTrue();
        assertThat(worker.runOnce()).isFalse();

        String versionId = JsonPath.read(
                mockMvc.perform(get("/api/v1/admin/ingestion-jobs/" + jobId).with(user(editor)))
                        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                        .andExpect(jsonPath("$.attempts").value(1))
                        .andExpect(jsonPath("$.fileName").value("規程.pdf"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.resultVersionId");

        // 原本を結び付けた下書きの版ができ、条ごとにページ範囲が付いている
        var version = jdbc.sql("""
                        SELECT source_file_id::text AS file, published_at IS NULL AS draft, body_markdown
                        FROM document_versions WHERE id = :v
                        """)
                .param("v", UUID.fromString(versionId))
                .query((rs, i) -> Map.of(
                        "file", rs.getString("file"),
                        "draft", rs.getBoolean("draft"),
                        "body", rs.getString("body_markdown")))
                .single();
        assertThat(version).containsEntry("file", fileId).containsEntry("draft", true);
        assertThat((String) version.get("body")).contains("### 第5条（結婚祝金）");
        var pages = jdbc.sql("""
                        SELECT article_no, page_from, page_to FROM sections WHERE document_version_id = :v
                        AND article_no IN ('第1条', '第7条', '第15条') ORDER BY ordinal
                        """)
                .param("v", UUID.fromString(versionId))
                .query((rs, i) -> rs.getString(1) + ":" + rs.getInt(2) + "-" + rs.getInt(3))
                .list();
        assertThat(pages).containsExactly("第1条:1-1", "第7条:2-2", "第15条:3-3");

        mockMvc.perform(get("/api/v1/admin/documents/" + document + "/ingestion-jobs")
                        .with(user(editor)))
                .andExpect(jsonPath("$[0].id").value(jobId));
    }

    @Test
    void unreadablePdfsFailOnceWithAReason() throws Exception {
        String fileId = upload(("%PDF-1.4\nこわれたファイル " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        String jobId = JsonPath.read(
                submit(fileId, "v1.0", editor).andReturn().getResponse().getContentAsString(), "$.id");

        worker.runOnce();

        // 内容の問題は、やり直しても変わらないので再試行しない
        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/" + jobId).with(user(editor)))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("壊れている可能性")))
                .andExpect(jsonPath("$.resultVersionId").doesNotExist());
        assertThat(worker.runOnce()).isFalse();
    }

    @Test
    void anExistingVersionLabelFailsWithoutTouchingTheDocument() throws Exception {
        String fileId = upload(regulationPdf("REG-GA-001_"));
        submit(fileId, "v1.0", editor);
        worker.runOnce();
        String second = JsonPath.read(
                submit(fileId, "v1.0", editor).andReturn().getResponse().getContentAsString(), "$.id");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/" + second).with(user(editor)))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.startsWith("版 v1.0 は既に存在します")));
        assertThat(jdbc.sql("SELECT count(*) FROM document_versions WHERE document_id = :d")
                        .param("d", document)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void onlyTheOwningDepartmentCanIngestOrSeeJobs() throws Exception {
        String fileId = upload(regulationPdf("REG-GA-001_"));
        submit(fileId, "v1.0", otherEditor).andExpect(status().isForbidden());

        String jobId = JsonPath.read(
                submit(fileId, "v1.0", editor).andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/" + jobId).with(user(otherEditor)))
                .andExpect(status().isForbidden());
        submit(UUID.randomUUID().toString(), "v2.0", editor).andExpect(status().isNotFound());
    }

    @Test
    void temporaryErrorsAreRetriedUpToThreeTimes() throws Exception {
        String fileId = upload(regulationPdf("REG-GA-001_"));
        String jobId = JsonPath.read(
                submit(fileId, "v1.0", editor).andReturn().getResponse().getContentAsString(), "$.id");
        doThrow(new IllegalStateException("ストレージに一時的につながらない")).when(parser).parseForJob(any());

        worker.runOnce();
        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/" + jobId).with(user(editor)))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("もう一度試します（1回目）")));
        // 再試行は少し間をあける（すぐには取り出さない）
        assertThat(worker.runOnce()).isFalse();

        for (int attempt = 2; attempt <= 3; attempt++) {
            jdbc.sql("UPDATE ingestion_jobs SET next_run_at = now() WHERE id = :id::uuid")
                    .param("id", jobId)
                    .update();
            worker.runOnce();
        }
        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/" + jobId).with(user(editor)))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(3))
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("3回試しました")));
    }

    @Test
    void jobsLeftRunningByARestartAreQueuedAgain() throws Exception {
        String fileId = upload(regulationPdf("REG-GA-001_"));
        String jobId = JsonPath.read(
                submit(fileId, "v1.0", editor).andReturn().getResponse().getContentAsString(), "$.id");
        // 実行中のまま backend が止まった状態を作る
        jdbc.sql("UPDATE ingestion_jobs SET status = 'RUNNING', attempts = 1, started_at = now() - interval '1 hour'"
                        + " WHERE id = :id::uuid")
                .param("id", jobId)
                .update();

        assertThat(worker.runOnce()).isTrue();

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/" + jobId).with(user(editor)))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attempts").value(2));
    }

    private ResultActions submit(String fileId, String versionLabel, KnaviUserDetails as) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/documents/" + document + "/ingestion-jobs")
                .with(user(as))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        Map.of("fileId", fileId, "versionLabel", versionLabel, "effectiveFrom", "2026-04-01"))));
    }

    private String upload(byte[] content) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/admin/files")
                        .file(new MockMultipartFile("file", "規程.pdf", "application/pdf", content))
                        .with(user(editor))
                        .with(csrf()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** ファイル名に日本語を含むため、OS のロケールに依存しないようにコードの接頭辞で探す */
    private static byte[] regulationPdf(String prefix) throws Exception {
        try (var files = Files.list(Path.of("..", "data", "pdf"))) {
            return Files.readAllBytes(
                    files.filter(p -> p.getFileName().toString().startsWith(prefix))
                            .findFirst()
                            .orElseThrow());
        }
    }

    private KnaviUserDetails insertEditor(UUID department, String email) {
        UUID id = jdbc.sql(
                        "INSERT INTO users (email, display_name, department_id, role) VALUES (:e, :e, :d, 'CONTENT_EDITOR') RETURNING id")
                .param("e", email)
                .param("d", department)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, department, email, null, "CONTENT_EDITOR", true);
    }

    private UUID id(String sql) {
        return jdbc.sql(sql).query(UUID.class).single();
    }
}
