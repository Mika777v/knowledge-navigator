package com.example.knavi.navigation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.navigation.infrastructure.VectorSearchQueries.VectorHit;
import com.example.knavi.shared.security.AccessScope;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * ベクトル検索の SQL が、キーワード検索と同じ閲覧条件で絞ること（ADR-0004・0007）を、本物の DB で確かめる。
 * embeddings には権限の情報がないので、見えない制度・FAQ・条のベクトルがあっても、結果にも件数にも出てはいけない。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class VectorSearchQueriesTests {

    static final String MODEL = "bge-m3";

    @Autowired
    VectorSearchQueries queries;

    @Autowired
    JdbcClient jdbc;

    UUID dept;
    UUID category;
    AccessScope employee;
    AccessScope manager;

    @BeforeEach
    void setUp() {
        dept = jdbc.sql("INSERT INTO departments (code, name) VALUES (:c, 'テスト部') RETURNING id")
                .param("c", "VEC-" + UUID.randomUUID().toString().substring(0, 8))
                .query(UUID.class)
                .single();
        category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:s, 'テスト') RETURNING id")
                .param("s", "vec-" + UUID.randomUUID())
                .query(UUID.class)
                .single();
        employee = new AccessScope(UUID.randomUUID(), UUID.randomUUID(), "EMPLOYEE");
        manager = new AccessScope(UUID.randomUUID(), UUID.randomUUID(), "MANAGER");
    }

    @Test
    void managersOnlyFaqIsHiddenFromEmployeesButShownToManagers() {
        UUID internal = faq("INTERNAL", "PUBLISHED");
        UUID managersOnly = faq("MANAGERS", "PUBLISHED");
        embed("FAQ", internal, 0, near(0));
        embed("FAQ", managersOnly, 0, near(0));

        assertThat(queries.faqs(MODEL, near(0), employee, 50, 0.5))
                .extracting(VectorHit::id)
                .contains(internal)
                .doesNotContain(managersOnly);
        assertThat(queries.faqs(MODEL, near(0), manager, 50, 0.5))
                .extracting(VectorHit::id)
                .contains(internal, managersOnly);
    }

    @Test
    void confidentialAndDraftsAreNeverReturnedEvenIfTheyHaveVectors() {
        UUID confidential = program("CONFIDENTIAL", "PUBLISHED");
        UUID draft = program("INTERNAL", "DRAFT");
        embed("PROGRAM", confidential, 0, near(0));
        embed("PROGRAM", draft, 0, near(0));
        AccessScope ownerEditor = new AccessScope(UUID.randomUUID(), dept, "CONTENT_EDITOR");

        assertThat(queries.programs(MODEL, near(0), ownerEditor, 50, 0.0))
                .extracting(VectorHit::id)
                .doesNotContain(confidential, draft);
    }

    @Test
    void lessSimilarThanTheMinimumIsLeftOut() {
        UUID close = faq("INTERNAL", "PUBLISHED");
        UUID far = faq("INTERNAL", "PUBLISHED");
        embed("FAQ", close, 0, near(0));
        embed("FAQ", far, 0, near(500));

        var hits = queries.faqs(MODEL, near(0), employee, 50, 0.5);

        assertThat(hits).extracting(VectorHit::id).contains(close).doesNotContain(far);
        assertThat(hits.stream()
                        .filter(h -> h.id().equals(close))
                        .findFirst()
                        .orElseThrow()
                        .similarity())
                .isGreaterThan(0.99);
    }

    @Test
    void onlyTheCurrentModelIsCompared() {
        UUID faq = faq("INTERNAL", "PUBLISHED");
        embed("FAQ", faq, 0, near(0), "text-embedding-3-small");

        assertThat(queries.faqs(MODEL, near(0), employee, 50, 0.0))
                .extracting(VectorHit::id)
                .doesNotContain(faq);
    }

    @Test
    void chunksOfASectionAreMergedIntoOneHitWithTheDocument() {
        UUID document = publishedDocument("INTERNAL");
        UUID section = section(document);
        embed("SECTION", section, 0, near(1));
        embed("SECTION", section, 1, near(0));

        var hits = queries.sections(MODEL, near(0), employee, 50, 0.0).stream()
                .filter(h -> h.id().equals(section))
                .toList();

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().documentId()).isEqualTo(document);
        assertThat(hits.getFirst().similarity()).isGreaterThan(0.99);
    }

    @Test
    void sectionsOfManagersOnlyDocumentsAreHiddenFromEmployees() {
        UUID section = section(publishedDocument("MANAGERS"));
        embed("SECTION", section, 0, near(0));

        assertThat(queries.sections(MODEL, near(0), employee, 50, 0.0))
                .extracting(VectorHit::id)
                .doesNotContain(section);
    }

    /** hot 番目だけが 1 のベクトル（違う hot どうしはコサイン類似度 0） */
    private static String near(int hot) {
        String[] values = new String[1024];
        Arrays.fill(values, "0");
        values[hot] = "1";
        return "[" + String.join(",", values) + "]";
    }

    private void embed(String type, UUID id, int chunk, String vector) {
        embed(type, id, chunk, vector, MODEL);
    }

    private void embed(String type, UUID id, int chunk, String vector, String model) {
        jdbc.sql("""
                        INSERT INTO embeddings (source_type, source_id, chunk_no, model, content_hash, embedding)
                        VALUES (:type, :id, :chunk, :model, repeat('0', 64), CAST(:vector AS vector))
                        """)
                .param("type", type)
                .param("id", id)
                .param("chunk", chunk)
                .param("model", model)
                .param("vector", vector)
                .update();
    }

    private UUID faq(String visibility, String status) {
        return jdbc.sql("""
                        INSERT INTO faqs (code, question, answer_markdown, owner_department_id, visibility, status,
                                          search_text)
                        VALUES (:code, '質問', '回答', :dept, :vis, :status, '質問') RETURNING id
                        """)
                .param("code", "FAQ-VEC-" + UUID.randomUUID().toString().substring(0, 8))
                .param("dept", dept)
                .param("vis", visibility)
                .param("status", status)
                .query(UUID.class)
                .single();
    }

    private UUID program(String visibility, String status) {
        return jdbc.sql("""
                        INSERT INTO programs (code, name, summary, owner_department_id, visibility, status,
                                              search_text)
                        VALUES (:code, '制度', '概要', :dept, :vis, :status, '制度') RETURNING id
                        """)
                .param("code", "PRG-VEC-" + UUID.randomUUID().toString().substring(0, 8))
                .param("dept", dept)
                .param("vis", visibility)
                .param("status", status)
                .query(UUID.class)
                .single();
    }

    private UUID publishedDocument(String visibility) {
        UUID document = jdbc.sql("""
                        INSERT INTO documents (code, title, document_type, category_id, owner_department_id,
                                               visibility, status)
                        VALUES (:code, 'テスト規程', 'REGULATION', :category, :dept, :vis, 'DRAFT')
                        RETURNING id
                        """)
                .param("code", "VEC-" + UUID.randomUUID().toString().substring(0, 8))
                .param("category", category)
                .param("dept", dept)
                .param("vis", visibility)
                .query(UUID.class)
                .single();
        UUID version = jdbc.sql("""
                        INSERT INTO document_versions (document_id, version_label, effective_from, body_markdown)
                        VALUES (:doc, 'v1.0', DATE '2026-04-01', '本文') RETURNING id
                        """).param("doc", document).query(UUID.class).single();
        // 公開中の文書は版を指す必要がある（ck_documents_published_has_version）ため、版を作ってから公開にする
        jdbc.sql("UPDATE documents SET current_version_id = :version, status = 'PUBLISHED' WHERE id = :doc")
                .param("version", version)
                .param("doc", document)
                .update();
        return document;
    }

    /** 文書の今の版に条を1つ作る */
    private UUID section(UUID document) {
        UUID version = jdbc.sql("SELECT current_version_id FROM documents WHERE id = :doc")
                .param("doc", document)
                .query(UUID.class)
                .single();
        return jdbc.sql("""
                        INSERT INTO sections (document_version_id, ordinal, heading, content, search_text)
                        VALUES (:version, 1, '第1条', '本文', '本文') RETURNING id
                        """).param("version", version).query(UUID.class).single();
    }
}
