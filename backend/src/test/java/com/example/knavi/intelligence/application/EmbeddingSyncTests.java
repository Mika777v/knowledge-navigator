package com.example.knavi.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.knavi.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * ベクトルの差分合わせ（ADR-0012）を、本物の DB と差し替えた ai-service で確かめる。
 * 「あるべきもの」と embeddings 表が一致すること、変わったものだけ作ること、要らない行を消すことを見る。
 */
@SpringBootTest(properties = "knavi.search.vector.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class EmbeddingSyncTests {

    static final AiServiceInfo LOCAL = new AiServiceInfo("ollama", "bge-m3", 1024, false);
    static final AiServiceInfo EXTERNAL = new AiServiceInfo("openai", "text-embedding-3-small", 1024, true);

    @Autowired
    EmbeddingSyncService service;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    AiClient ai;

    UUID dept;
    UUID category;

    @BeforeEach
    void setUp() {
        when(ai.info()).thenReturn(LOCAL);
        when(ai.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<float[]> vectors = new ArrayList<>();
            for (String text : texts) {
                float[] v = new float[1024];
                v[Math.floorMod(text.hashCode(), 1024)] = 1f;
                vectors.add(v);
            }
            return vectors;
        });
        dept = jdbc.sql("INSERT INTO departments (code, name) VALUES (:c, 'テスト部') RETURNING id")
                .param("c", "EMB-" + UUID.randomUUID().toString().substring(0, 8))
                .query(UUID.class)
                .single();
        category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:s, 'テスト') RETURNING id")
                .param("s", "emb-" + UUID.randomUUID())
                .query(UUID.class)
                .single();
    }

    @Test
    void embedsPublishedContentAndSkipsDraftsAndConfidential() {
        UUID section = publishedSection("INTERNAL", "第5条（結婚休暇）", "結婚するときは5日休める。");
        UUID managers = publishedSection("MANAGERS", "第9条（部下の育児）", "管理職は部下の申出に応じる。");
        UUID confidential = publishedSection("CONFIDENTIAL", "第1条（役員報酬）", "役員の報酬を定める。");
        UUID faq = faq("INTERNAL", "PUBLISHED", "育休はいつまで？", "子が1歳になるまで。");
        UUID draftFaq = faq("INTERNAL", "DRAFT", "下書きの質問", "まだ公開しない。");
        UUID program = program("INTERNAL", "PUBLISHED", "結婚祝金", "結婚したら3万円を支給する。");

        service.syncOnce();

        assertThat(models(section)).containsExactly("bge-m3");
        // 社外に送らないプロバイダには、管理職限定（MANAGERS）も送ってよい
        assertThat(models(managers)).containsExactly("bge-m3");
        assertThat(models(faq)).containsExactly("bge-m3");
        assertThat(models(program)).containsExactly("bge-m3");
        assertThat(models(confidential)).isEmpty();
        assertThat(models(draftFaq)).isEmpty();
    }

    @Test
    void unchangedContentIsNotEmbeddedAgain() {
        publishedSection("INTERNAL", "第5条（結婚休暇）", "結婚するときは5日休める。");
        service.syncOnce();
        clearInvocations(ai);

        var result = service.syncOnce();

        assertThat(result.embedded()).isZero();
        verify(ai, never()).embed(anyList());
    }

    @Test
    void changedContentIsEmbeddedAgainAndUnpublishedIsDeleted() {
        UUID faq = faq("INTERNAL", "PUBLISHED", "育休はいつまで？", "子が1歳になるまで。");
        UUID program = program("INTERNAL", "PUBLISHED", "結婚祝金", "結婚したら3万円を支給する。");
        service.syncOnce();
        String before = hash(faq);

        jdbc.sql("UPDATE faqs SET answer_markdown = '子が1歳6か月になるまで延長できる。' WHERE id = :id")
                .param("id", faq)
                .update();
        jdbc.sql("UPDATE programs SET status = 'ARCHIVED' WHERE id = :id")
                .param("id", program)
                .update();
        service.syncOnce();

        assertThat(hash(faq)).isNotEqualTo(before);
        assertThat(models(program)).isEmpty();
    }

    @Test
    void becomingConfidentialRemovesTheVector() {
        UUID faq = faq("INTERNAL", "PUBLISHED", "育休はいつまで？", "子が1歳になるまで。");
        service.syncOnce();

        jdbc.sql("UPDATE faqs SET visibility = 'CONFIDENTIAL' WHERE id = :id")
                .param("id", faq)
                .update();
        service.syncOnce();

        assertThat(models(faq)).isEmpty();
    }

    @Test
    void externalProviderGetsOnlyAllowedVisibilitiesAndReplacesTheOldModel() {
        UUID internal = faq("INTERNAL", "PUBLISHED", "育休はいつまで？", "子が1歳になるまで。");
        UUID managers = faq("MANAGERS", "PUBLISHED", "部下が妊娠を報告したら？", "まず本人の希望を聞く。");
        service.syncOnce();

        when(ai.info()).thenReturn(EXTERNAL);
        service.syncOnce();

        // 社外に送るプロバイダには、既定で INTERNAL だけを送る（ADR-0008）。古いモデルの行は消える（ADR-0012）
        assertThat(models(internal)).containsExactly("text-embedding-3-small");
        assertThat(models(managers)).isEmpty();
    }

    @Test
    void longSectionsAreSplitIntoChunks() {
        UUID section = publishedSection("INTERNAL", "第20条（長い条）", "あ".repeat(1500));

        service.syncOnce();

        assertThat(jdbc.sql("SELECT count(*) FROM embeddings WHERE source_id = :id")
                        .param("id", section)
                        .query(Integer.class)
                        .single())
                .isGreaterThan(1);
    }

    @Test
    void aiServiceFailureIsReportedAndRetriedNextTime() {
        faq("INTERNAL", "PUBLISHED", "育休はいつまで？", "子が1歳になるまで。");
        when(ai.embed(anyList())).thenThrow(new AiClient.AiServiceException("refused", null));

        assertThatThrownBy(() -> service.syncOnce()).isInstanceOf(AiClient.AiServiceException.class);
    }

    private List<String> models(UUID sourceId) {
        return jdbc.sql("SELECT DISTINCT model FROM embeddings WHERE source_id = :id")
                .param("id", sourceId)
                .query(String.class)
                .list();
    }

    private String hash(UUID sourceId) {
        return jdbc.sql("SELECT content_hash FROM embeddings WHERE source_id = :id AND chunk_no = 0")
                .param("id", sourceId)
                .query(String.class)
                .single();
    }

    private UUID publishedSection(String visibility, String heading, String content) {
        UUID document = jdbc.sql("""
                        INSERT INTO documents (code, title, document_type, category_id, owner_department_id,
                                               visibility, status)
                        VALUES (:code, 'テスト規程', 'REGULATION', :category, :dept, :vis, 'DRAFT')
                        RETURNING id
                        """)
                .param("code", "EMB-" + UUID.randomUUID().toString().substring(0, 8))
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
        return jdbc.sql("""
                        INSERT INTO sections (document_version_id, ordinal, heading, content, search_text)
                        VALUES (:version, 1, :heading, :content, :content) RETURNING id
                        """)
                .param("version", version)
                .param("heading", heading)
                .param("content", content)
                .query(UUID.class)
                .single();
    }

    private UUID faq(String visibility, String status, String question, String answer) {
        return jdbc.sql("""
                        INSERT INTO faqs (code, question, answer_markdown, owner_department_id, visibility, status,
                                          search_text)
                        VALUES (:code, :q, :a, :dept, :vis, :status, :q) RETURNING id
                        """)
                .param("code", "FAQ-EMB-" + UUID.randomUUID().toString().substring(0, 8))
                .param("q", question)
                .param("a", answer)
                .param("dept", dept)
                .param("vis", visibility)
                .param("status", status)
                .query(UUID.class)
                .single();
    }

    private UUID program(String visibility, String status, String name, String summary) {
        return jdbc.sql("""
                        INSERT INTO programs (code, name, summary, owner_department_id, visibility, status,
                                              search_text)
                        VALUES (:code, :name, :summary, :dept, :vis, :status, :name) RETURNING id
                        """)
                .param("code", "PRG-EMB-" + UUID.randomUUID().toString().substring(0, 8))
                .param("name", name)
                .param("summary", summary)
                .param("dept", dept)
                .param("vis", visibility)
                .param("status", status)
                .query(UUID.class)
                .single();
    }
}
