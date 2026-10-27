package com.example.knavi.navigation.application;

import com.example.knavi.navigation.infrastructure.NavigationQueries;
import com.example.knavi.navigation.infrastructure.NavigationQueries.DocumentRow;
import com.example.knavi.navigation.infrastructure.NavigationQueries.SectionRow;
import com.example.knavi.navigation.infrastructure.VectorSearchQueries;
import com.example.knavi.navigation.infrastructure.VectorSearchQueries.VectorHit;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 条に似た、ほかの規程の条（E13-T6・PRD F-404「この条文に似た規定」）。
 *
 * <p>embeddings に入っている条どうしを比べるので、ai-service は呼ばない。元の条・似た条とも、検索と同じ権限（SEARCH）で
 * 見えるものだけを扱い、元の条が見えなければ空を返す（推薦と同じく、404 と区別しないことで見えない条の有無を分からないようにする）。
 * 意味検索がオフ（{@code knavi.search.vector.enabled=false}）のときも空を返す（ADR-0007）。
 */
@Service
@Transactional(readOnly = true)
public class SimilarSectionService {

    static final int LIMIT = 5;

    private final VectorSearchQueries vectors;
    private final NavigationQueries navigation;
    private final VectorSearchProperties properties;

    SimilarSectionService(
            VectorSearchQueries vectors, NavigationQueries navigation, VectorSearchProperties properties) {
        this.vectors = vectors;
        this.navigation = navigation;
        this.properties = properties;
    }

    /** @param articleNo 条番号（例: 第5条。無い条もある） */
    public record SimilarSection(
            UUID id, String articleNo, String heading, UUID documentId, String documentCode, String documentTitle) {}

    public List<SimilarSection> similar(UUID sectionId, AuthenticatedUser user) {
        if (!properties.enabled()) {
            return List.of();
        }
        AccessScope scope = AccessScope.of(user);
        List<VectorHit> hits = vectors.similarSections(sectionId, scope, LIMIT, properties.minSimilarity());
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<UUID, DocumentRow> documents =
                navigation
                        .documents(hits.stream().map(VectorHit::documentId).collect(Collectors.toSet()), scope)
                        .stream()
                        .collect(Collectors.toMap(DocumentRow::id, Function.identity()));
        Map<UUID, SectionRow> sections = navigation
                .sections(hits.stream().map(VectorHit::id).toList())
                .stream()
                .collect(Collectors.toMap(SectionRow::id, Function.identity()));
        return hits.stream()
                .filter(h -> documents.containsKey(h.documentId()) && sections.containsKey(h.id()))
                .map(h -> {
                    SectionRow s = sections.get(h.id());
                    DocumentRow d = documents.get(h.documentId());
                    return new SimilarSection(s.id(), s.articleNo(), s.heading(), d.id(), d.code(), d.title());
                })
                .toList();
    }
}
