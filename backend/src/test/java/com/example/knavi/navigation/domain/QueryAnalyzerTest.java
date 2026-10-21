package com.example.knavi.navigation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.navigation.domain.QueryAnalyzer.AnalyzedQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryAnalyzerTest {

    @Test
    void removesSymbolsAndColloquialPhrases() {
        AnalyzedQuery q = QueryAnalyzer.analyze("結婚したら何か制度ある？");
        assertThat(q.normalized()).isEqualTo("結婚したら何か制度ある");
        assertThat(q.cleaned()).isEqualTo("結婚 制度");
    }

    @Test
    void extractsKanjiKatakanaAndAlphanumericRunsAsTerms() {
        assertThat(QueryAnalyzer.analyze("家が台風で浸水した").terms()).containsExactly("台風", "浸水");
        assertThat(QueryAnalyzer.analyze("ＰＣが壊れた").terms()).containsExactly("pc");
        assertThat(QueryAnalyzer.analyze("テレワークの申請方法を教えてください").terms()).containsExactly("テレワーク", "申請");
    }

    @Test
    void dropsGenericAndSingleCharacterWords() {
        assertThat(QueryAnalyzer.analyze("会社の制度").terms()).isEmpty();
        assertThat(QueryAnalyzer.analyze("家が").terms()).isEmpty();
    }

    @Test
    void stripsGenericSuffixesOnlyWhenTheRestIsLongEnough() {
        assertThat(QueryAnalyzer.stripGenericSuffix("育児制度")).isEqualTo("育児");
        assertThat(QueryAnalyzer.stripGenericSuffix("新制度")).isEqualTo("新制度");
    }

    @Test
    void dictionaryTermsComeFirstAndTermsAreCapped() {
        AnalyzedQuery q = QueryAnalyzer.analyze("台風 浸水").withTerms(List.of("浸水", "災害"));
        assertThat(q.terms()).containsExactly("浸水", "災害", "台風");

        String many = String.join(" ", List.of("漢字一", "漢字二", "漢字三", "漢字四", "漢字五", "漢字六", "漢字七", "漢字八", "漢字九"));
        assertThat(QueryAnalyzer.analyze(many).terms()).hasSize(QueryAnalyzer.MAX_TERMS);
    }
}
