package com.example.knavi.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void normalizesWidthCaseAndWhitespace() {
        assertThat(TextNormalizer.normalize("  ＰＣが　壊れた\n\tＶＰＮ  ")).isEqualTo("pcが 壊れた vpn");
    }

    @Test
    void convertsHalfWidthKanaAndFullWidthDigits() {
        assertThat(TextNormalizer.normalize("ｶﾞｲﾄﾞ 第５条 ３０，０００円")).isEqualTo("ガイド 第5条 30,000円");
    }

    @Test
    void returnsEmptyForNull() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }
}
