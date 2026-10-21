package com.example.knavi.content.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.DocumentImport.ImportedDocument;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 管理 API のテスト用データ。
 *
 * <ul>
 *   <li>部署 GA / HR、窓口 GA-GENERAL / HR-LABOR、カテゴリ welfare-ceremony
 *   <li>公開済みの文書 REG-GA-001（慶弔見舞金規程・社内公開）と REG-X-MGR（管理職限定）
 *   <li>GA・HR のコンテンツ担当者、一般社員、システム管理者
 * </ul>
 */
public class AdminApiFixture {

    final MockMvc mockMvc;
    final JdbcClient jdbc;
    final JsonMapper json;

    public final KnaviUserDetails gaEditor;
    public final KnaviUserDetails hrEditor;
    public final KnaviUserDetails employee;
    public final KnaviUserDetails admin;

    public AdminApiFixture(MockMvc mockMvc, JdbcClient jdbc, JsonMapper json, DocumentImport documentImport)
            throws IOException {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.json = json;
        UUID ga = insert("INSERT INTO departments (code, name) VALUES ('GA', '総務部') RETURNING id");
        UUID hr = insert("INSERT INTO departments (code, name) VALUES ('HR', '人事部') RETURNING id");
        jdbc.sql(
                        "INSERT INTO contacts (code, department_id, name, email) VALUES ('GA-GENERAL', :d, '総務部 庶務グループ', 'shomu@example.com')")
                .param("d", ga)
                .update();
        jdbc.sql(
                        "INSERT INTO contacts (code, department_id, name, email) VALUES ('HR-LABOR', :d, '人事部 労務グループ', 'roumu@example.com')")
                .param("d", hr)
                .update();
        jdbc.sql("INSERT INTO categories (slug, name) VALUES ('welfare-ceremony', '慶弔')")
                .update();

        documentImport.importPublished(new ImportedDocument(
                "REG-GA-001",
                "慶弔見舞金規程",
                null,
                "REGULATION",
                "welfare-ceremony",
                ga,
                "INTERNAL",
                "v2.1",
                LocalDate.of(2026, 4, 1),
                Files.readString(seedDocument("REG-GA-001_"))));
        documentImport.importPublished(new ImportedDocument(
                "REG-X-MGR",
                "管理職向け手順",
                null,
                "PROCEDURE",
                "welfare-ceremony",
                ga,
                "MANAGERS",
                "v1.0",
                LocalDate.of(2026, 4, 1),
                "### 第1条（目的）\n\n管理職の対応を定める。\n"));

        gaEditor = insertUser(ga, "ito@example.com", "CONTENT_EDITOR");
        hrEditor = insertUser(hr, "takahashi@example.com", "CONTENT_EDITOR");
        employee = insertUser(ga, "sato@example.com", "EMPLOYEE");
        admin = insertUser(ga, "watanabe@example.com", "SYSTEM_ADMIN");
    }

    public ResultActions perform(MockHttpServletRequestBuilder request, KnaviUserDetails as, Object body)
            throws Exception {
        return mockMvc.perform(request.with(user(as))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    public UUID idOf(String table, String code) {
        return jdbc.sql("SELECT id FROM " + table + " WHERE code = :code")
                .param("code", code)
                .query(UUID.class)
                .single();
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

    private UUID insert(String sql) {
        return jdbc.sql(sql).query(UUID.class).single();
    }

    /** ファイル名に日本語を含むため、OS のロケールに依存しないようにコードの接頭辞で探す */
    static Path seedDocument(String prefix) throws IOException {
        try (var files = Files.list(Path.of("..", "data", "seed", "documents"))) {
            return files.filter(p -> p.getFileName().toString().startsWith(prefix))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
