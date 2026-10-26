package com.example.knavi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Flyway のスキーマの制約・索引が設計どおりに働くことを確認する。docs/DOMAIN_MODEL.md・ADR-0007 */
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
                        "search_logs",
                        "embeddings");
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

    @Test
    void embeddingsRejectUnknownSourceType() {
        assertThatThrownBy(() -> insertEmbedding("DOCUMENT", UUID.randomUUID(), "bge-m3", vector(1024, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void embeddingsRejectWrongDimensions() {
        // 1つのトランザクションでは最初のエラーで以降の SQL が通らないため、テストを分ける
        assertThatThrownBy(() -> insertEmbedding("SECTION", UUID.randomUUID(), "bge-m3", vector(768, 0)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("1024");
    }

    @Test
    void embeddingsAreUniquePerSourceChunkAndModel() {
        UUID source = UUID.randomUUID();
        insertEmbedding("SECTION", source, "bge-m3", vector(1024, 0));
        // 別のモデルなら同じ条でも持てる（モデルの切り替え中に並べて作るため）
        insertEmbedding("SECTION", source, "text-embedding-3-small", vector(1024, 0));
        assertThatThrownBy(() -> insertEmbedding("SECTION", source, "bge-m3", vector(1024, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsNearestEmbeddingsWithHnswIndex() {
        UUID near = UUID.randomUUID();
        insertEmbedding("SECTION", near, "bge-m3", vector(1024, 0));
        insertEmbedding("SECTION", UUID.randomUUID(), "bge-m3", vector(1024, 500));

        String nearest = "SELECT source_id FROM embeddings WHERE model = 'bge-m3'"
                + " ORDER BY embedding <=> CAST(:q AS vector) LIMIT 1";
        assertThat(jdbc.sql(nearest)
                        .param("q", vector(1024, 0))
                        .query(UUID.class)
                        .single())
                .isEqualTo(near);

        // 権限の WHERE で候補が減っても件数が足りなくならないよう、反復スキャンを使う（ADR-0007）
        jdbc.sql("SET LOCAL hnsw.iterative_scan = relaxed_order").update();
        jdbc.sql("SET LOCAL enable_seqscan = off").update();
        var plan = String.join(
                "\n",
                jdbc.sql("EXPLAIN " + nearest)
                        .param("q", vector(1024, 0))
                        .query(String.class)
                        .list());
        assertThat(plan).contains("idx_embeddings_hnsw_bge_m3");
    }

    private void insertEmbedding(String sourceType, UUID sourceId, String model, String embedding) {
        jdbc.sql("""
                        INSERT INTO embeddings (source_type, source_id, model, content_hash, embedding)
                        VALUES (:type, :id, :model, repeat('0', 64), CAST(:embedding AS vector))
                        """)
                .param("type", sourceType)
                .param("id", sourceId)
                .param("model", model)
                .param("embedding", embedding)
                .update();
    }

    /** hot 番目だけが 1、ほかは 0 のベクトル（pgvector の文字列表現） */
    private static String vector(int dims, int hot) {
        var values = new String[dims];
        Arrays.fill(values, "0");
        values[hot] = "1";
        return "[" + String.join(",", values) + "]";
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
