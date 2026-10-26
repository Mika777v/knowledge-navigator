package com.example.knavi.navigation.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.text.TextNormalizer;
import com.example.knavi.shared.web.ApiErrors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 類義語辞書の管理（E12-T2・PRD F-301。コンテンツ担当者・システム管理者）。更新は If-Match（row_version）で楽観ロックする。
 *
 * <p>1つの語（正規化後）は1つのグループにだけ入れられる。代表の語は必ずグループの語に含める
 */
@Service
@Transactional
public class SynonymService {

    static final int MAX_TERMS = 20;
    static final int MAX_LENGTH = 100;
    /** 新しく作るグループ（まだ ID がない）。ほかのすべてのグループと比べる */
    private static final UUID NO_GROUP = new UUID(0, 0);

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;
    private final AuditLog auditLog;

    SynonymService(JdbcClient jdbc, AccessPolicy accessPolicy, AuditLog auditLog) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.auditLog = auditLog;
    }

    /** @param terms グループの語（代表の語を含む。登録した順） */
    public record SynonymGroupView(UUID id, String canonical, List<String> terms, int rowVersion) {}

    /** 一覧（代表の語の順）。q を指定すると、その語を含むグループだけ */
    @Transactional(readOnly = true)
    public List<SynonymGroupView> list(String q, AuthenticatedUser user) {
        accessPolicy.checkCanManageDictionary(user);
        String normalized = TextNormalizer.normalize(q);
        List<UUID> ids = jdbc.sql("""
                        SELECT g.id FROM synonym_groups g
                        WHERE :q = '' OR EXISTS (SELECT 1 FROM synonym_terms t WHERE t.group_id = g.id
                          AND strpos(t.normalized_term, :q) > 0)
                        ORDER BY g.canonical, g.id
                        """).param("q", normalized).query(UUID.class).list();
        return views(ids);
    }

    @Transactional(readOnly = true)
    public SynonymGroupView get(UUID id, AuthenticatedUser user) {
        accessPolicy.checkCanManageDictionary(user);
        return find(id);
    }

    public SynonymGroupView create(String canonical, List<String> terms, AuthenticatedUser user) {
        accessPolicy.checkCanManageDictionary(user);
        Map<String, String> normalized = normalize(canonical, terms);
        checkNotInOtherGroups(normalized, NO_GROUP);
        UUID id = jdbc.sql("INSERT INTO synonym_groups (canonical) VALUES (:canonical) RETURNING id")
                .param("canonical", canonical.strip())
                .query(UUID.class)
                .single();
        insertTerms(id, normalized);
        auditLog.record(
                user.id(),
                "SYNONYM_GROUP_CREATED",
                "SYNONYM_GROUP",
                id,
                Map.of("canonical", canonical.strip(), "terms", List.copyOf(normalized.values())));
        return find(id);
    }

    public SynonymGroupView update(
            UUID id, int expectedVersion, String canonical, List<String> terms, AuthenticatedUser user) {
        accessPolicy.checkCanManageDictionary(user);
        find(id);
        Map<String, String> normalized = normalize(canonical, terms);
        checkNotInOtherGroups(normalized, id);
        int updated = jdbc.sql("""
                        UPDATE synonym_groups SET canonical = :canonical, row_version = row_version + 1, updated_at = now()
                        WHERE id = :id AND row_version = :version
                        """)
                .param("canonical", canonical.strip())
                .param("id", id)
                .param("version", expectedVersion)
                .update();
        if (updated == 0) {
            throw ApiErrors.preconditionFailed();
        }
        jdbc.sql("DELETE FROM synonym_terms WHERE group_id = :id")
                .param("id", id)
                .update();
        insertTerms(id, normalized);
        auditLog.record(
                user.id(),
                "SYNONYM_GROUP_UPDATED",
                "SYNONYM_GROUP",
                id,
                Map.of("canonical", canonical.strip(), "terms", List.copyOf(normalized.values())));
        return find(id);
    }

    public void delete(UUID id, AuthenticatedUser user) {
        accessPolicy.checkCanManageDictionary(user);
        SynonymGroupView group = find(id);
        jdbc.sql("DELETE FROM synonym_groups WHERE id = :id").param("id", id).update();
        auditLog.record(
                user.id(), "SYNONYM_GROUP_DELETED", "SYNONYM_GROUP", id, Map.of("canonical", group.canonical()));
    }

    /**
     * 代表の語を先頭にして、正規化後の重複・空を除く。語が2つ未満・多すぎる・長すぎるときは 400
     *
     * @return 正規化した語 → 入力の語（入力の順）
     */
    static Map<String, String> normalize(String canonical, List<String> terms) {
        List<String> all = new ArrayList<>();
        all.add(canonical == null ? "" : canonical);
        if (terms != null) {
            all.addAll(terms);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String term : all) {
            String n = TextNormalizer.normalize(term);
            if (n.isEmpty()) {
                continue;
            }
            if (n.length() > MAX_LENGTH) {
                throw ApiErrors.badRequest("語は" + MAX_LENGTH + "文字以内にしてください: " + n);
            }
            result.putIfAbsent(n, term.strip());
        }
        if (TextNormalizer.normalize(canonical).isEmpty()) {
            throw ApiErrors.badRequest("代表の語を入力してください。");
        }
        if (result.size() < 2) {
            throw ApiErrors.badRequest("代表の語のほかに、同じ意味の語を1つ以上入力してください。");
        }
        if (result.size() > MAX_TERMS) {
            throw ApiErrors.badRequest("1つのグループの語は" + MAX_TERMS + "個までです。");
        }
        return result;
    }

    /** 語がほかのグループに入っていたら 409（書き込む前に確かめる） */
    private void checkNotInOtherGroups(Map<String, String> terms, UUID groupId) {
        for (var e : terms.entrySet()) {
            Optional<String> other = jdbc.sql("""
                            SELECT g.canonical FROM synonym_terms t JOIN synonym_groups g ON g.id = t.group_id
                            WHERE t.normalized_term = :n AND t.group_id <> :group
                            """)
                    .param("n", e.getKey())
                    .param("group", groupId)
                    .query(String.class)
                    .optional();
            if (other.isPresent()) {
                throw ApiErrors.conflict("「" + e.getValue() + "」は類義語「" + other.get() + "」のグループに登録されています。");
            }
        }
    }

    private void insertTerms(UUID groupId, Map<String, String> terms) {
        terms.forEach((normalized, term) -> jdbc.sql(
                        "INSERT INTO synonym_terms (group_id, term, normalized_term) VALUES (:group, :term, :n)")
                .param("group", groupId)
                .param("term", term)
                .param("n", normalized)
                .update());
    }

    private SynonymGroupView find(UUID id) {
        List<SynonymGroupView> found = views(List.of(id));
        if (found.isEmpty()) {
            throw ApiErrors.notFound("類義語のグループが見つかりません: " + id);
        }
        return found.getFirst();
    }

    private List<SynonymGroupView> views(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<String>> terms = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT t.group_id, t.term FROM synonym_terms t JOIN synonym_groups g ON g.id = t.group_id
                        WHERE t.group_id IN (:ids)
                        ORDER BY t.group_id, t.term <> g.canonical, t.normalized_term COLLATE "C"
                        """).param("ids", ids).query(rs -> {
            terms.computeIfAbsent(rs.getObject("group_id", UUID.class), k -> new ArrayList<>())
                    .add(rs.getString("term"));
        });
        Map<UUID, SynonymGroupView> byId = new LinkedHashMap<>();
        jdbc.sql("SELECT id, canonical, row_version FROM synonym_groups WHERE id IN (:ids)")
                .param("ids", ids)
                .query(rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    byId.put(
                            id,
                            new SynonymGroupView(
                                    id,
                                    rs.getString("canonical"),
                                    List.copyOf(terms.getOrDefault(id, List.of())),
                                    rs.getInt("row_version")));
                });
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }
}
