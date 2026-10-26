package com.example.knavi.content.application;

import com.example.knavi.shared.text.JapaneseNormalizer;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 表記ゆれを吸収する検索列（search_norm）の作り直し（ADR-0011）。
 *
 * <p>起動時に、検索列を作ったときの辞書の版（app_settings の {@value #SETTING}）と今の辞書の版を比べ、違えば全件を作り直す。
 * 辞書がないときは記録を消す（辞書なしで保存した行は検索列が空のため、次に辞書ありで起動したときに作り直す）。
 * 制度・条・FAQ の件数は数千件までの想定で、起動時に同期的に作り直す。
 */
@Component
class SearchNormIndex {

    static final String SETTING = "search.norm_dictionary";

    private static final Logger log = LoggerFactory.getLogger(SearchNormIndex.class);
    private static final List<String> TABLES = List.of("programs", "sections", "faqs");

    private final JdbcClient jdbc;

    SearchNormIndex(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationStartedEvent.class)
    @Transactional
    public void rebuildIfDictionaryChanged() {
        String current = JapaneseNormalizer.dictionaryVersion();
        String indexed = jdbc.sql("SELECT value FROM app_settings WHERE key = :key")
                .param("key", SETTING)
                .query(String.class)
                .optional()
                .orElse("");
        if (current.isEmpty()) {
            jdbc.sql("DELETE FROM app_settings WHERE key = :key")
                    .param("key", SETTING)
                    .update();
            return;
        }
        if (current.equals(indexed)) {
            return;
        }
        int rows = rebuild();
        jdbc.sql("""
                        INSERT INTO app_settings (key, value) VALUES (:key, :value)
                        ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()
                        """).param("key", SETTING).param("value", current).update();
        log.info("表記ゆれを吸収する検索列を作り直しました: {} 行（辞書 {}）", rows, current);
    }

    /** すべての制度・条・FAQ の検索列を作り直す */
    int rebuild() {
        int count = 0;
        for (String table : TABLES) {
            record Row(UUID id, String searchText) {}
            List<Row> rows = jdbc.sql("SELECT id, search_text FROM " + table)
                    .query((rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("search_text")))
                    .list();
            for (Row r : rows) {
                count += jdbc.sql("UPDATE " + table + " SET search_norm = :norm WHERE id = :id")
                        .param("norm", JapaneseNormalizer.normalize(r.searchText()))
                        .param("id", r.id())
                        .update();
            }
        }
        return count;
    }
}
