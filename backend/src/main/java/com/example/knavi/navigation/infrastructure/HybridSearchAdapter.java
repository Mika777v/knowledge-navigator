package com.example.knavi.navigation.infrastructure;

import com.example.knavi.intelligence.QueryEmbedder;
import com.example.knavi.intelligence.QueryEmbedder.QueryEmbedding;
import com.example.knavi.navigation.application.NavigationWeights;
import com.example.knavi.navigation.application.SearchPort;
import com.example.knavi.navigation.application.VectorSearchProperties;
import com.example.knavi.navigation.domain.RankFusion;
import com.example.knavi.navigation.infrastructure.VectorSearchQueries.VectorHit;
import com.example.knavi.shared.security.AccessScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * キーワード検索（pg_bigm）とベクトル検索を、ブロック（制度・条・FAQ）ごとに RRF で1つの順位にまとめる（ADR-0007）。
 *
 * <p>意味検索がオフ（{@code knavi.search.vector.enabled=false}）のとき、または ai-service に繋がらないときは、
 * キーワード検索の結果をそのまま返す（今までと同じ結果。ADR-0006 の Graceful Degradation）。
 *
 * <p>まとめた点数は、ほかの理由（辞書・きっかけ）の点数と足し合わせるため、キーワード検索の1位の点数
 * （キーワード検索で何も出なかったときは本文の一致の重み）を上限にした尺度にそろえる。
 */
@Primary
@Component
class HybridSearchAdapter implements SearchPort {

    private final PgBigmSearchAdapter keyword;
    private final VectorSearchQueries vectors;
    private final QueryEmbedder embedder;
    private final VectorSearchProperties properties;
    private final NavigationWeights weights;

    HybridSearchAdapter(
            PgBigmSearchAdapter keyword,
            VectorSearchQueries vectors,
            QueryEmbedder embedder,
            VectorSearchProperties properties,
            NavigationWeights weights) {
        this.keyword = keyword;
        this.vectors = vectors;
        this.embedder = embedder;
        this.properties = properties;
        this.weights = weights;
    }

    @Override
    public SearchCandidates search(String question, List<Term> terms, AccessScope scope, int limit) {
        SearchCandidates byKeyword = keyword.search(question, terms, scope, limit);
        if (!properties.enabled()) {
            return byKeyword;
        }
        Optional<QueryEmbedding> embedding = embedder.embed(question);
        if (embedding.isEmpty()) {
            return byKeyword;
        }
        String model = embedding.get().model();
        String vector = embedding.get().literal();
        int n = properties.limit();
        double min = properties.minSimilarity();
        return new SearchCandidates(
                fuse(byKeyword.programs(), vectors.programs(model, vector, scope, n, min), limit),
                fuseSections(byKeyword.sections(), vectors.sections(model, vector, scope, n, min), limit),
                fuse(byKeyword.faqs(), vectors.faqs(model, vector, scope, n, min), limit),
                true);
    }

    private List<Hit> fuse(List<Hit> byKeyword, List<VectorHit> byVector, int limit) {
        Map<UUID, Hit> keywordHits = new HashMap<>();
        byKeyword.forEach(h -> keywordHits.put(h.id(), h));
        Fused fused = fused(
                byKeyword.stream().map(Hit::id).toList(),
                byVector.stream().map(VectorHit::id).toList(),
                byKeyword.isEmpty() ? null : byKeyword.getFirst().score());
        return fused.ranked(limit).stream()
                .map(id -> new Hit(
                        id,
                        fused.score(id),
                        keywordHits.containsKey(id) ? keywordHits.get(id).matchedTerms() : List.of(),
                        fused.semantic().contains(id)))
                .toList();
    }

    private List<SectionHit> fuseSections(List<SectionHit> byKeyword, List<VectorHit> byVector, int limit) {
        Map<UUID, SectionHit> keywordHits = new HashMap<>();
        byKeyword.forEach(h -> keywordHits.put(h.sectionId(), h));
        Map<UUID, UUID> documents = new HashMap<>();
        byKeyword.forEach(h -> documents.put(h.sectionId(), h.documentId()));
        byVector.forEach(h -> documents.putIfAbsent(h.id(), h.documentId()));
        Fused fused = fused(
                byKeyword.stream().map(SectionHit::sectionId).toList(),
                byVector.stream().map(VectorHit::id).toList(),
                byKeyword.isEmpty() ? null : byKeyword.getFirst().score());
        return fused.ranked(limit).stream()
                .map(id -> new SectionHit(
                        id,
                        documents.get(id),
                        fused.score(id),
                        keywordHits.containsKey(id) ? keywordHits.get(id).matchedTerms() : List.of(),
                        fused.semantic().contains(id)))
                .toList();
    }

    private Fused fused(List<UUID> byKeyword, List<UUID> byVector, Double topKeywordScore) {
        Map<UUID, Double> rrf = RankFusion.fuse(List.of(byKeyword, byVector));
        double max =
                rrf.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double scale = topKeywordScore == null ? weights.body() : topKeywordScore;
        Map<UUID, Double> scores = new HashMap<>();
        rrf.forEach((id, s) -> scores.put(id, scale * s / max));
        return new Fused(scores, new ArrayList<>(rrf.keySet()), new HashSet<>(byVector));
    }

    /** まとめた点数。order は最初に出た順（同じ点数のときの並び） */
    private record Fused(Map<UUID, Double> scores, List<UUID> order, Set<UUID> semantic) {

        double score(UUID id) {
            return scores.get(id);
        }

        List<UUID> ranked(int limit) {
            return order.stream()
                    .sorted(Comparator.comparingDouble(this::score).reversed())
                    .limit(limit)
                    .toList();
        }
    }
}
