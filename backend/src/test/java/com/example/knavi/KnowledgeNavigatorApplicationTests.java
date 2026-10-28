package com.example.knavi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KnowledgeNavigatorApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        var body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        // CI はこのファイルから frontend の型（schema.d.ts）を作り直し、コミット済みのものとずれていないか確かめる
        Path out = Path.of("build/openapi/openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, body, StandardCharsets.UTF_8);
    }

    @Test
    void databaseHasRequiredExtensions() {
        var extensions = jdbcClient
                .sql("SELECT extname FROM pg_extension")
                .query(String.class)
                .list();
        assertThat(extensions).contains("pg_bigm", "vector", "pgcrypto");
    }
}
