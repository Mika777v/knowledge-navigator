package com.example.knavi.navigation.application;

import com.example.knavi.insight.ProgramCoClicks;
import com.example.knavi.navigation.infrastructure.NavigationQueries;
import com.example.knavi.navigation.infrastructure.NavigationQueries.ProgramRow;
import com.example.knavi.navigation.infrastructure.RecommendationQueries;
import com.example.knavi.navigation.infrastructure.RecommendationQueries.LifeEventSibling;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.AuthenticatedUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 制度の推薦（E12-T5・PRD F-305）。制度の詳細で「この制度を見た人はこちらも」を出す。
 *
 * <ol>
 *   <li>検索結果から同じ人があわせて開いた制度（{@link ProgramCoClicks}。一定の人数以上のものだけ）
 *   <li>同じきっかけに結び付いた制度（結婚祝金 → 結婚休暇・氏名・住所・扶養の変更届）
 * </ol>
 *
 * <p>元の制度・推薦する制度とも、検索と同じ権限（SEARCH）で見えるものだけを扱う。元の制度が見えなければ空を返す
 * （404 と区別しないことで、見えない制度の有無を推薦から分からないようにする）。
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    static final int LIMIT = 5;

    private final NavigationQueries navigation;
    private final RecommendationQueries queries;
    private final ProgramCoClicks coClicks;

    RecommendationService(NavigationQueries navigation, RecommendationQueries queries, ProgramCoClicks coClicks) {
        this.navigation = navigation;
        this.queries = queries;
        this.coClicks = coClicks;
    }

    /** @param reason CO_CLICK（あわせて見られている）・LIFE_EVENT（同じきっかけ） @param lifeEvent LIFE_EVENT のときのきっかけの名前 */
    public record RecommendedProgram(UUID id, String code, String name, String reason, String lifeEvent) {}

    public List<RecommendedProgram> recommend(UUID programId, AuthenticatedUser user) {
        AccessScope scope = AccessScope.of(user);
        if (navigation.programs(List.of(programId), scope).isEmpty()) {
            return List.of();
        }
        Map<UUID, String[]> candidates = new LinkedHashMap<>();
        coClicks.coClickedWith(programId, LIMIT * 2)
                .forEach(id -> candidates.putIfAbsent(id, new String[] {"CO_CLICK", null}));
        for (LifeEventSibling s : queries.lifeEventSiblings(programId)) {
            candidates.putIfAbsent(s.programId(), new String[] {"LIFE_EVENT", s.lifeEvent()});
        }
        candidates.remove(programId);
        Map<UUID, ProgramRow> visible = navigation.programs(candidates.keySet(), scope).stream()
                .collect(Collectors.toMap(ProgramRow::id, Function.identity()));
        return candidates.entrySet().stream()
                .filter(e -> visible.containsKey(e.getKey()))
                .limit(LIMIT)
                .map(e -> {
                    ProgramRow p = visible.get(e.getKey());
                    return new RecommendedProgram(p.id(), p.code(), p.name(), e.getValue()[0], e.getValue()[1]);
                })
                .toList();
    }
}
