package com.example.knavi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Flyway の初期スキーマ（V1）の制約が設計どおりに働くことを確認する。docs/DOMAIN_MODEL.md */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaMigrationTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    void createsCoreTables() {
        var tables = jdbc.sql("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
                .query(String.class)
                .list();
        assertThat(tables)
                .contains(
                        "departments",
                        "contacts",
                        "users",
                        "documents",
                        "document_versions",
                        "sections",
                        "programs",
                        "program_basis",
                        "program_contacts",
                        "life_events",
                        "faqs",
                        "navigation_keywords",
                        "access_rules",
                        "audit_logs",
                        "search_logs");
    }

    @Test
    void allowsOnlyOnePrimaryContactPerDepartment() {
        UUID dept = insertDepartment("HR");
        insertContact(dept, "HR-REP", true);
        insertContact(dept, "HR-LABOR", false);

        assertThatThrownBy(() -> insertContact(dept, "HR-OTHER", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresContactChannel() {
        UUID dept = insertDepartment("GA");
        assertThatThrownBy(() -> jdbc.sql(
                                "INSERT INTO contacts (code, department_id, name) VALUES ('GA-X', :dept, '連絡手段なし')")
                        .param("dept", dept)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownVisibility() {
        UUID dept = insertDepartment("IT");
        UUID category = insertCategory();
        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO documents (code, title, document_type, category_id, owner_department_id, visibility)
                                VALUES ('REG-X', 'x', 'REGULATION', :category, :dept, 'PUBLIC')
                                """)
                        .param("category", category)
                        .param("dept", dept)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void currentVersionMustBelongToTheSameDocument() {
        UUID dept = insertDepartment("FIN");
        UUID category = insertCategory();
        UUID docA = insertDocument("REG-A", category, dept);
        UUID docB = insertDocument("REG-B", category, dept);
        UUID versionOfB = insertVersion(docB);

        jdbc.sql("UPDATE documents SET current_version_id = :v WHERE id = :d")
                .param("v", versionOfB)
                .param("d", docA)
                .update();

        // FK は DEFERRABLE INITIALLY DEFERRED のため、コミット時（ここでは即時チェックに切り替え）に検出される
        assertThatThrownBy(() -> jdbc.sql("SET CONSTRAINTS ALL IMMEDIATE").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // DB エラー後は同じトランザクションで SQL を実行できないため、更新と削除を別のテストにする
    @Test
    void auditLogsCannotBeUpdated() {
        Long id = insertAuditLog();
        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_logs SET action = 'TAMPERED' WHERE id = :id")
                        .param("id", id)
                        .update())
                .hasMessageContaining("append-only");
    }

    @Test
    void auditLogsCannotBeDeleted() {
        Long id = insertAuditLog();
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_logs WHERE id = :id")
                        .param("id", id)
                        .update())
                .hasMessageContaining("append-only");
    }

    @Test
    void searchesJapaneseSectionsWithBigmIndex() {
        UUID dept = insertDepartment("HR2");
        UUID category = insertCategory();
        UUID version = insertVersion(insertDocument("REG-HR-X", category, dept));
        insertSection(version, 1, "第5条（結婚祝金）社員が婚姻したときは、結婚祝金として30,000円を支給する。");
        insertSection(version, 2, "第6条（出産祝金）社員が出産したときは、出産祝金を支給する。");

        var hits = jdbc.sql("SELECT ordinal FROM sections WHERE search_text LIKE likequery('結婚')")
                .query(Integer.class)
                .list();
        assertThat(hits).containsExactly(1);

        jdbc.sql("SET LOCAL enable_seqscan = off").update();
        var plan = String.join(
                "\n",
                jdbc.sql("EXPLAIN SELECT ordinal FROM sections WHERE search_text LIKE likequery('結婚')")
                        .query(String.class)
                        .list());
        assertThat(plan).contains("idx_sections_search_bigm");
    }

    private Long insertAuditLog() {
        return jdbc.sql("INSERT INTO audit_logs (action) VALUES ('TEST') RETURNING id")
                .query(Long.class)
                .single();
    }

    private UUID insertDepartment(String code) {
        return jdbc.sql("INSERT INTO departments (code, name) VALUES (:code, :code) RETURNING id")
                .param("code", code)
                .query(UUID.class)
                .single();
    }

    private void insertContact(UUID department, String code, boolean primary) {
        jdbc.sql("""
                        INSERT INTO contacts (code, department_id, name, email, is_primary)
                        VALUES (:code, :dept, :code, 'x@example.com', :primary)
                        """)
                .param("code", code)
                .param("dept", department)
                .param("primary", primary)
                .update();
    }

    private UUID insertCategory() {
        return jdbc.sql("INSERT INTO categories (slug, name) VALUES (:slug, 'テスト') RETURNING id")
                .param("slug", "test-" + UUID.randomUUID())
                .query(UUID.class)
                .single();
    }

    private UUID insertDocument(String code, UUID category, UUID department) {
        return jdbc.sql("""
                        INSERT INTO documents (code, title, document_type, category_id, owner_department_id)
                        VALUES (:code, :code, 'REGULATION', :category, :dept) RETURNING id
                        """)
                .param("code", code)
                .param("category", category)
                .param("dept", department)
                .query(UUID.class)
                .single();
    }

    private UUID insertVersion(UUID document) {
        return jdbc.sql("""
                        INSERT INTO document_versions (document_id, version_label, effective_from, body_markdown)
                        VALUES (:doc, 'v1.0', DATE '2026-04-01', '本文') RETURNING id
                        """).param("doc", document).query(UUID.class).single();
    }

    private void insertSection(UUID version, int ordinal, String text) {
        jdbc.sql("""
                        INSERT INTO sections (document_version_id, ordinal, content, search_text)
                        VALUES (:version, :ordinal, :text, :text)
                        """)
                .param("version", version)
                .param("ordinal", ordinal)
                .param("text", text)
                .update();
    }
}
