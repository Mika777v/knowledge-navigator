package com.example.knavi.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.knavi.intelligence.AnswerExtractor.Choice;
import com.example.knavi.intelligence.AnswerExtractor.Question;
import com.example.knavi.intelligence.application.AiClient.AiServiceException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 確認質問の答えの読み取り（ADR-0014 の決定 2）。ai-service が失敗しても例外にせず、空を返す */
class AiAnswerExtractorTest {

    final AiClient ai = mock(AiClient.class);
    final AiAnswerExtractor extractor = new AiAnswerExtractor(ai);
    final List<Question> questions = List.of(
            new Question("TENURE_6M", "入社から6か月以上たっていますか。", List.of(new Choice("YES", "はい"), new Choice("NO", "いいえ"))));

    @Test
    void returnsWhatTheServiceRead() {
        when(ai.extract(any(), anyList())).thenReturn(Map.of("TENURE_6M", "YES"));

        assertThat(extractor.extract("入社して2年です", questions)).containsExactly(Map.entry("TENURE_6M", "YES"));
    }

    @Test
    void failureIsAnEmptyResult() {
        when(ai.extract(any(), anyList())).thenThrow(new AiServiceException("timeout", null));

        assertThat(extractor.extract("入社して2年です", questions)).isEmpty();
    }

    @Test
    void doesNotCallTheServiceWithoutQuestions() {
        assertThat(extractor.extract("入社して2年です", List.of())).isEmpty();
        verifyNoInteractions(ai);
    }
}
