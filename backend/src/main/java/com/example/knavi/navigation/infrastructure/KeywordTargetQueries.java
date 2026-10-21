package com.example.knavi.navigation.infrastructure;

import com.example.knavi.navigation.domain.KeywordTargetType;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.SqlPredicate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 辞書の管理画面で使う、キーワードの案内先（制度・きっかけ・文書・窓口・FAQ）の表示名。
 *
 * <p>制度・文書・FAQ は操作する人の閲覧条件（VIEW）で絞る。辞書を管理できる人でも、閲覧できない資源の名前は見せない
 * （例: システム管理者は辞書を管理できるが、機密の制度は閲覧できない）。きっかけと窓口には公開範囲がない。
 */
@Repository
public class KeywordTargetQueries {

    public record Target(UUID id, String code, String label) {}

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;

    KeywordTargetQueries(JdbcClient jdbc, AccessPolicy accessPolicy) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
    }

    /** 種類ごとの案内先の一覧（名前順） */
    public List<Target> list(KeywordTargetType type, AccessScope scope) {
        return query(type, scope, null);
    }

    /** ID → 表示名。閲覧できない案内先は含めない */
    public Map<UUID, String> labels(KeywordTargetType type, Collection<UUID> ids, AccessScope scope) {
        Map<UUID, String> labels = new HashMap<>();
        if (!ids.isEmpty()) {
            query(type, scope, ids).forEach(t -> labels.put(t.id(), t.label()));
        }
        return labels;
    }

    private List<Target> query(KeywordTargetType type, AccessScope scope, Collection<UUID> ids) {
        String idFilter = ids == null ? "" : " AND x.id IN (:ids)";
        SqlPredicate visible = switch (type) {
            case PROGRAM -> accessPolicy.readPredicate(scope, "programs", "x", Purpose.VIEW);
            case DOCUMENT -> accessPolicy.readPredicate(scope, "documents", "x", Purpose.VIEW);
            case FAQ -> accessPolicy.readPredicate(scope, "faqs", "x", Purpose.VIEW);
            case LIFE_EVENT, CONTACT -> new SqlPredicate("true", Map.of());
        };
        String sql = switch (type) {
            case PROGRAM -> "SELECT x.id, x.code, x.name AS label FROM programs x";
            case DOCUMENT -> "SELECT x.id, x.code, x.title AS label FROM documents x";
            case FAQ -> "SELECT x.id, x.code, x.question AS label FROM faqs x";
            case LIFE_EVENT -> "SELECT x.id, x.code, x.name AS label FROM life_events x";
            case CONTACT -> "SELECT x.id, x.code, x.name AS label FROM contacts x";
        };
        var statement = jdbc.sql(sql + " WHERE " + visible.sql() + idFilter + " ORDER BY x.code")
                .params(visible.params());
        if (ids != null) {
            statement = statement.param("ids", ids);
        }
        return statement
                .query((rs, i) ->
                        new Target(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("label")))
                .list();
    }
}
