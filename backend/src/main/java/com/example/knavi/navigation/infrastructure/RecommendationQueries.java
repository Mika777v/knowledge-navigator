package com.example.knavi.navigation.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 推薦（E12-T5）に使う読み取り。閲覧権限での絞り込みは {@link NavigationQueries#programs} で行う */
@Repository
public class RecommendationQueries {

    private final JdbcClient jdbc;

    RecommendationQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param lifeEvent 同じきっかけの名前 */
    public record LifeEventSibling(UUID programId, String lifeEvent) {}

    /** 同じきっかけに結び付いたほかの制度（きっかけの表示順 → まず確認する制度 → あわせて必要な手続きの順） */
    public List<LifeEventSibling> lifeEventSiblings(UUID programId) {
        return jdbc.sql("""
                        SELECT o.program_id, e.name FROM life_event_programs p
                        JOIN life_events e ON e.id = p.life_event_id
                        JOIN life_event_programs o ON o.life_event_id = p.life_event_id AND o.program_id <> p.program_id
                        WHERE p.program_id = :id
                        ORDER BY e.sort_order, o.relation, o.sort_order
                        """)
                .param("id", programId)
                .query((rs, i) -> new LifeEventSibling(rs.getObject("program_id", UUID.class), rs.getString("name")))
                .list();
    }
}
