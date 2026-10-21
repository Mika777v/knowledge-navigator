package com.example.knavi.insight.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 検索ログの保存期間（docs/ARCHITECTURE.md §6）。保存期間を過ぎた検索ログを毎日削除する。
 * クリックとフィードバックは検索ログと一緒に消える（外部キーの ON DELETE CASCADE）。
 *
 * <p>検索ログは仮名化しているが、質問やコメントに個人の事情が書かれることがあるため、必要な期間だけ残す。
 */
@Component
public class SearchLogRetention {

    private static final Logger log = LoggerFactory.getLogger(SearchLogRetention.class);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration retention;

    SearchLogRetention(JdbcClient jdbc, Clock clock, @Value("${knavi.insight.retention-days:180}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalStateException("knavi.insight.retention-days は1以上にしてください。");
        }
        this.jdbc = jdbc;
        this.clock = clock;
        this.retention = Duration.ofDays(retentionDays);
    }

    /** 毎日 3:17（日本時間）に実行する。利用の少ない時間帯で、ちょうどの時刻を避ける */
    @Scheduled(cron = "${knavi.insight.retention-cron:0 17 3 * * *}", zone = "Asia/Tokyo")
    public void purgeOnSchedule() {
        int deleted = purge();
        if (deleted > 0) {
            log.info("保存期間（{}日）を過ぎた検索ログを{}件削除しました", retention.toDays(), deleted);
        }
    }

    /** 保存期間を過ぎた検索ログを削除し、削除した件数を返す */
    @Transactional
    public int purge() {
        Instant cutoff = clock.instant().minus(retention);
        return jdbc.sql("DELETE FROM search_logs WHERE created_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }
}
