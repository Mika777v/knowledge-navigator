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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** アップロードから取得まで、実際の S3 互換ストレージ（SeaweedFS。compose と同じイメージ）を使って確かめる */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers
@Transactional
class AdminFileApiTests {

    static final String ACCESS_KEY = "knavi-test";
    static final String SECRET_KEY = "knavi-test-secret";

    @Container
    static final GenericContainer<?> STORAGE = new GenericContainer<>("chrislusf/seaweedfs:4.48")
            .withCommand("server", "-s3", "-dir=/data", "-ip.bind=0.0.0.0", "-volume.max=0")
            .withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
            .withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
            .withExposedPorts(8333)
            // 認証なしのアクセスに 403 を返せば、S3 API が起動している
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

    KnaviUserDetails editor;
    KnaviUserDetails employee;
    KnaviUserDetails admin;

    @BeforeEach
    void setUp() {
        editor = insertUser("CONTENT_EDITOR");
        employee = insertUser("EMPLOYEE");
        admin = insertUser("SYSTEM_ADMIN");
    }

    @Test
    void uploadsAPdfAndServesItBack() throws Exception {
        byte[] pdf = pdf("慶弔見舞金規程");

        String body = upload("慶弔見舞金規程.pdf", pdf, editor)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalName").value("慶弔見舞金規程.pdf"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(pdf.length))
                .andExpect(jsonPath("$.sha256").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.id");

        byte[] served = mockMvc.perform(
                        get("/api/v1/admin/files/" + id + "/content").with(user(editor)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(served).isEqualTo(pdf);

        // 保存キーに利用者のファイル名は使わない。監査ログにも記録する
        String key = jdbc.sql("SELECT storage_key FROM stored_files WHERE id = :id")
                .param("id", UUID.fromString(id))
                .query(String.class)
                .single();
        assertThat(key).matches("files/[0-9a-f]{2}/" + id + "\\.pdf");
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'FILE_UPLOADED' AND resource_id = :id")
                        .param("id", UUID.fromString(id))
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void sameContentIsStoredOnlyOnce() throws Exception {
        byte[] pdf = pdf("同じ内容");
        String first = JsonPath.read(
                upload("a.pdf", pdf, editor).andReturn().getResponse().getContentAsString(), "$.id");

        upload("b.pdf", pdf, editor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.originalName").value("a.pdf"))
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void acceptsOnlyPdfContent() throws Exception {
        // 拡張子が .pdf でも、中身が PDF でなければ受け付けない
        upload("report.pdf", "<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8), editor)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("PDF ファイルだけをアップロードできます。"));
        upload("empty.pdf", new byte[0], editor)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("ファイルが空です。"));
    }

    @Test
    void keepsOnlyTheFileNameForDisplay() throws Exception {
        upload("../../etc/規程.pdf", pdf("パス付きの名前"), editor)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalName").value("規程.pdf"));
    }

    @Test
    void onlyContentEditorsCanUploadOrRead() throws Exception {
        upload("a.pdf", pdf("権限"), employee).andExpect(status().isForbidden());
        upload("a.pdf", pdf("権限"), admin).andExpect(status().isForbidden());

        String id = JsonPath.read(
                upload("a.pdf", pdf("権限"), editor).andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/v1/admin/files/" + id + "/content").with(user(employee)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/files/" + UUID.randomUUID()).with(user(editor)))
                .andExpect(status().isNotFound());
    }

    private ResultActions upload(String name, byte[] content, KnaviUserDetails as) throws Exception {
        return mockMvc.perform(multipart("/api/v1/admin/files")
                .file(new MockMultipartFile("file", name, "application/pdf", content))
                .with(user(as))
                .with(csrf()));
    }

    /** テスト用の小さな PDF（中身はテストごとに変え、SHA-256 が重ならないようにする） */
    private static byte[] pdf(String text) {
        return ("%PDF-1.4\n% " + text + " " + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
    }

    private KnaviUserDetails insertUser(String role) {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        UUID id = jdbc.sql("INSERT INTO users (email, display_name, role) VALUES (:e, :e, :r) RETURNING id")
                .param("e", email)
                .param("r", role)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, null, email, null, role, true);
    }
}
