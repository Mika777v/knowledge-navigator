package com.example.knavi.navigation.infrastructure;

import com.example.knavi.navigation.application.NavigationWeights;
import com.example.knavi.navigation.application.SearchPort;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.SqlPredicate;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * pg_bigm による全文検索（MVP）。search_text 列の 2-gram GIN インデックスで {@code LIKE '%語%'} を引く。
 *
 * <p>語ごとに「名前・見出しに含まれる」「本文に含まれる」で重みを付け、語の重み（類義語で広げた語は小さい）を掛けて足し合わせる（docs/ARCHITECTURE.md §4.3）。
 * 閲覧権限の条件は同じ SQL の WHERE 句に入れる（ADR-0004 のプレフィルタ）。CONFIDENTIAL は検索に出さない。
 */
@Component
class PgBigmSearchAdapter implements SearchPort {

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;
    private final NavigationWeights weights;

    PgBigmSearchAdapter(JdbcClient jdbc, AccessPolicy accessPolicy, NavigationWeights weights) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.weights = weights;
    }

    @Override
    public SearchCandidates search(List<Term> terms, AccessScope scope, int limit) {
        if (terms.isEmpty()) {
            return SearchCandidates.empty();
        }
        return new SearchCandidates(
                programs(terms, scope, limit), sections(terms, scope, limit), faqs(terms, scope, limit));
    }

    private List<Hit> programs(List<Term> terms, AccessScope scope, int limit) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.SEARCH);
        Scoring s =
                scoring(terms, i -> "strpos(lower(p.name), :t" + i + ") > 0", "p.search_text", weights.programName());
        return jdbc.sql("SELECT p.id, " + s.select() + " FROM programs p WHERE " + s.anyMatch() + " AND "
                        + visible.sql() + " ORDER BY score DESC, p.code LIMIT :limit")
                .params(s.params())
                .params(visible.params())
                .param("limit", limit)
                .query((rs, n) -> new Hit(rs.getObject("id", UUID.class), rs.getDouble("score"), matched(rs)))
                .list();
    }

    private List<SectionHit> sections(List<Term> terms, AccessScope scope, int limit) {
        // 公開中の版の条文だけを対象にする（過去の版は検索に出さない）
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.SEARCH);
        Scoring s = scoring(
                terms,
                i -> "strpos(lower(coalesce(s.heading, '') || ' ' || d.title), :t" + i + ") > 0",
                "s.search_text",
                weights.heading());
        return jdbc.sql("SELECT s.id, d.id AS document_id, " + s.select()
                        + " FROM sections s JOIN documents d ON d.current_version_id = s.document_version_id WHERE "
                        + s.anyMatch() + " AND " + visible.sql()
                        + " ORDER BY score DESC, d.code, s.ordinal LIMIT :limit")
                .params(s.params())
                .params(visible.params())
                .param("limit", limit)
                .query((rs, n) -> new SectionHit(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getDouble("score"),
                        matched(rs)))
                .list();
    }

    private List<Hit> faqs(List<Term> terms, AccessScope scope, int limit) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "faqs", "f", Purpose.SEARCH);
        Scoring s = scoring(
                terms, i -> "strpos(lower(f.question), :t" + i + ") > 0", "f.search_text", weights.faqQuestion());
        return jdbc.sql("SELECT f.id, " + s.select() + " FROM faqs f WHERE " + s.anyMatch() + " AND " + visible.sql()
                        + " ORDER BY score DESC, f.code LIMIT :limit")
                .params(s.params())
                .params(visible.params())
                .param("limit", limit)
                .query((rs, n) -> new Hit(rs.getObject("id", UUID.class), rs.getDouble("score"), matched(rs)))
                .list();
    }

    /**
     * 語ごとのスコアの式を組み立てる。語と語の重みは名前付きパラメーター（:t0, :tw0, ...）で渡し、SQL に埋め込まない。
     *
     * @param strongMatch 名前・見出しに含まれる条件（i 番目の語）
     * @param searchText 本文を含む検索用の列
     * @param strongWeight 名前・見出しに含まれるときの重み
     */
    private Scoring scoring(List<Term> terms, IntFunction<String> strongMatch, String searchText, double strongWeight) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder score = new StringBuilder("(0");
        StringBuilder matched = new StringBuilder("array_remove(ARRAY[NULL::text");
        StringBuilder any = new StringBuilder("(false");
        for (int i = 0; i < terms.size(); i++) {
            params.put("t" + i, terms.get(i).text());
            params.put("tw" + i, terms.get(i).weight());
            String like = searchText + " LIKE likequery(:t" + i + ")";
            score.append(" + CASE WHEN ")
                    .append(strongMatch.apply(i))
                    .append(" THEN :w_strong * :tw")
                    .append(i)
                    .append(" WHEN ")
                    .append(like)
                    .append(" THEN :w_body * :tw")
                    .append(i)
                    .append(" ELSE 0 END");
            matched.append(", CASE WHEN ")
                    .append(like)
                    .append(" THEN :t")
                    .append(i)
                    .append(" END");
            any.append(" OR ").append(like);
        }
        params.put("w_strong", strongWeight);
        params.put("w_body", weights.body());
        return new Scoring(
                score.append(") AS score, ")
                        .append(matched)
                        .append("], NULL) AS matched_terms")
                        .toString(),
                any.append(")").toString(),
                params);
    }

    private record Scoring(String select, String anyMatch, Map<String, Object> params) {}

    private static List<String> matched(ResultSet rs) throws SQLException {
        Array array = rs.getArray("matched_terms");
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
