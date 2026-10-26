package com.example.knavi.intelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void shortTextIsOneChunkWithTheHeader() {
        assertThat(TextChunker.chunks("就業規則 > 第5条（結婚休暇）", "社員が結婚するときは5日の休暇を与える。"))
                .containsExactly("就業規則 > 第5条（結婚休暇）\n社員が結婚するときは5日の休暇を与える。");
    }

    @Test
    void longTextIsSplitWithOverlapAndEveryChunkHasTheHeader() {
        String body = "あ".repeat(TextChunker.SIZE) + "い".repeat(500);

        var chunks = TextChunker.chunks("見出し", body);

        assertThat(chunks).hasSize(2).allMatch(c -> c.startsWith("見出し\n"));
        // 2つ目は、1つ目の終わりの OVERLAP 字を重ねて始まる
        assertThat(chunks.get(1)).startsWith("見出し\n" + "あ".repeat(TextChunker.OVERLAP) + "い");
        assertThat(chunks.get(1)).endsWith("い".repeat(500));
    }

    @Test
    void blankHeaderIsLeftOut() {
        assertThat(TextChunker.chunks(" ", "本文")).containsExactly("本文");
    }
}
