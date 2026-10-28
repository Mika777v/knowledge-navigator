package com.example.knavi.intelligence.application;

import com.example.knavi.intelligence.AnswerGenerator;
import com.example.knavi.intelligence.application.AiClient.AiServiceException;
import com.example.knavi.intelligence.application.AiClient.RagAnswer;
import com.example.knavi.intelligence.application.AiClient.RagPassage;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** ai-service で回答文を作る（ADR-0013）。失敗しても例外にせず「回答できない」を返す */
@Component
class AiAnswerGenerator implements AnswerGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiAnswerGenerator.class);

    private final AiClient ai;
    private final AiProperties properties;

    AiAnswerGenerator(AiClient ai, AiProperties properties) {
        this.ai = ai;
        this.properties = properties;
    }

    @Override
    public Set<String> sendableVisibilities() {
        try {
            return SendableVisibilities.of(ai.chatInfo().external(), properties.externalVisibilities());
        } catch (AiServiceException e) {
            log.warn("回答文の生成のプロバイダを確かめられませんでした: {}", e.getMessage());
            return Set.of();
        }
    }

    @Override
    public GeneratedAnswer generate(String question, List<Passage> passages) {
        if (passages.isEmpty()) {
            return GeneratedAnswer.unanswerable("NO_EVIDENCE");
        }
        try {
            RagAnswer answer = ai.answer(
                    question,
                    passages.stream()
                            .map(p -> new RagPassage(p.ref(), p.title(), p.text()))
                            .toList());
            List<Integer> citations = answer.citations() == null ? List.of() : answer.citations();
            return new GeneratedAnswer(answer.answerable(), answer.reason(), answer.answer(), citations, answer.model());
        } catch (AiServiceException e) {
            // 時間切れ・接続できない・プロバイダの失敗（502）。4ブロックの結果は呼ぶ側で出ている
            log.warn("回答文を作れませんでした: {}", e.getMessage());
            return GeneratedAnswer.unanswerable("GENERATION_FAILED");
        }
    }
}
