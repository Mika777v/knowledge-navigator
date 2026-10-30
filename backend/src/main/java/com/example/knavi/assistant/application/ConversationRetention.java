package com.example.knavi.assistant.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 会話の履歴の保持期間（ADR-0014 の決定 3・ARCHITECTURE.md §6）。保持期間を過ぎた会話を毎日削除する。
 *
 * <p>会話には家族・病気など個人の事情が書かれやすいため、必要な期間だけ残す。
 */
@Component
class ConversationRetention {

    private static final Logger log = LoggerFactory.getLogger(ConversationRetention.class);

    private final ConversationService service;

    ConversationRetention(ConversationService service) {
        this.service = service;
    }

    /** 毎日 3:23（日本時間）に実行する。検索ログの削除（3:17）と少しずらす */
    @Scheduled(cron = "${knavi.assistant.retention-cron:0 23 3 * * *}", zone = "Asia/Tokyo")
    void purgeOnSchedule() {
        int deleted = service.purge();
        if (deleted > 0) {
            log.info("保持期間（{}日）を過ぎた会話を{}件削除しました", service.retentionDays(), deleted);
        }
    }
}
