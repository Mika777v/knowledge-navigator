package com.example.knavi.intelligence.application;

import com.example.knavi.intelligence.AnswerExtractor;
import com.example.knavi.intelligence.application.AiClient.AiServiceException;
import com.example.knavi.intelligence.application.AiClient.ExtractChoice;
import com.example.knavi.intelligence.application.AiClient.ExtractQuestion;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** ai-service で確認質問の答えを読み取る（ADR-0014 の決定 2）。失敗しても例外にせず空を返す */
@Component
class AiAnswerExtractor implements AnswerExtractor {

    private static final Logger log = LoggerFactory.getLogger(AiAnswerExtractor.class);

    private final AiClient ai;

    AiAnswerExtractor(AiClient ai) {
        this.ai = ai;
    }

    @Override
    public Map<String, String> extract(String text, List<Question> questions) {
        if (questions.isEmpty()) {
            return Map.of();
        }
        try {
            return Map.copyOf(ai.extract(
                    text,
                    questions.stream()
                            .map(q -> new ExtractQuestion(
                                    q.code(),
                                    q.text(),
                                    q.choices().stream()
                                            .map(c -> new ExtractChoice(c.value(), c.label()))
                                            .toList()))
                            .toList()));
        } catch (AiServiceException e) {
            // 時間切れ・接続できない・プロバイダの失敗（502）。利用者はクリックで答えられる
            log.warn("確認質問の答えを読み取れませんでした: {}", e.getMessage());
            return Map.of();
        }
    }
}
