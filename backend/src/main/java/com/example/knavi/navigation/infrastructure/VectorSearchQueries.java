package com.example.knavi.navigation.infrastructure;

import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.SqlPredicate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * ベクトル検索（ADR-0007）。embeddings を元の表と結合し、キーワード検索と同じ閲覧条件
 * （{@code AccessPolicy.readPredicate(..., SEARCH)}。CONFIDENTIAL は出さない）を同じ SQL の WHERE に入れる（ADR-0004）。
 * embeddings には権限の情報を持たせていないので、権限の判定は必ずここで元の表に対して行う。
 */
@Component
class VectorSearchQueries {

    /** 長い条は区切って持っているため、条の数より多めに取り出してからまとめる */
    private static final int CHUNKS_PER_SECTION = 3;

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;

    VectorSearchQueries(JdbcClient jdbc, AccessPolicy accessPolicy) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
    }

    /**
     * @param documentId 条のときだけ、その条の文書
     * @param similarity コサイン類似度（1 に近いほど似ている）
     */
    record VectorHit(UUID id, UUID documentId, double similarity) {}

    List<VectorHit> programs(String model, String vector, AccessScope scope, int limit, double minSimilarity) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.SEARCH);
        return nearest(
                "SELECT p.id, NULL::uuid AS document_id, 1 - (e.embedding <=> CAST(:q AS vector)) AS similarity"
                        + " FROM embeddings e JOIN programs p ON p.id = e.source_id"
                        + " WHERE e.source_type = 'PROGRAM' AND e.model = :model AND " + visible.sql(),
                visible,
                model,
                vector,
                limit,
                minSimilarity);
    }

    List<VectorHit> faqs(String model, String vector, AccessScope scope, int limit, double minSimilarity) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "faqs", "f", Purpose.SEARCH);
        return nearest(
                "SELECT f.id, NULL::uuid AS document_id, 1 - (e.embedding <=> CAST(:q AS vector)) AS similarity"
                        + " FROM embeddings e JOIN faqs f ON f.id = e.source_id"
                        + " WHERE e.source_type = 'FAQ' AND e.model = :model AND " + visible.sql(),
                visible,
                model,
                vector,
                limit,
                minSimilarity);
    }

    /** 公開中の版の条だけ（過去の版は出さない。キーワード検索と同じ） */
    List<VectorHit> sections(String model, String vector, AccessScope scope, int limit, double minSimilarity) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.SEARCH);
        return nearest(
                "SELECT s.id, d.id AS document_id, 1 - (e.embedding <=> CAST(:q AS vector)) AS similarity"
                        + " FROM embeddings e JOIN sections s ON s.id = e.source_id"
                        + " JOIN documents d ON d.current_version_id = s.document_version_id"
                        + " WHERE e.source_type = 'SECTION' AND e.model = :model AND " + visible.sql(),
                visible,
                model,
                vector,
                limit * CHUNKS_PER_SECTION,
                minSimilarity);
    }

    private List<VectorHit> nearest(
            String select, SqlPredicate visible, String model, String vector, int fetch, double minSimilarity) {
        // 閲覧条件で候補が減っても件数が足りなくならないよう、HNSW の反復スキャンを使う（このトランザクションの中だけ）
        jdbc.sql("SELECT set_config('hnsw.iterative_scan', 'relaxed_order', true)")
                .query(String.class)
                .single();
        List<VectorHit> rows = jdbc.sql(select + " ORDER BY e.embedding <=> CAST(:q AS vector) LIMIT :fetch")
                .params(visible.params())
                .param("q", vector)
                .param("model", model)
                .param("fetch", fetch)
                .query((rs, n) -> new VectorHit(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getDouble("similarity")))
                .list();
        // 区切った部分ごとの行を、元の1件（いちばん似ている部分）にまとめる
        Map<UUID, VectorHit> best = new LinkedHashMap<>();
        for (VectorHit row : rows) {
            if (row.similarity() >= minSimilarity) {
                best.merge(row.id(), row, (a, b) -> a.similarity() >= b.similarity() ? a : b);
            }
        }
        return best.values().stream()
                .sorted(Comparator.comparingDouble(VectorHit::similarity).reversed())
                .toList();
    }
}
