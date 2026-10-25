package com.example.knavi.ingestion.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 取り込みジョブを定期的に処理する。待ちのジョブがある間は続けて処理する。
 * テストでは {@code knavi.ingestion.worker.enabled=false} にして止め、{@link IngestionWorker#runOnce()} を直接呼ぶ。
 */
@Component
@ConditionalOnProperty(name = "knavi.ingestion.worker.enabled", havingValue = "true", matchIfMissing = true)
class IngestionScheduler {

    /** 1回の起動で処理する最大件数（ほかの定期処理を待たせすぎないため） */
    private static final int BATCH = 10;

    private final IngestionWorker worker;

    IngestionScheduler(IngestionWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${knavi.ingestion.worker.interval:PT2S}")
    void poll() {
        for (int i = 0; i < BATCH && worker.runOnce(); i++) {
            // 待ちのジョブが無くなるか、上限まで処理する
        }
    }
}
