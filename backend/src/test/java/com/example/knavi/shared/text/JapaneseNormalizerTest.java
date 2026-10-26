package com.example.knavi.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.shared.text.JapaneseNormalizer.Token;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 解析器を差し替えて、語の選び方を確かめる（辞書は使わない） */
class JapaneseNormalizerTest {

    JapaneseNormalizer.Analyzer original;

    @BeforeEach
    void keepOriginal() {
        original = JapaneseNormalizer.installed();
    }

    @AfterEach
    void restore() {
        JapaneseNormalizer.install(original);
    }

    /** 「朝の満員電車を避けたい」を Sudachi が分けた結果と同じ形 */
    static final List<Token> COMMUTE = List.of(
            new Token("朝", "朝", "名詞", "普通名詞"),
            new Token("の", "の", "助詞", "格助詞"),
            new Token("満員", "満員", "名詞", "普通名詞"),
            new Token("電車", "電車", "名詞", "普通名詞"),
            new Token("を", "を", "助詞", "格助詞"),
            new Token("避け", "避ける", "動詞", "一般"),
            new Token("たい", "たい", "助動詞", ""),
            new Token("？", "?", "補助記号", "句点"));

    static JapaneseNormalizer.Analyzer fixed(List<Token> tokens) {
        return new JapaneseNormalizer.Analyzer() {
            @Override
            public List<Token> tokenize(String text) {
                return tokens;
            }

            @Override
            public String dictionaryVersion() {
                return "test";
            }
        };
    }

    @Test
    void normalizeJoinsNormalizedFormsWithoutSymbols() {
        JapaneseNormalizer.install(fixed(COMMUTE));

        assertThat(JapaneseNormalizer.normalize("x")).isEqualTo("朝 の 満員 電車 を 避ける たい");
    }

    @Test
    void contentTermsKeepNounRunsAndPredicatesButNotParticlesOrShortNouns() {
        JapaneseNormalizer.install(fixed(COMMUTE));

        assertThat(JapaneseNormalizer.contentTerms("x")).containsExactly("満員 電車", "満員", "電車", "避ける");
    }

    @Test
    void contentTermsSkipGenericVerbsAndNumbers() {
        JapaneseNormalizer.install(fixed(List.of(
                new Token("10", "10", "名詞", "数詞"),
                new Token("年", "年", "接尾辞", "名詞的"),
                new Token("使っ", "使う", "動詞", "一般"),
                new Token("し", "する", "動詞", "非自立可能"))));

        assertThat(JapaneseNormalizer.contentTerms("x")).isEmpty();
    }

    @Test
    void withoutADictionaryNothingIsAdded() {
        JapaneseNormalizer.install(null);

        assertThat(JapaneseNormalizer.available()).isFalse();
        assertThat(JapaneseNormalizer.dictionaryVersion()).isEmpty();
        assertThat(JapaneseNormalizer.normalize("振り込まれますか")).isEmpty();
        assertThat(JapaneseNormalizer.contentTerms("振り込まれますか")).isEmpty();
    }

    @Test
    void setupWithoutADictionaryLeavesSearchWorkingWithoutNormalization() {
        new SudachiSetup("");
        assertThat(JapaneseNormalizer.available()).isFalse();

        new SudachiSetup(Path.of("no-such-dictionary.dic").toString());
        assertThat(JapaneseNormalizer.available()).isFalse();
    }
}
