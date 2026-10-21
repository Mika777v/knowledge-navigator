package com.example.knavi.insight.application;

import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 検索の分析（E10-T4）。コンテンツ担当者が「答えられなかった質問」を見つけ、FAQ・辞書を改善するために使う。
 *
 * <p>個人の検索履歴にならないよう、質問ごとの集計は<b>異なる利用者が一定数以上</b>検索したものだけを返す
 * （{@code knavi.insight.min-distinct-users}。利用者は検索ログの仮名で数える）。フィードバックのコメントは改善のために
 * 書いてもらうものなので個別に返すが、誰が書いたかは返さない。
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    static final int DEFAULT_DAYS = 30;
    public static final int MAX_LIMIT = 100;

    /** 制度・文書・FAQ がどれも0件だった検索 */
    private static final String ZERO_HIT = """
            (COALESCE((l.result_counts->>'programs')::int, 0) + COALESCE((l.result_counts->>'documents')::int, 0)
              + COALESCE((l.result_counts->>'faqs')::int, 0)) = 0
            """;

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;
    private final Clock clock;
    private final int minDistinctUsers;

    AnalyticsService(
            JdbcClient jdbc,
            AccessPolicy accessPolicy,
            Clock clock,
            @Value("${knavi.insight.min-distinct-users:2}") int minDistinctUsers) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
        this.minDistinctUsers = minDistinctUsers;
    }

    /**
     * 集計の期間（日本時間の日付。to を含む）。省略時は今日までの30日間
     *
     * @param from 開始日
     * @param to 終了日
     */
    public record Period(LocalDate from, LocalDate to) {

        Instant start() {
            return from.atStartOfDay(ZONE).toInstant();
        }

        Instant end() {
            return to.plusDays(1).atStartOfDay(ZONE).toInstant();
        }
    }

    public record QueryStat(
            String query, int searches, int users, int zeroHitSearches, int clickedSearches, Instant lastSearchedAt) {}

    public record FeedbackSummary(Period period, int helpful, int notHelpful, List<FeedbackComment> comments) {}

    public record FeedbackComment(String query, boolean helpful, String comment, Instant createdAt) {}

    public record QueryReport(Period period, int minDistinctUsers, List<QueryStat> queries) {}

    /** 何も見つからなかった質問（多い順） */
    public QueryReport zeroHits(LocalDate from, LocalDate to, int limit, AuthenticatedUser user) {
        accessPolicy.checkCanViewAnalytics(user);
        Period period = period(from, to);
        return new QueryReport(period, minDistinctUsers, queryStats(period, limit, true));
    }

    /** よく検索された質問（多い順）。ゼロヒットの割合とクリックされた割合も返す */
    public QueryReport topQueries(LocalDate from, LocalDate to, int limit, AuthenticatedUser user) {
        accessPolicy.checkCanViewAnalytics(user);
        Period period = period(from, to);
        return new QueryReport(period, minDistinctUsers, queryStats(period, limit, false));
    }

    /** 役に立った／立たなかったの件数と、コメント（新しい順） */
    public FeedbackSummary feedback(LocalDate from, LocalDate to, int limit, AuthenticatedUser user) {
        accessPolicy.checkCanViewAnalytics(user);
        Period period = period(from, to);
        record Counts(int helpful, int notHelpful) {}
        Counts counts = jdbc.sql("""
                        SELECT count(*) FILTER (WHERE f.helpful) AS helpful, count(*) FILTER (WHERE NOT f.helpful) AS not_helpful
                        FROM feedbacks f WHERE f.created_at >= :start AND f.created_at < :end
                        """)
                .param("start", Timestamp.from(period.start()))
                .param("end", Timestamp.from(period.end()))
                .query((rs, i) -> new Counts(rs.getInt("helpful"), rs.getInt("not_helpful")))
                .single();
        List<FeedbackComment> comments = jdbc.sql("""
                        SELECT l.query, f.helpful, f.comment, f.created_at FROM feedbacks f
                        JOIN search_logs l ON l.id = f.search_log_id
                        WHERE f.comment IS NOT NULL AND f.created_at >= :start AND f.created_at < :end
                        ORDER BY f.created_at DESC LIMIT :limit
                        """)
                .param("start", Timestamp.from(period.start()))
                .param("end", Timestamp.from(period.end()))
                .param("limit", limit)
                .query((rs, i) -> new FeedbackComment(
                        rs.getString("query"),
                        rs.getBoolean("helpful"),
                        rs.getString("comment"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
        return new FeedbackSummary(period, counts.helpful(), counts.notHelpful(), comments);
    }

    private List<QueryStat> queryStats(Period period, int limit, boolean zeroHitOnly) {
        // 同じ質問は、口語の言い回しなどを除いた形（normalized_query）でまとめる
        return jdbc.sql("""
                        SELECT l.normalized_query AS query, count(*) AS searches, count(DISTINCT l.user_hash) AS users,
                          count(*) FILTER (WHERE %s) AS zero_hits,
                          count(*) FILTER (WHERE EXISTS (SELECT 1 FROM search_clicks c WHERE c.search_log_id = l.id)) AS clicked,
                          max(l.created_at) AS last_searched_at
                        FROM search_logs l
                        WHERE l.created_at >= :start AND l.created_at < :end AND l.normalized_query <> ''
                        GROUP BY l.normalized_query
                        HAVING count(DISTINCT l.user_hash) >= :min_users %s
                        ORDER BY searches DESC, last_searched_at DESC LIMIT :limit
                        """.formatted(ZERO_HIT, zeroHitOnly ? "AND count(*) FILTER (WHERE " + ZERO_HIT + ") > 0" : ""))
                .param("start", Timestamp.from(period.start()))
                .param("end", Timestamp.from(period.end()))
                .param("min_users", minDistinctUsers)
                .param("limit", limit)
                .query((rs, i) -> new QueryStat(
                        rs.getString("query"),
                        rs.getInt("searches"),
                        rs.getInt("users"),
                        rs.getInt("zero_hits"),
                        rs.getInt("clicked"),
                        rs.getTimestamp("last_searched_at").toInstant()))
                .list();
    }

    private Period period(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1) : from;
        if (start.isAfter(end)) {
            throw ApiErrors.badRequest("開始日は終了日以前にしてください。");
        }
        if (ChronoUnit.DAYS.between(start, end) >= 366) {
            throw ApiErrors.badRequest("期間は1年以内にしてください。");
        }
        return new Period(start, end);
    }
}
