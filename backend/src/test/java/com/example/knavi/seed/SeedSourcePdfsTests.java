package com.example.knavi.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** make seed で、ダミーの規程10本に原本（data/pdf）と条ごとのページを結び付ける */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers
@Transactional
class SeedSourcePdfsTests {

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
    SeedLoader loader;

    @Autowired
    SeedSourcePdfs sourcePdfs;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        loader.load(Path.of("..", "data", "seed"), "demo-password-for-test");
    }

    @Test
    void attachesTheOriginalAndPagesToEveryArticleOfTheTenRegulations() throws Exception {
        assertThat(sourcePdfs.attach(Path.of("..", "data", "pdf"))).isEqualTo(10);

        assertThat(count("""
                        SELECT count(*) FROM documents d JOIN document_versions v ON v.id = d.current_version_id
                        WHERE d.code LIKE 'REG-%' AND v.source_file_id IS NOT NULL
                        """)).isEqualTo(10);
        // 条・附則のすべてにページが付く（前文は Markdown の版には無い）
        assertThat(count("""
                        SELECT count(*) FROM sections s JOIN documents d ON d.current_version_id = s.document_version_id
                        WHERE d.code LIKE 'REG-%' AND s.page_from IS NULL
                        """)).isZero();
        assertThat(jdbc.sql("""
                        SELECT s.page_from FROM sections s JOIN documents d ON d.current_version_id = s.document_version_id
                        WHERE d.code = 'REG-GA-001' AND s.article_no = '第15条'
                        """).query(Integer.class).single()).isEqualTo(3);

        // 何度実行しても、同じ内容のファイルは増えない
        sourcePdfs.attach(Path.of("..", "data", "pdf"));
        assertThat(count("SELECT count(*) FROM stored_files")).isEqualTo(10);

        // 社員は規程の詳細から原本を開ける
        UUID ga = jdbc.sql("SELECT id FROM documents WHERE code = 'REG-GA-001'")
                .query(UUID.class)
                .single();
        KnaviUserDetails sato = demoUser("sato@example.com");
        mockMvc.perform(get("/api/v1/documents/" + ga).with(user(sato)))
                .andExpect(jsonPath("$.version.hasSourceFile").value(true));
        mockMvc.perform(get("/api/v1/documents/" + ga + "/source").with(user(sato)))
                .andExpect(status().isOk());
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
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
