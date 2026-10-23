package com.example.knavi.content.application;

import com.example.knavi.content.application.ContentViews.BasisView;
import com.example.knavi.content.application.ContentViews.CategoryNode;
import com.example.knavi.content.application.ContentViews.CategoryRef;
import com.example.knavi.content.application.ContentViews.ContactView;
import com.example.knavi.content.application.ContentViews.DepartmentRef;
import com.example.knavi.content.application.ContentViews.DocumentDetail;
import com.example.knavi.content.application.ContentViews.DocumentProgram;
import com.example.knavi.content.application.ContentViews.DocumentSummary;
import com.example.knavi.content.application.ContentViews.FaqDetail;
import com.example.knavi.content.application.ContentViews.FaqSummary;
import com.example.knavi.content.application.ContentViews.LifeEventDetail;
import com.example.knavi.content.application.ContentViews.LifeEventProgram;
import com.example.knavi.content.application.ContentViews.LifeEventSummary;
import com.example.knavi.content.application.ContentViews.ProgramDetail;
import com.example.knavi.content.application.ContentViews.ProgramSummary;
import com.example.knavi.content.application.ContentViews.SectionDetail;
import com.example.knavi.content.application.ContentViews.SectionView;
import com.example.knavi.content.application.ContentViews.VersionView;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.SqlPredicate;
import com.example.knavi.shared.web.ApiErrors;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 一般利用者向けの閲覧（文書・条文・制度・FAQ・きっかけ）。
 *
 * <p>すべての問い合わせに {@link AccessPolicy#readPredicate} の条件を付け、見えてはいけないものは取得しない（ADR-0004）。
 * 詳細が見えない場合は「存在しない」と区別できないよう 404 を返す。関連（制度の根拠、FAQ の参照先など）も1件ずつ同じ条件で絞る。
 *
 * <p>読み取りは画面の表示に合わせた SQL で組み立てる（部署・窓口の表示名は JOIN で取得する）。
 */
@Service
@Transactional(readOnly = true)
public class ContentReadService {

    private static final String DOCUMENT_COLUMNS = """
            d.id, d.code, d.title, d.summary, d.document_type, c.slug AS category_slug, c.name AS category_name,
            dep.code AS dept_code, dep.name AS dept_name, v.version_label, v.effective_from,
            """
            + "ARRAY(SELECT t.name FROM document_tags dt JOIN tags t ON t.id = dt.tag_id WHERE dt.document_id = d.id"
            + " ORDER BY " + DocumentTags.ORDER + ") AS tags ";
    private static final String DOCUMENT_FROM = """
            FROM documents d
            JOIN categories c ON c.id = d.category_id
            JOIN departments dep ON dep.id = d.owner_department_id
            JOIN document_versions v ON v.id = d.current_version_id
            """;
    private static final String PROGRAM_COLUMNS =
            "p.id, p.code, p.name, p.summary, p.benefit, p.deadline_note, c.slug AS category_slug, c.name AS category_name";

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;

    ContentReadService(JdbcClient jdbc, AccessPolicy accessPolicy) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
    }

    // ------------------------------------------------------------------ 文書・条文

    public List<DocumentSummary> listDocuments(AccessScope scope, String categorySlug, String tag) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.VIEW);
        return jdbc.sql("SELECT " + DOCUMENT_COLUMNS + DOCUMENT_FROM + " WHERE " + visible.sql()
                        + " AND (CAST(:category AS text) IS NULL OR c.slug = :category OR c.parent_id = (SELECT id FROM categories WHERE slug = :category))"
                        + " AND (CAST(:tag AS text) IS NULL OR EXISTS (SELECT 1 FROM document_tags dt JOIN tags t ON t.id = dt.tag_id"
                        + " WHERE dt.document_id = d.id AND t.name = :tag))"
                        + " ORDER BY d.code")
                .params(visible.params())
                .param("category", categorySlug)
                .param("tag", tag)
                .query(ContentReadService::documentSummary)
                .list();
    }

    /**
     * 文書の詳細。
     *
     * @param versionId 表示する版（null なら公開中の版）。公開済みの過去の版も指定できる
     */
    public DocumentDetail getDocument(AccessScope scope, UUID id, UUID versionId) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.VIEW);
        var row = jdbc.sql("SELECT " + DOCUMENT_COLUMNS + ", d.current_version_id " + DOCUMENT_FROM
                        + " WHERE d.id = :id AND " + visible.sql())
                .params(visible.params())
                .param("id", id)
                .query((rs, i) -> Map.entry(documentSummary(rs, i), rs.getObject("current_version_id", UUID.class)))
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("文書が見つかりません。"));
        UUID currentVersionId = row.getValue();

        List<VersionView> versions = jdbc.sql("""
                        SELECT id, version_label, effective_from, change_note FROM document_versions
                        WHERE document_id = :id AND published_at IS NOT NULL ORDER BY effective_from DESC, published_at DESC
                        """)
                .param("id", id)
                .query((rs, i) -> {
                    UUID vid = rs.getObject("id", UUID.class);
                    return new VersionView(
                            vid,
                            rs.getString("version_label"),
                            rs.getObject("effective_from", LocalDate.class),
                            rs.getString("change_note"),
                            vid.equals(currentVersionId));
                })
                .list();
        UUID shownId = versionId == null ? currentVersionId : versionId;
        // 下書きの版や他の文書の版は「見つからない」扱いにする
        VersionView shown = versions.stream()
                .filter(v -> v.id().equals(shownId))
                .findFirst()
                .orElseThrow(() -> ApiErrors.notFound("文書の版が見つかりません。"));

        var sections = jdbc.sql("""
                        SELECT id, ordinal, path, article_no, heading, content FROM sections
                        WHERE document_version_id = :version ORDER BY ordinal
                        """)
                .param("version", shown.id())
                .query(ContentReadService::sectionView)
                .list();

        SqlPredicate programVisible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.VIEW);
        record BasisRow(UUID id, String code, String name, String summary, String articleNo) {}
        var basisRows = jdbc.sql("SELECT p.id, p.code, p.name, p.summary, b.article_no FROM program_basis b"
                        + " JOIN programs p ON p.id = b.program_id WHERE b.document_id = :id AND "
                        + programVisible.sql()
                        + " ORDER BY p.code, b.sort_order")
                .params(programVisible.params())
                .param("id", id)
                .query((rs, i) -> new BasisRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("summary"),
                        rs.getString("article_no")))
                .list();
        Map<UUID, DocumentProgram> programs = new LinkedHashMap<>();
        for (BasisRow b : basisRows) {
            DocumentProgram p = programs.computeIfAbsent(
                    b.id(), k -> new DocumentProgram(b.id(), b.code(), b.name(), b.summary(), new ArrayList<>()));
            if (b.articleNo() != null && !p.articleNos().contains(b.articleNo())) {
                p.articleNos().add(b.articleNo());
            }
        }

        List<ContactView> contacts = programs.isEmpty()
                ? List.of()
                : jdbc.sql("""
                                SELECT DISTINCT ON (ct.code) ct.code, ct.name, ct.description, dep.code AS dept_code,
                                  dep.name AS dept_name, ct.email, ct.phone_ext, ct.chat_url, ct.office_hours, NULL AS role
                                FROM program_contacts pc JOIN contacts ct ON ct.id = pc.contact_id
                                JOIN departments dep ON dep.id = ct.department_id
                                WHERE pc.program_id IN (:ids) ORDER BY ct.code
                                """)
                        .param("ids", programs.keySet())
                        .query(ContentReadService::contactView)
                        .list();

        return new DocumentDetail(
                row.getKey(),
                currentVersionId,
                shown,
                versions,
                sections,
                programs.values().stream()
                        .map(p -> new DocumentProgram(
                                p.id(), p.code(), p.name(), p.summary(), List.copyOf(p.articleNos())))
                        .toList(),
                contacts);
    }

    public SectionDetail getSection(AccessScope scope, UUID sectionId) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.VIEW);
        return jdbc.sql(
                        "SELECT s.id AS s_id, s.ordinal, s.path, s.article_no, s.heading, s.content, sv.version_label AS s_version, "
                                + "(sv.id = d.current_version_id) AS is_current, " + DOCUMENT_COLUMNS
                                + " FROM sections s JOIN document_versions sv ON sv.id = s.document_version_id "
                                + "JOIN documents d ON d.id = sv.document_id "
                                + "JOIN categories c ON c.id = d.category_id JOIN departments dep ON dep.id = d.owner_department_id "
                                + "JOIN document_versions v ON v.id = d.current_version_id WHERE s.id = :id AND "
                                + visible.sql())
                .params(visible.params())
                .param("id", sectionId)
                .query((rs, i) -> new SectionDetail(
                        new SectionView(
                                rs.getObject("s_id", UUID.class),
                                rs.getInt("ordinal"),
                                rs.getString("path"),
                                rs.getString("article_no"),
                                rs.getString("heading"),
                                rs.getString("content")),
                        documentSummary(rs, i),
                        rs.getString("s_version"),
                        rs.getBoolean("is_current")))
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("条文が見つかりません。"));
    }

    // ------------------------------------------------------------------ 制度

    public List<ProgramSummary> listPrograms(AccessScope scope, String categorySlug) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.VIEW);
        return jdbc.sql("SELECT " + PROGRAM_COLUMNS
                        + " FROM programs p LEFT JOIN categories c ON c.id = p.category_id WHERE "
                        + visible.sql()
                        + " AND (CAST(:category AS text) IS NULL OR c.slug = :category OR c.parent_id = (SELECT id FROM categories WHERE slug = :category))"
                        + " ORDER BY p.code")
                .params(visible.params())
                .param("category", categorySlug)
                .query(ContentReadService::programSummary)
                .list();
    }

    public ProgramDetail getProgram(AccessScope scope, UUID id) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.VIEW);
        record Row(
                ProgramSummary summary,
                String kana,
                String eligibility,
                String howToApply,
                String docs,
                DepartmentRef owner) {}
        Row row = jdbc.sql("SELECT " + PROGRAM_COLUMNS
                        + ", p.name_kana, p.eligibility, p.how_to_apply, p.required_documents, dep.code AS dept_code, dep.name AS dept_name"
                        + " FROM programs p LEFT JOIN categories c ON c.id = p.category_id"
                        + " JOIN departments dep ON dep.id = p.owner_department_id WHERE p.id = :id AND "
                        + visible.sql())
                .params(visible.params())
                .param("id", id)
                .query((rs, i) -> new Row(
                        programSummary(rs, i),
                        rs.getString("name_kana"),
                        rs.getString("eligibility"),
                        rs.getString("how_to_apply"),
                        rs.getString("required_documents"),
                        new DepartmentRef(rs.getString("dept_code"), rs.getString("dept_name"))))
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("制度が見つかりません。"));

        // 根拠は文書ごとに閲覧条件で絞り直す（制度が見えても、根拠の文書が見えるとは限らない）
        SqlPredicate docVisible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.VIEW);
        List<BasisView> basis = jdbc.sql("""
                        SELECT d.id AS document_id, d.code, d.title, s.id AS section_id, b.article_no, s.heading
                        FROM program_basis b JOIN documents d ON d.id = b.document_id
                        LEFT JOIN sections s ON s.id = b.section_id
                        WHERE b.program_id = :id AND """ + docVisible.sql() + " ORDER BY b.sort_order")
                .params(docVisible.params())
                .param("id", id)
                .query(ContentReadService::basisView)
                .list();

        List<ContactView> contacts = jdbc.sql("""
                        SELECT ct.code, ct.name, ct.description, dep.code AS dept_code, dep.name AS dept_name, ct.email,
                          ct.phone_ext, ct.chat_url, ct.office_hours, pc.role
                        FROM program_contacts pc JOIN contacts ct ON ct.id = pc.contact_id
                        JOIN departments dep ON dep.id = ct.department_id
                        WHERE pc.program_id = :id ORDER BY pc.sort_order
                        """)
                .param("id", id)
                .query(ContentReadService::contactView)
                .list();

        SqlPredicate faqVisible = accessPolicy.readPredicate(scope, "faqs", "f", Purpose.VIEW);
        List<FaqSummary> faqs = jdbc.sql(
                        "SELECT f.id, f.code, f.question, c.slug AS category_slug, c.name AS category_name"
                                + " FROM faqs f JOIN faq_links l ON l.faq_id = f.id AND l.target_type = 'PROGRAM' AND l.target_id = :id"
                                + " LEFT JOIN categories c ON c.id = f.category_id WHERE " + faqVisible.sql()
                                + " ORDER BY f.code")
                .params(faqVisible.params())
                .param("id", id)
                .query(ContentReadService::faqSummary)
                .list();

        List<LifeEventSummary> lifeEvents = jdbc.sql("""
                        SELECT e.code, e.name, e.icon, e.description FROM life_events e
                        JOIN life_event_programs lp ON lp.life_event_id = e.id WHERE lp.program_id = :id
                        ORDER BY e.sort_order
                        """)
                .param("id", id)
                .query(ContentReadService::lifeEventSummary)
                .list();

        return new ProgramDetail(
                row.summary(),
                row.kana(),
                row.eligibility(),
                row.howToApply(),
                row.docs(),
                row.owner(),
                basis,
                contacts,
                faqs,
                lifeEvents);
    }

    // ------------------------------------------------------------------ FAQ

    public List<FaqSummary> listFaqs(AccessScope scope, String categorySlug) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "faqs", "f", Purpose.VIEW);
        return jdbc.sql("SELECT f.id, f.code, f.question, c.slug AS category_slug, c.name AS category_name"
                        + " FROM faqs f LEFT JOIN categories c ON c.id = f.category_id WHERE " + visible.sql()
                        + " AND (CAST(:category AS text) IS NULL OR c.slug = :category OR c.parent_id = (SELECT id FROM categories WHERE slug = :category))"
                        + " ORDER BY f.code")
                .params(visible.params())
                .param("category", categorySlug)
                .query(ContentReadService::faqSummary)
                .list();
    }

    public FaqDetail getFaq(AccessScope scope, UUID id) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "faqs", "f", Purpose.VIEW);
        record Row(FaqSummary summary, String answer, UUID contactId) {}
        Row row = jdbc.sql("SELECT f.id, f.code, f.question, f.answer_markdown, f.contact_id, c.slug AS category_slug,"
                        + " c.name AS category_name FROM faqs f LEFT JOIN categories c ON c.id = f.category_id"
                        + " WHERE f.id = :id AND " + visible.sql())
                .params(visible.params())
                .param("id", id)
                .query((rs, i) -> new Row(
                        faqSummary(rs, i), rs.getString("answer_markdown"), rs.getObject("contact_id", UUID.class)))
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("FAQ が見つかりません。"));

        ContactView contact = row.contactId() == null
                ? null
                : jdbc.sql("""
                                SELECT ct.code, ct.name, ct.description, dep.code AS dept_code, dep.name AS dept_name, ct.email,
                                  ct.phone_ext, ct.chat_url, ct.office_hours, NULL AS role
                                FROM contacts ct JOIN departments dep ON dep.id = ct.department_id WHERE ct.id = :id
                                """)
                        .param("id", row.contactId())
                        .query(ContentReadService::contactView)
                        .optional()
                        .orElse(null);

        SqlPredicate programVisible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.VIEW);
        List<ProgramSummary> programs = jdbc.sql("SELECT " + PROGRAM_COLUMNS
                        + " FROM faq_links l JOIN programs p ON p.id = l.target_id LEFT JOIN categories c ON c.id = p.category_id"
                        + " WHERE l.faq_id = :id AND l.target_type = 'PROGRAM' AND " + programVisible.sql()
                        + " ORDER BY p.code")
                .params(programVisible.params())
                .param("id", id)
                .query(ContentReadService::programSummary)
                .list();

        SqlPredicate docVisible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.VIEW);
        List<BasisView> references = jdbc.sql("""
                        SELECT d.id AS document_id, d.code, d.title, s.id AS section_id, s.article_no, s.heading
                        FROM faq_links l
                        LEFT JOIN sections s ON l.target_type = 'SECTION' AND s.id = l.target_id
                        LEFT JOIN document_versions sv ON sv.id = s.document_version_id
                        JOIN documents d ON d.id = CASE WHEN l.target_type = 'SECTION' THEN sv.document_id ELSE l.target_id END
                        WHERE l.faq_id = :id AND l.target_type IN ('SECTION', 'DOCUMENT') AND """ + docVisible.sql() + " ORDER BY d.code, s.ordinal")
                .params(docVisible.params())
                .param("id", id)
                .query(ContentReadService::basisView)
                .list();

        return new FaqDetail(row.summary(), row.answer(), contact, programs, references);
    }

    // ------------------------------------------------------------------ きっかけ・カテゴリ

    public List<LifeEventSummary> listLifeEvents() {
        return jdbc.sql("SELECT code, name, icon, description FROM life_events ORDER BY sort_order")
                .query(ContentReadService::lifeEventSummary)
                .list();
    }

    public LifeEventDetail getLifeEvent(AccessScope scope, String code) {
        LifeEventSummary event = jdbc.sql("SELECT code, name, icon, description FROM life_events WHERE code = :code")
                .param("code", code)
                .query(ContentReadService::lifeEventSummary)
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("きっかけが見つかりません: " + code));
        SqlPredicate visible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.VIEW);
        List<LifeEventProgram> programs = jdbc.sql("SELECT " + PROGRAM_COLUMNS + ", lp.relation FROM life_events e"
                        + " JOIN life_event_programs lp ON lp.life_event_id = e.id JOIN programs p ON p.id = lp.program_id"
                        + " LEFT JOIN categories c ON c.id = p.category_id WHERE e.code = :code AND " + visible.sql()
                        + " ORDER BY lp.relation, lp.sort_order")
                .params(visible.params())
                .param("code", code)
                .query((rs, i) -> new LifeEventProgram(programSummary(rs, i), rs.getString("relation")))
                .list();
        return new LifeEventDetail(event, programs);
    }

    public List<CategoryNode> categoryTree() {
        record Row(UUID id, UUID parentId, String slug, String name) {}
        var rows = jdbc.sql("SELECT id, parent_id, slug, name FROM categories ORDER BY sort_order, slug")
                .query((rs, i) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("parent_id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name")))
                .list();
        Map<UUID, List<CategoryNode>> children = new HashMap<>();
        for (Row r : rows) {
            if (r.parentId() != null) {
                children.computeIfAbsent(r.parentId(), k -> new ArrayList<>())
                        .add(new CategoryNode(r.slug(), r.name(), List.of()));
            }
        }
        Map<UUID, CategoryNode> roots = new LinkedHashMap<>();
        for (Row r : rows) {
            if (r.parentId() == null) {
                roots.put(r.id(), new CategoryNode(r.slug(), r.name(), children.getOrDefault(r.id(), List.of())));
            }
        }
        return List.copyOf(roots.values());
    }

    // ------------------------------------------------------------------ 行の変換

    private static DocumentSummary documentSummary(ResultSet rs, int i) throws SQLException {
        return new DocumentSummary(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("document_type"),
                new CategoryRef(rs.getString("category_slug"), rs.getString("category_name")),
                new DepartmentRef(rs.getString("dept_code"), rs.getString("dept_name")),
                rs.getString("version_label"),
                rs.getObject("effective_from", LocalDate.class),
                List.of((String[]) rs.getArray("tags").getArray()));
    }

    private static SectionView sectionView(ResultSet rs, int i) throws SQLException {
        return new SectionView(
                rs.getObject("id", UUID.class),
                rs.getInt("ordinal"),
                rs.getString("path"),
                rs.getString("article_no"),
                rs.getString("heading"),
                rs.getString("content"));
    }

    private static ProgramSummary programSummary(ResultSet rs, int i) throws SQLException {
        String slug = rs.getString("category_slug");
        return new ProgramSummary(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("summary"),
                rs.getString("benefit"),
                rs.getString("deadline_note"),
                slug == null ? null : new CategoryRef(slug, rs.getString("category_name")));
    }

    private static FaqSummary faqSummary(ResultSet rs, int i) throws SQLException {
        String slug = rs.getString("category_slug");
        return new FaqSummary(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("question"),
                slug == null ? null : new CategoryRef(slug, rs.getString("category_name")));
    }

    private static BasisView basisView(ResultSet rs, int i) throws SQLException {
        return new BasisView(
                rs.getObject("document_id", UUID.class),
                rs.getString("code"),
                rs.getString("title"),
                rs.getObject("section_id", UUID.class),
                rs.getString("article_no"),
                rs.getString("heading"));
    }

    private static ContactView contactView(ResultSet rs, int i) throws SQLException {
        return new ContactView(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                new DepartmentRef(rs.getString("dept_code"), rs.getString("dept_name")),
                rs.getString("email"),
                rs.getString("phone_ext"),
                rs.getString("chat_url"),
                rs.getString("office_hours"),
                rs.getString("role"));
    }

    private static LifeEventSummary lifeEventSummary(ResultSet rs, int i) throws SQLException {
        return new LifeEventSummary(
                rs.getString("code"), rs.getString("name"), rs.getString("icon"), rs.getString("description"));
    }
}
