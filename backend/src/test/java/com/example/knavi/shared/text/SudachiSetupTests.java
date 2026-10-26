package com.example.knavi.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 実際の辞書（Gradle の sudachiDictionary で取り寄せたもの）で、表記ゆれ・活用がそろうことを確かめる */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SudachiSetupTests {

    @Test
    void normalizesInflectionsAndSpellingVariants() {
        assertThat(JapaneseNormalizer.available()).isTrue();
        assertThat(JapaneseNormalizer.dictionaryVersion()).startsWith("system_core-");

        assertThat(JapaneseNormalizer.normalize("慶弔見舞金はいつ振り込まれますか？")).contains("振り込む");
        assertThat(JapaneseNormalizer.normalize("引越し")).isEqualTo(JapaneseNormalizer.normalize("引越"));
        assertThat(JapaneseNormalizer.normalize("子ども")).isEqualTo(JapaneseNormalizer.normalize("子供"));
    }

    @Test
    void picksContentTermsFromQuestions() {
        assertThat(JapaneseNormalizer.contentTerms("お祝い金はいつ振り込まれる？")).contains("祝い金", "振り込む");
        assertThat(JapaneseNormalizer.contentTerms("朝の満員電車を避けたい")).contains("満員 電車", "避ける");
        assertThat(JapaneseNormalizer.contentTerms("在宅勤務中に病院へ行くため抜けたい"))
                .contains("在宅勤務", "病院", "抜ける")
                .doesNotContain("行く");
    }
}
