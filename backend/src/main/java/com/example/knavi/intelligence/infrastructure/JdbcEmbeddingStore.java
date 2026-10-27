package com.example.knavi.intelligence.infrastructure;

import com.example.knavi.intelligence.application.EmbeddingStore;
import com.example.knavi.intelligence.domain.ChunkKey;
import com.example.knavi.intelligence.domain.EmbeddingSource;
import com.example.knavi.intelligence.domain.EmbeddingSource.SourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 元の文章を content の表から読み、embeddings 表を読み書きする。
 * 権限の情報は embeddings に写さない（ADR-0007）。ここで絞るのは「ベクトルにしてよいか」（公開中か・送ってよい公開範囲か）だけ。
 */
@Component
class JdbcEmbeddingStore implements EmbeddingStore {

    /** ロックの番号（ほかの用途と重ならない固定の値） */
    private static final long LOCK_KEY = 0x6B6E6176_656D6201L;

    private final JdbcClient jdbc;

    JdbcEmbeddingStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryLock() {
        return Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)")
                .param("key", LOCK_KEY)
                .query(Boolean.class)
                .single());
    }

    @Override
    public List<EmbeddingSource> sources(Collection<String> visibilities) {
        if (visibilities.isEmpty()) {
            return List.of();
        }
        List<EmbeddingSource> sources = new ArrayList<>();
        // 公開中の文書の、今の版の条
        sources.addAll(jdbc.sql("""
                        SELECT s.id, d.title, s.heading, s.content
                        FROM sections s JOIN documents d ON d.current_version_id = s.document_version_id
                        WHERE d.status = 'PUBLISHED' AND d.visibility IN (:vis)
                        ORDER BY d.code, s.ordinal
                        """)
                .param("vis", visibilities)
                .query((rs, n) -> new EmbeddingSource(
                        SourceType.SECTION,
                        rs.getObject("id", UUID.class),
                        join(" > ", rs.getString("title"), rs.getString("heading")),
                        rs.getString("content")))
                .list());
        sources.addAll(jdbc.sql("""
                        SELECT id, question, answer_markdown FROM faqs
                        WHERE status = 'PUBLISHED' AND visibility IN (:vis)
                        ORDER BY code
                        """)
                .param("vis", visibilities)
                .query((rs, n) -> new EmbeddingSource(
                        SourceType.FAQ,
                        rs.getObject("id", UUID.class),
                        rs.getString("question"),
                        rs.getString("answer_markdown")))
                .list());
        sources.addAll(jdbc.sql("""
                        SELECT id, name, summary, eligibility, benefit, how_to_apply FROM programs
                        WHERE status = 'PUBLISHED' AND visibility IN (:vis)
                        ORDER BY code
                        """)
                .param("vis", visibilities)
                .query((rs, n) -> new EmbeddingSource(
                        SourceType.PROGRAM,
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        join(
                                "\n",
                                rs.getString("summary"),
                                rs.getString("eligibility"),
                                rs.getString("benefit"),
                                rs.getString("how_to_apply"))))
                .list());
        return sources;
    }

    @Override
    public Map<ChunkKey, String> hashes(String model) {
        Map<ChunkKey, String> hashes = new HashMap<>();
        jdbc.sql("SELECT source_type, source_id, chunk_no, content_hash FROM embeddings WHERE model = :model")
                .param("model", model)
                .query((RowCallbackHandler) rs -> {
                    hashes.put(
                            new ChunkKey(
                                    SourceType.valueOf(rs.getString("source_type")),
                                    rs.getObject("source_id", UUID.class),
                                    rs.getInt("chunk_no")),
                            rs.getString("content_hash"));
                });
        return hashes;
    }

    @Override
    public void upsert(ChunkKey key, String model, String contentHash, float[] embedding) {
        jdbc.sql("""
                        INSERT INTO embeddings (source_type, source_id, chunk_no, model, content_hash, embedding)
                        VALUES (:type, :id, :chunk, :model, :hash, CAST(:embedding AS vector))
                        ON CONFLICT (source_type, source_id, chunk_no, model) DO UPDATE
                        SET content_hash = EXCLUDED.content_hash, embedding = EXCLUDED.embedding, created_at = now()
                        """)
                .param("type", key.type().name())
                .param("id", key.id())
                .param("chunk", key.chunkNo())
                .param("model", model)
                .param("hash", contentHash)
                .param("embedding", vectorLiteral(embedding))
                .update();
    }

    @Override
    public void delete(ChunkKey key, String model) {
        jdbc.sql("""
                        DELETE FROM embeddings
                        WHERE source_type = :type AND source_id = :id AND chunk_no = :chunk AND model = :model
                        """)
                .param("type", key.type().name())
                .param("id", key.id())
                .param("chunk", key.chunkNo())
                .param("model", model)
                .update();
    }

    @Override
    public int deleteOtherModels(String model) {
        return jdbc.sql("DELETE FROM embeddings WHERE model <> :model")
                .param("model", model)
                .update();
    }

    /** pgvector の文字列表現（[0.1,0.2,...]） */
    static String vectorLiteral(float[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        return sb.append(']').toString();
    }

    private static String join(String separator, String... parts) {
        return Stream.of(parts)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(separator));
    }
}
