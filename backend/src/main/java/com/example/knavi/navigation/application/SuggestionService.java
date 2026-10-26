package com.example.knavi.navigation.application;

import com.example.knavi.navigation.infrastructure.SuggestionQueries;
import com.example.knavi.navigation.infrastructure.SuggestionQueries.SuggestionRow;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.text.TextNormalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 検索欄の入力補完（E12-T6・PRD F-306）。入力に一致する制度名・きっかけを返す。
 *
 * <p>制度は閲覧権限（SEARCH）で絞り込んだものだけ（見えない制度の名前を候補で漏らさない。ADR-0004）。
 * 入力が短すぎる・長すぎるときは候補を返さない（エラーにはしない）。検索ログには残さない。
 */
@Service
@Transactional(readOnly = true)
public class SuggestionService {

    static final int LIMIT = 8;
    static final int MAX_INPUT_LENGTH = 50;

    private final SuggestionQueries queries;

    SuggestionService(SuggestionQueries queries) {
        this.queries = queries;
    }

    /**
     * @param type PROGRAM（制度。詳細画面へ）・LIFE_EVENT（きっかけ。その名前で検索する）
     * @param label 表示する名前
     */
    public record SuggestionItem(String type, UUID id, String code, String label) {}

    public List<SuggestionItem> suggest(String q, AuthenticatedUser user) {
        String normalized = TextNormalizer.normalize(q).replace(" ", "");
        if (normalized.isEmpty() || normalized.length() > MAX_INPUT_LENGTH) {
            return List.of();
        }
        List<SuggestionRow> rows = new ArrayList<>(queries.lifeEvents(normalized, LIMIT));
        rows.addAll(queries.programs(normalized, AccessScope.of(user), LIMIT));
        // 一致の強さ → きっかけを先に → 短い名前の順
        return rows.stream()
                .sorted(Comparator.comparingInt(SuggestionRow::rank)
                        .thenComparing(r -> "PROGRAM".equals(r.type()))
                        .thenComparingInt(r -> r.label().length()))
                .limit(LIMIT)
                .map(r -> new SuggestionItem(r.type(), r.id(), r.code(), r.label()))
                .toList();
    }
}
