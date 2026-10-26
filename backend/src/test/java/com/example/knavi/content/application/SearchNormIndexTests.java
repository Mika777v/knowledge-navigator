package com.example.knavi.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.shared.text.JapaneseNormalizer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SearchNormIndexTests {

    @Autowired
    SearchNormIndex index;

    @Autowired
    JdbcClient jdbc;

    @Test
    void rebuildsTheNormalizedColumnsWhenTheDictionaryChanged() {
        UUID dept = jdbc.sql("INSERT INTO departments (code, name) VALUES ('T-NORM', '検証部') RETURNING id")
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        INSERT INTO faqs (code, question, answer_markdown, owner_department_id, visibility, status, search_text)
                        VALUES ('FAQ-T', 'いつ振り込まれますか', '答え', :dept, 'INTERNAL', 'PUBLISHED', 'いつ振り込まれますか 答え')
                        """).param("dept", dept).update();
        jdbc.sql("""
                        INSERT INTO app_settings (key, value) VALUES ('search.norm_dictionary', 'old')
                        ON CONFLICT (key) DO UPDATE SET value = 'old'
                        """).update();

        index.rebuildIfDictionaryChanged();

        assertThat(norm()).contains("振り込む");
        assertThat(jdbc.sql("SELECT value FROM app_settings WHERE key = 'search.norm_dictionary'")
                        .query(String.class)
                        .single())
                .isEqualTo(JapaneseNormalizer.dictionaryVersion());

        // 同じ辞書なら作り直さない
        jdbc.sql("UPDATE faqs SET search_norm = '' WHERE code = 'FAQ-T'").update();
        index.rebuildIfDictionaryChanged();
        assertThat(norm()).isEmpty();
    }

    private String norm() {
        return jdbc.sql("SELECT search_norm FROM faqs WHERE code = 'FAQ-T'")
                .query(String.class)
                .single();
    }
}
