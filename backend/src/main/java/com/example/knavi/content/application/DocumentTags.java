package com.example.knavi.content.application;

import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.SqlPredicate;
import com.example.knavi.shared.web.ApiErrors;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 文書のタグ（対象者・話題など）。タグは名前で扱い、文書に付けるときに無ければ自由タグ（FREE）として作る。
 *
 * <p>種類: TARGET（対象者。例: 正社員）・TOPIC（話題。例: お金）・FREE（自由）。表示はこの順にする
 */
@Component
public class DocumentTags {

    static final int MAX_TAGS = 10;
    static final int MAX_LENGTH = 50;

    /** 表示の順（種類 → 名前） */
    static final String ORDER = "CASE t.tag_type WHEN 'TARGET' THEN 0 WHEN 'TOPIC' THEN 1 ELSE 2 END, t.name";

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;

    DocumentTags(JdbcClient jdbc, AccessPolicy accessPolicy) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
    }

    /** @param documentCount そのタグが付いた文書のうち、利用者が閲覧できる公開中のものの数 */
    public record TagCount(String name, String type, int documentCount) {}

    public List<String> namesOf(UUID documentId) {
        return jdbc.sql("SELECT t.name FROM document_tags dt JOIN tags t ON t.id = dt.tag_id"
                        + " WHERE dt.document_id = :id ORDER BY " + ORDER)
                .param("id", documentId)
                .query(String.class)
                .list();
    }

    /**
     * 文書のタグを置き換える。名前は NFKC で正規化し、重複を除く。
     *
     * @return 変わったら true
     */
    public boolean replace(UUID documentId, List<String> names) {
        List<String> normalized = normalize(names);
        if (normalized.equals(namesOf(documentId).stream().sorted().toList())) {
            return false;
        }
        jdbc.sql("DELETE FROM document_tags WHERE document_id = :id")
                .param("id", documentId)
                .update();
        for (String name : normalized) {
            jdbc.sql("INSERT INTO tags (name) VALUES (:name) ON CONFLICT (name) DO NOTHING")
                    .param("name", name)
                    .update();
            jdbc.sql("""
                            INSERT INTO document_tags (document_id, tag_id)
                            SELECT :id, id FROM tags WHERE name = :name
                            """).param("id", documentId).param("name", name).update();
        }
        return true;
    }

    /** 利用者が閲覧できる公開中の文書に付いているタグと件数（件数から見えない文書の存在が分からないよう、見える文書だけで数える） */
    public List<TagCount> visibleTags(AccessScope scope) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.VIEW);
        return jdbc.sql("SELECT t.name, t.tag_type, count(*) AS documents FROM tags t"
                        + " JOIN document_tags dt ON dt.tag_id = t.id JOIN documents d ON d.id = dt.document_id"
                        + " WHERE " + visible.sql()
                        + " GROUP BY t.id, t.name, t.tag_type ORDER BY " + ORDER)
                .params(visible.params())
                .query((rs, i) -> new TagCount(rs.getString("name"), rs.getString("tag_type"), rs.getInt("documents")))
                .list();
    }

    /** NFKC・前後の空白を除き、空・重複を除いて名前順にする。長すぎる・多すぎるときは 400 */
    static List<String> normalize(List<String> names) {
        var result = new LinkedHashSet<String>();
        for (String name : names == null ? List.<String>of() : names) {
            String n = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKC)
                    .strip()
                    .replaceAll("\\s+", " ");
            if (n.isEmpty()) {
                continue;
            }
            if (n.length() > MAX_LENGTH) {
                throw ApiErrors.badRequest("タグは" + MAX_LENGTH + "文字以内にしてください: " + n);
            }
            result.add(n);
        }
        if (result.size() > MAX_TAGS) {
            throw ApiErrors.badRequest("タグは1つの文書に" + MAX_TAGS + "個までです。");
        }
        return result.stream().sorted().toList();
    }
}
