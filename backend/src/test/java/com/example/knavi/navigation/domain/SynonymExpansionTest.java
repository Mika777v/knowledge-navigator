package com.example.knavi.navigation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.navigation.domain.SynonymExpansion.Pair;
import java.util.List;
import org.junit.jupiter.api.Test;

class SynonymExpansionTest {

    @Test
    void dropsMatchesContainedInLongerOnes() {
        var short1 = new Pair("育休", "育児休業");
        var long1 = new Pair("育休明け", "復職");
        var other = new Pair("pc", "パソコン");

        assertThat(SynonymExpansion.longest(List.of(long1, short1, other))).containsExactly(long1, other);
    }

    @Test
    void variantsReplaceTheMatchedWordInTheQuery() {
        var pairs = List.of(new Pair("育休明け", "復職"), new Pair("育休明け", "職場 復帰"));

        assertThat(SynonymExpansion.variants("育休明けに元の部署に戻れる", pairs)).containsExactly("復職に元の部署に戻れる", "職場復帰に元の部署に戻れる");
    }

    @Test
    void extraTermsPutRewrittenTermsFirstAndSkipTermsAlreadyInTheQuery() {
        var pairs = List.of(new Pair("マイカー", "自家用車"), new Pair("マイカー", "テレワーク"));

        assertThat(SynonymExpansion.extraTerms(List.of("マイカー通勤", "テレワーク"), pairs))
                .containsExactly("自家用車通勤", "テレワーク通勤", "自家用車");
    }

    @Test
    void limitsTheNumberOfExtraTerms() {
        var pairs = List.of(
                new Pair("a", "b1"),
                new Pair("a", "b2"),
                new Pair("a", "b3"),
                new Pair("a", "b4"),
                new Pair("a", "b5"),
                new Pair("a", "b6"),
                new Pair("a", "b7"));

        assertThat(SynonymExpansion.extraTerms(List.of("x"), pairs)).hasSize(SynonymExpansion.MAX_EXTRA_TERMS);
    }
}
