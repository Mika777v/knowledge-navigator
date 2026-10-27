package com.example.knavi.intelligence.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 一定の間隔でベクトルを合わせる（ADR-0012）。意味検索がオフ（{@code knavi.search.vector.enabled=false}）のときは動かさない。
 * ai-service に繋がらないときは、その回を諦めて次の回にやり直す。
 */
@Component
@ConditionalOnProperty(name = "knavi.search.vector.enabled", havingValue = "true")
class EmbeddingSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSyncScheduler.class);

    private final EmbeddingSyncService service;

    EmbeddingSyncScheduler(EmbeddingSyncService service) {
        this.service = service;
    }

    @Scheduled(
            initialDelayString = "${knavi.embedding.initial-delay:PT30S}",
            fixedDelayString = "${knavi.embedding.sync-interval:PT5M}")
    void sync() {
        try {
            service.syncOnce();
        } catch (AiClient.AiServiceException e) {
            log.warn("ai-service に繋がらないため、ベクトルの合わせを次の回に回します: {}", e.getMessage());
        }
    }
}
