package com.example.knavi.intelligence.application;

import com.example.knavi.intelligence.QueryEmbedder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 質問を ai-service でベクトルにする。ai-service の設定（モデル・次元）は検索のたびに聞かず、しばらく覚えておく。
 */
@Component
class AiQueryEmbedder implements QueryEmbedder {

    private static final Logger log = LoggerFactory.getLogger(AiQueryEmbedder.class);

    /** ai-service の設定を覚えておく時間（モデルを替えたら、この時間のうちに追いつく） */
    static final Duration INFO_TTL = Duration.ofMinutes(1);

    private final AiClient ai;
    private final Clock clock;
    private volatile CachedInfo cached;

    AiQueryEmbedder(AiClient ai) {
        this(ai, Clock.systemUTC());
    }

    AiQueryEmbedder(AiClient ai, Clock clock) {
        this.ai = ai;
        this.clock = clock;
    }

    @Override
    public Optional<QueryEmbedding> embed(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        try {
            AiServiceInfo info = info();
            if (info.dims() != EmbeddingSyncService.DIMS) {
                return Optional.empty();
            }
            float[] vector = ai.embedQuery(question);
            if (vector.length != EmbeddingSyncService.DIMS) {
                return Optional.empty();
            }
            return Optional.of(new QueryEmbedding(info.model(), vector));
        } catch (AiClient.AiServiceException e) {
            // 質問の中身はログに出さない
            log.warn("ai-service に繋がらないため、キーワード検索だけで返します: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private AiServiceInfo info() {
        CachedInfo current = cached;
        Instant now = clock.instant();
        if (current == null || now.isAfter(current.expiresAt())) {
            current = new CachedInfo(ai.info(), now.plus(INFO_TTL));
            cached = current;
        }
        return current.info();
    }

    private record CachedInfo(AiServiceInfo info, Instant expiresAt) {}
}
