package com.example.knavi.navigation.infrastructure;

import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.SqlPredicate;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 検索結果の組み立てに使う読み取り。制度・文書・FAQ は {@link AccessPolicy#readPredicate}（SEARCH）で絞り込む。
 * 窓口ときっかけには公開範囲がないため、絞り込まない。
 */
@Repository
public class NavigationQueries {

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;

    NavigationQueries(JdbcClient jdbc, AccessPolicy accessPolicy) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
    }

    // ------------------------------------------------------------------ 辞書・きっかけ

    public record KeywordMatch(String term, String targetType, UUID targetId, double weight) {}

    /**
     * 質問に含まれる辞書の語をすべて返す（辞書の件数は数百件の想定のため、全件と照合する）。
     *
     * <p>言い方の活用のちがい（「眠れない」と「眠れなくて」、「出勤時間をずらす」と「出勤時間を遅くしたい」）も拾えるよう、
     * 語がそのまま含まれないときは語幹でも照合する。語幹は次のどちらか（形態素解析は Phase3 で導入する）。
     *
     * <ul>
     *   <li>「名詞＋を/が/に/は＋ひらがな」なら、助詞の前まで（出勤時間をずらす → 出勤時間）
     *   <li>それ以外は、末尾の活用語尾（ない・たい・た・る など）を除いたもの（眠れない → 眠れ）
     * </ul>
     *
     * <p>語幹は2文字以上で漢字・カタカナ・英数字を含むものだけを使い、一致の強さは {@code stemWeight} を掛けて弱める。
     *
     * @param normalizedQuery 正規化した質問
     * @param stemWeight 語幹で一致したときに掛ける重み（0〜1）
     */
    public List<KeywordMatch> keywordsIn(String normalizedQuery, double stemWeight) {
        return jdbc.sql("""
                        WITH k AS (
                          SELECT normalized_term, target_type, target_id, weight,
                            CASE WHEN normalized_term ~ '[をがには][ぁ-ゖ]+$'
                                 THEN regexp_replace(normalized_term, '[をがには][ぁ-ゖ]+$', '')
                                 ELSE regexp_replace(normalized_term, '(ない|たい|った|して|した|する|ます|る|た|て|す)$', '')
                            END AS stem
                          FROM navigation_keywords WHERE char_length(normalized_term) >= 2
                        )
                        SELECT normalized_term AS matched, target_type, target_id, weight FROM k
                        WHERE strpos(:q, normalized_term) > 0
                        UNION ALL
                        SELECT stem, target_type, target_id, weight * :stem_weight FROM k
                        WHERE strpos(:q, normalized_term) = 0 AND stem <> normalized_term
                          AND char_length(stem) >= 2 AND stem ~ '[^ぁ-ゖ]' AND strpos(:q, stem) > 0
                        """)
                .param("q", normalizedQuery)
                .param("stem_weight", stemWeight)
                .query((rs, i) -> new KeywordMatch(
                        rs.getString("matched"),
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        rs.getDouble("weight")))
                .list();
    }

    public record LifeEventProgramRow(UUID lifeEventId, String code, String name, UUID programId, String relation) {}

    public List<LifeEventProgramRow> lifeEventPrograms(Collection<UUID> lifeEventIds) {
        if (lifeEventIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT e.id, e.code, e.name, lp.program_id, lp.relation FROM life_events e
                        JOIN life_event_programs lp ON lp.life_event_id = e.id
                        WHERE e.id IN (:ids) ORDER BY e.sort_order, lp.relation, lp.sort_order
                        """)
                .param("ids", lifeEventIds)
                .query((rs, i) -> new LifeEventProgramRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getObject("program_id", UUID.class),
                        rs.getString("relation")))
                .list();
    }

    // ------------------------------------------------------------------ 制度

    public record ProgramRow(UUID id, String code, String name, String summary, String benefit, String deadlineNote) {}

    public List<ProgramRow> programs(Collection<UUID> ids, AccessScope scope) {
        if (ids.isEmpty()) {
            return List.of();
        }
        SqlPredicate visible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.SEARCH);
        return jdbc.sql("SELECT p.id, p.code, p.name, p.summary, p.benefit, p.deadline_note FROM programs p"
                        + " WHERE p.id IN (:ids) AND " + visible.sql())
                .params(visible.params())
                .param("ids", ids)
                .query((rs, i) -> new ProgramRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("summary"),
                        rs.getString("benefit"),
                        rs.getString("deadline_note")))
                .list();
    }

    public record BasisRow(
            UUID programId,
            UUID documentId,
            String documentTitle,
            String versionLabel,
            UUID sectionId,
            String articleNo) {}

    /** 制度の根拠。文書ごとに閲覧条件で絞る（制度が見えても根拠の文書が見えるとは限らない） */
    public List<BasisRow> basis(Collection<UUID> programIds, AccessScope scope) {
        if (programIds.isEmpty()) {
            return List.of();
        }
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.SEARCH);
        return jdbc.sql("""
                        SELECT b.program_id, d.id AS document_id, d.title, v.version_label, b.section_id, b.article_no
                        FROM program_basis b JOIN documents d ON d.id = b.document_id
                        JOIN document_versions v ON v.id = d.current_version_id
                        WHERE b.program_id IN (:ids) AND """ + visible.sql() + " ORDER BY b.sort_order")
                .params(visible.params())
                .param("ids", programIds)
                .query((rs, i) -> new BasisRow(
                        rs.getObject("program_id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("title"),
                        rs.getString("version_label"),
                        rs.getObject("section_id", UUID.class),
                        rs.getString("article_no")))
                .list();
    }

    // ------------------------------------------------------------------ 文書・条文

    /** @param hasSourceFile 公開中の版に原本（PDF）がある */
    public record DocumentRow(
            UUID id, String code, String title, String versionLabel, LocalDate effectiveFrom, boolean hasSourceFile) {}

    public List<DocumentRow> documents(Collection<UUID> ids, AccessScope scope) {
        if (ids.isEmpty()) {
            return List.of();
        }
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.SEARCH);
        return jdbc.sql("SELECT d.id, d.code, d.title, v.version_label, v.effective_from,"
                        + " v.source_file_id IS NOT NULL AS has_source FROM documents d"
                        + " JOIN document_versions v ON v.id = d.current_version_id WHERE d.id IN (:ids) AND "
                        + visible.sql())
                .params(visible.params())
                .param("ids", ids)
                .query((rs, i) -> new DocumentRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("title"),
                        rs.getString("version_label"),
                        rs.getObject("effective_from", LocalDate.class),
                        rs.getBoolean("has_source")))
                .list();
    }

    public record SectionRow(
            UUID id,
            UUID documentId,
            int ordinal,
            String articleNo,
            String heading,
            String content,
            Integer pageFrom,
            Integer pageTo) {}

    /** 条文の本文。文書の閲覧可否は呼び出し側で {@link #documents} により確認済みであること */
    public List<SectionRow> sections(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT s.id, v.document_id, s.ordinal, s.article_no, s.heading, s.content, s.page_from, s.page_to
                        FROM sections s
                        JOIN document_versions v ON v.id = s.document_version_id WHERE s.id IN (:ids)
                        """)
                .param("ids", ids)
                .query((rs, i) -> new SectionRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getInt("ordinal"),
                        rs.getString("article_no"),
                        rs.getString("heading"),
                        rs.getString("content"),
                        rs.getObject("page_from", Integer.class),
                        rs.getObject("page_to", Integer.class)))
                .list();
    }

    // ------------------------------------------------------------------ FAQ

    public record FaqRow(UUID id, String code, String question, UUID contactId) {}

    public List<FaqRow> faqs(Collection<UUID> ids, AccessScope scope) {
        if (ids.isEmpty()) {
            return List.of();
        }
        SqlPredicate visible = accessPolicy.readPredicate(scope, "faqs", "f", Purpose.SEARCH);
        return jdbc.sql("SELECT f.id, f.code, f.question, f.contact_id FROM faqs f WHERE f.id IN (:ids) AND "
                        + visible.sql())
                .params(visible.params())
                .param("ids", ids)
                .query((rs, i) -> new FaqRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("question"),
                        rs.getObject("contact_id", UUID.class)))
                .list();
    }

    public record FaqLinkRow(UUID faqId, UUID programId) {}

    /** 制度に紐づく FAQ（FAQ の閲覧可否は {@link #faqs} で確認する） */
    public List<FaqLinkRow> faqsLinkedTo(Collection<UUID> programIds) {
        if (programIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT faq_id, target_id FROM faq_links WHERE target_type = 'PROGRAM' AND target_id IN (:ids)
                        """)
                .param("ids", programIds)
                .query((rs, i) ->
                        new FaqLinkRow(rs.getObject("faq_id", UUID.class), rs.getObject("target_id", UUID.class)))
                .list();
    }

    // ------------------------------------------------------------------ 窓口

    public record ContactRow(
            UUID id,
            String code,
            String name,
            String department,
            String phoneExt,
            String email,
            String chatUrl,
            String officeHours) {}

    public record ProgramContactRow(UUID programId, String role, ContactRow contact) {}

    private static final String CONTACT_COLUMNS =
            "ct.id, ct.code, ct.name, dep.name AS dept_name, ct.phone_ext, ct.email, ct.chat_url, ct.office_hours";

    public List<ProgramContactRow> programContacts(Collection<UUID> programIds) {
        if (programIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT pc.program_id, pc.role, " + CONTACT_COLUMNS
                        + " FROM program_contacts pc JOIN contacts ct ON ct.id = pc.contact_id"
                        + " JOIN departments dep ON dep.id = ct.department_id WHERE pc.program_id IN (:ids)"
                        + " ORDER BY pc.sort_order")
                .param("ids", programIds)
                .query((rs, i) -> new ProgramContactRow(
                        rs.getObject("program_id", UUID.class), rs.getString("role"), contact(rs)))
                .list();
    }

    public List<ContactRow> contacts(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + CONTACT_COLUMNS
                        + " FROM contacts ct JOIN departments dep ON dep.id = ct.department_id WHERE ct.id IN (:ids)")
                .param("ids", ids)
                .query((rs, i) -> contact(rs))
                .list();
    }

    /** 該当する窓口が見つからないときに案内する総合窓口（app_settings の navigation.default_contact_code） */
    public Optional<ContactRow> defaultContact() {
        return jdbc.sql("SELECT " + CONTACT_COLUMNS + " FROM app_settings s JOIN contacts ct ON ct.code = s.value"
                        + " JOIN departments dep ON dep.id = ct.department_id"
                        + " WHERE s.key = 'navigation.default_contact_code'")
                .query((rs, i) -> contact(rs))
                .optional();
    }

    private static ContactRow contact(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ContactRow(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("dept_name"),
                rs.getString("phone_ext"),
                rs.getString("email"),
                rs.getString("chat_url"),
                rs.getString("office_hours"));
    }
}
