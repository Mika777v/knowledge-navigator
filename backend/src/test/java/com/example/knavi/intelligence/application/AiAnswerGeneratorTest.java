package com.example.knavi.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.knavi.intelligence.AnswerGenerator.GeneratedAnswer;
import com.example.knavi.intelligence.AnswerGenerator.Passage;
import com.example.knavi.intelligence.application.AiClient.AiServiceException;
import com.example.knavi.intelligence.application.AiClient.RagAnswer;
import com.example.knavi.intelligence.application.AiClient.RagPassage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 生成のプロバイダに送ってよい公開範囲（E14-T7・ADR-0008）と、失敗のときの扱い */
class AiAnswerGeneratorTest {

    final AiClient ai = mock(AiClient.class);
    final Duration timeout = Duration.ofSeconds(1);

    AiAnswerGenerator generator(List<String> externalVisibilities) {
        return new AiAnswerGenerator(
                ai, new AiProperties("http://ai", "t", timeout, timeout, timeout, timeout, externalVisibilities));
    }

    @Test
    void localProvidersMayReceiveEverythingButConfidential() {
        when(ai.chatInfo()).thenReturn(new AiChatInfo("ollama", "qwen3.5:4b", false));

        assertThat(generator(List.of("INTERNAL")).sendableVisibilities())
                .containsExactlyInAnyOrder("INTERNAL", "MANAGERS", "RESTRICTED");
    }

    @Test
    void externalProvidersReceiveOnlyTheConfiguredVisibilitiesAndNeverConfidential() {
        when(ai.chatInfo()).thenReturn(new AiChatInfo("openai", "m", true));

        assertThat(generator(List.of("INTERNAL")).sendableVisibilities()).containsExactly("INTERNAL");
        assertThat(generator(List.of("INTERNAL", "CONFIDENTIAL")).sendableVisibilities())
                .containsExactly("INTERNAL");
        assertThat(generator(List.of()).sendableVisibilities()).isEmpty();
    }

    @Test
    void anUnreachableServiceMayReceiveNothing() {
        when(ai.chatInfo()).thenThrow(new AiServiceException("refused", null));

        assertThat(generator(List.of("INTERNAL")).sendableVisibilities()).isEmpty();
    }

    @Test
    void passesThePassagesAndReturnsTheAnswer() {
        when(ai.answer("結婚祝金", List.of(new RagPassage(1, "規程 第5条", "30,000円"))))
                .thenReturn(new RagAnswer(true, "ANSWERED", "30,000円です。[1]", List.of(1), "qwen3.5:4b"));

        GeneratedAnswer answer = generator(List.of()).generate("結婚祝金", List.of(new Passage(1, "規程 第5条", "30,000円")));

        assertThat(answer).isEqualTo(new GeneratedAnswer(true, "ANSWERED", "30,000円です。[1]", List.of(1), "qwen3.5:4b"));
    }

    @Test
    void failuresBecomeUnanswerable() {
        when(ai.answer(anyString(), anyList())).thenThrow(new AiServiceException("timeout", null));

        assertThat(generator(List.of())
                        .generate("q", List.of(new Passage(1, "t", "x")))
                        .reason())
                .isEqualTo("GENERATION_FAILED");
    }

    @Test
    void noPassagesMeansNoEvidenceWithoutCallingTheService() {
        assertThat(generator(List.of()).generate("q", List.of()).reason()).isEqualTo("NO_EVIDENCE");
        verify(ai, never()).answer(anyString(), anyList());
    }
}
