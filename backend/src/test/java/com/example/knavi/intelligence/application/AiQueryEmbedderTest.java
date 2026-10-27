package com.example.knavi.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AiQueryEmbedderTest {

    AiClient ai = mock(AiClient.class);
    MutableClock clock = new MutableClock();
    AiQueryEmbedder embedder = new AiQueryEmbedder(ai, clock);

    @Test
    void returnsTheModelAndVectorAndRemembersTheInfoForAWhile() {
        when(ai.info()).thenReturn(new AiServiceInfo("ollama", "bge-m3", 1024, false));
        when(ai.embedQuery(anyString())).thenReturn(new float[1024]);

        var first = embedder.embed("結婚したら");
        embedder.embed("育休はいつまで");

        assertThat(first).hasValueSatisfying(e -> assertThat(e.model()).isEqualTo("bge-m3"));
        verify(ai, times(1)).info();

        clock.advance(AiQueryEmbedder.INFO_TTL.plusSeconds(1));
        embedder.embed("出張の精算");
        verify(ai, times(2)).info();
    }

    @Test
    void aiServiceFailureGivesNothing() {
        when(ai.info()).thenThrow(new AiClient.AiServiceException("refused", null));

        assertThat(embedder.embed("結婚したら")).isEmpty();
    }

    @Test
    void otherDimensionsGiveNothing() {
        when(ai.info()).thenReturn(new AiServiceInfo("openai", "text-embedding-3-large", 3072, true));

        assertThat(embedder.embed("結婚したら")).isEmpty();
    }

    @Test
    void blankQuestionGivesNothing() {
        assertThat(embedder.embed(" ")).isEmpty();
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-27T00:00:00Z");

        void advance(java.time.Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
