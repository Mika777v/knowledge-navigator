package com.example.knavi.insight.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** 検索ログの保存期間（既定180日） */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SearchLogRetentionTests {

    @Autowired
    SearchLogRetention retention;

    @Autowired
    JdbcClient jdbc;

    @Test
    void deletesLogsOlderThanTheRetentionPeriodWithTheirClicksAndFeedback() {
        UUID old = log("181 days");
        UUID recent = log("179 days");
        jdbc.sql("INSERT INTO search_clicks (search_log_id, target_type, target_id, rank) VALUES (:id, 'FAQ', :t, 1)")
                .param("id", old)
                .param("t", UUID.randomUUID())
                .update();
        jdbc.sql("INSERT INTO feedbacks (search_log_id, helpful, comment) VALUES (:id, false, '古いコメント')")
                .param("id", old)
                .update();

        assertThat(retention.purge()).isGreaterThanOrEqualTo(1);

        assertThat(exists("search_logs", "id", old)).isFalse();
        assertThat(exists("search_clicks", "search_log_id", old)).isFalse();
        assertThat(exists("feedbacks", "search_log_id", old)).isFalse();
        assertThat(exists("search_logs", "id", recent)).isTrue();
    }

    private UUID log(String age) {
        return jdbc.sql("""
                        INSERT INTO search_logs (user_hash, query, normalized_query, result_counts, created_at)
                        VALUES (repeat('a', 64), '保存期間', '保存期間', '{}'::jsonb, now() - CAST(:age AS interval))
                        RETURNING id
                        """).param("age", age).query(UUID.class).single();
    }

    private boolean exists(String table, String column, UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE " + column + " = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }
}
