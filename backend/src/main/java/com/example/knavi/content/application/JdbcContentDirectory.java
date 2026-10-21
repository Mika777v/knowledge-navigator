package com.example.knavi.content.application;

import com.example.knavi.content.ContentDirectory;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcContentDirectory implements ContentDirectory {

    private static final Map<String, String> TABLES =
            Map.of("PROGRAM", "programs", "LIFE_EVENT", "life_events", "DOCUMENT", "documents", "FAQ", "faqs");

    private final JdbcClient jdbc;

    JdbcContentDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(String type, UUID id) {
        String table = TABLES.get(type);
        if (table == null) {
            return false;
        }
        // table は上の定数表からのみ選ぶ（利用者の入力をそのまま SQL にしない）
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }
}
