package com.example.knavi.insight.application;

import com.example.knavi.insight.ProgramCoClicks;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 検索のクリックログ（search_clicks）から、同じユーザー（user_hash）が開いた制度を数える */
@Component
@Transactional(readOnly = true)
class ProgramCoClickQueries implements ProgramCoClicks {

    private final JdbcClient jdbc;

    ProgramCoClickQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> coClickedWith(UUID programId, int limit) {
        return jdbc.sql("""
                        WITH viewers AS (
                          SELECT DISTINCT l.user_hash FROM search_clicks c JOIN search_logs l ON l.id = c.search_log_id
                          WHERE c.target_type = 'PROGRAM' AND c.target_id = :id AND l.user_hash IS NOT NULL
                        )
                        SELECT c.target_id FROM search_clicks c
                        JOIN search_logs l ON l.id = c.search_log_id
                        JOIN viewers v ON v.user_hash = l.user_hash
                        WHERE c.target_type = 'PROGRAM' AND c.target_id <> :id
                        GROUP BY c.target_id
                        HAVING count(DISTINCT l.user_hash) >= :min
                        ORDER BY count(DISTINCT l.user_hash) DESC, c.target_id
                        LIMIT :limit
                        """)
                .param("id", programId)
                .param("min", MIN_USERS)
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }
}
