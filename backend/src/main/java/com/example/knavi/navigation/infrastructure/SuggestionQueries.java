package com.example.knavi.navigation.infrastructure;

import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.SqlPredicate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 入力補完の候補（E12-T6・PRD F-306）。制度は閲覧権限（SEARCH）で絞り込む。きっかけには公開範囲がない。
 *
 * <p>名前の先頭が一致するものを先に、名前の途中に含むもの・キーワードが前方一致するものを後にする。
 */
@Repository
public class SuggestionQueries {

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;

    SuggestionQueries(JdbcClient jdbc, AccessPolicy accessPolicy) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
    }

    /**
     * @param rank 0: 名前の先頭が一致、1: 名前の途中に含む、2: キーワードの先頭が一致
     */
    public record SuggestionRow(String type, UUID id, String code, String label, int rank) {}

    /** @param normalized 正規化した入力（空白を除く） */
    public List<SuggestionRow> programs(String normalized, AccessScope scope, int limit) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.SEARCH);
        return jdbc.sql("SELECT p.id, p.code, p.name, " + rank("p.name", "PROGRAM", "p.id") + " AS rank FROM programs p"
                        + " WHERE " + visible.sql() + " AND " + rank("p.name", "PROGRAM", "p.id") + " < 3"
                        + " ORDER BY rank, char_length(p.name), p.code LIMIT :limit")
                .param("q", normalized)
                .params(visible.params())
                .param("limit", limit)
                .query((rs, i) -> new SuggestionRow(
                        "PROGRAM",
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("rank")))
                .list();
    }

    public List<SuggestionRow> lifeEvents(String normalized, int limit) {
        return jdbc.sql("SELECT e.id, e.code, e.name, " + rank("e.name", "LIFE_EVENT", "e.id") + " AS rank"
                        + " FROM life_events e WHERE " + rank("e.name", "LIFE_EVENT", "e.id") + " < 3"
                        + " ORDER BY rank, e.sort_order, e.code LIMIT :limit")
                .param("q", normalized)
                .param("limit", limit)
                .query((rs, i) -> new SuggestionRow(
                        "LIFE_EVENT",
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("rank")))
                .list();
    }

    /** 名前・キーワードとの一致の強さ（一致しなければ 3）。名前は空白を除き、小文字にしてから比べる */
    private static String rank(String name, String targetType, String id) {
        String compact = "replace(lower(normalize(" + name + ", NFKC)), ' ', '')";
        return "CASE WHEN starts_with(" + compact + ", :q) THEN 0"
                + " WHEN strpos(" + compact + ", :q) > 0 THEN 1"
                + " WHEN EXISTS (SELECT 1 FROM navigation_keywords k WHERE k.target_type = '" + targetType + "'"
                + " AND k.target_id = " + id + " AND starts_with(replace(k.normalized_term, ' ', ''), :q)) THEN 2"
                + " ELSE 3 END";
    }
}
