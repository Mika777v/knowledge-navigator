package com.example.knavi.navigation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.knavi.intelligence.QueryEmbedder;
import com.example.knavi.intelligence.QueryEmbedder.QueryEmbedding;
import com.example.knavi.navigation.application.NavigationWeights;
import com.example.knavi.navigation.application.SearchPort.Hit;
import com.example.knavi.navigation.application.SearchPort.SearchCandidates;
import com.example.knavi.navigation.application.SearchPort.SectionHit;
import com.example.knavi.navigation.application.SearchPort.Term;
import com.example.knavi.navigation.application.VectorSearchProperties;
import com.example.knavi.navigation.infrastructure.VectorSearchQueries.VectorHit;
import com.example.knavi.shared.security.AccessScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** キーワード検索とベクトル検索のまとめ方（RRF）と、意味検索を使わないときの動きを確かめる */
class HybridSearchAdapterTest {

    static final NavigationWeights WEIGHTS = new NavigationWeights(3.0, 2.5, 2.0, 1.5, 1.0, 0.5, 0.3, 0.2, 0.7, 0.8);
    static final AccessScope SCOPE = new AccessScope(UUID.randomUUID(), UUID.randomUUID(), "EMPLOYEE");
    static final List<Term> TERMS = List.of(Term.of("結婚"));

    PgBigmSearchAdapter keyword = mock(PgBigmSearchAdapter.class);
    VectorSearchQueries vectors = mock(VectorSearchQueries.class);
    QueryEmbedder embedder = mock(QueryEmbedder.class);

    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    UUID doc = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(embedder.embed(anyString())).thenReturn(Optional.of(new QueryEmbedding("bge-m3", new float[] {1f, 0f})));
        when(vectors.programs(anyString(), anyString(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        when(vectors.sections(anyString(), anyString(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        when(vectors.faqs(anyString(), anyString(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of());
    }

    HybridSearchAdapter adapter(boolean enabled) {
        return new HybridSearchAdapter(
                keyword, vectors, embedder, new VectorSearchProperties(enabled, 50, 0.5), WEIGHTS);
    }

    @Test
    void disabledReturnsKeywordResultsAsTheyAre() {
        var byKeyword = new SearchCandidates(List.of(new Hit(a, 2.5, List.of("結婚"))), List.of(), List.of());
        when(keyword.search(anyString(), any(), any(), anyInt())).thenReturn(byKeyword);

        assertThat(adapter(false).search("結婚したら", TERMS, SCOPE, 30)).isSameAs(byKeyword);
        verifyNoInteractions(embedder, vectors);
    }

    @Test
    void withoutAiServiceReturnsKeywordResultsAsTheyAre() {
        var byKeyword = new SearchCandidates(List.of(new Hit(a, 2.5, List.of("結婚"))), List.of(), List.of());
        when(keyword.search(anyString(), any(), any(), anyInt())).thenReturn(byKeyword);
        when(embedder.embed(anyString())).thenReturn(Optional.empty());

        assertThat(adapter(true).search("結婚したら", TERMS, SCOPE, 30)).isSameAs(byKeyword);
        verifyNoInteractions(vectors);
    }

    @Test
    void itemsFoundByBothComeFirstAndScoresStayOnTheKeywordScale() {
        when(keyword.search(anyString(), any(), any(), anyInt()))
                .thenReturn(new SearchCandidates(
                        List.of(new Hit(a, 2.5, List.of("結婚")), new Hit(b, 1.0, List.of("結婚"))), List.of(), List.of()));
        when(vectors.programs(eq("bge-m3"), anyString(), eq(SCOPE), anyInt(), anyDouble()))
                .thenReturn(List.of(new VectorHit(b, null, 0.8), new VectorHit(c, null, 0.7)));

        var programs = adapter(true).search("籍を入れる", TERMS, SCOPE, 30).programs();

        // b はキーワード2位・ベクトル1位で合計が最も高い。上限はキーワード検索の1位の点数（2.5）
        assertThat(programs).extracting(Hit::id).containsExactly(b, a, c);
        assertThat(programs.getFirst().score()).isEqualTo(2.5);
        assertThat(programs.getFirst().semantic()).isTrue();
        assertThat(programs.getFirst().matchedTerms()).containsExactly("結婚");
        assertThat(programs.get(1).semantic()).isFalse();
        // ベクトル検索だけで見つかったものは、一致した語を持たない
        assertThat(programs.get(2).matchedTerms()).isEmpty();
    }

    @Test
    void vectorOnlyResultsUseTheBodyWeightAsTheScale() {
        when(keyword.search(anyString(), any(), any(), anyInt())).thenReturn(SearchCandidates.empty());
        when(vectors.sections(anyString(), anyString(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new VectorHit(a, doc, 0.9), new VectorHit(b, doc, 0.6)));

        var sections = adapter(true).search("籍を入れる", List.of(), SCOPE, 30).sections();

        assertThat(sections).extracting(SectionHit::sectionId).containsExactly(a, b);
        assertThat(sections).extracting(SectionHit::documentId).containsOnly(doc);
        assertThat(sections.getFirst().score()).isEqualTo(WEIGHTS.body());
        assertThat(sections).allMatch(SectionHit::semantic);
    }

    @Test
    void limitIsApplied() {
        when(keyword.search(anyString(), any(), any(), anyInt())).thenReturn(SearchCandidates.empty());
        when(vectors.faqs(anyString(), anyString(), any(), anyInt(), anyDouble()))
                .thenReturn(
                        List.of(new VectorHit(a, null, 0.9), new VectorHit(b, null, 0.8), new VectorHit(c, null, 0.7)));

        assertThat(adapter(true).search("質問", List.of(), SCOPE, 2).faqs()).hasSize(2);
    }
}
