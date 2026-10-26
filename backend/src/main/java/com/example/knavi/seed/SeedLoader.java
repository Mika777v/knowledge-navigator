package com.example.knavi.seed;

import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.DocumentImport.ImportedDocument;
import com.example.knavi.shared.text.TextNormalizer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

/**
 * data/seed を DB に取り込む。コード（code / slug / email）で既存の行を特定して上書きするため、何度実行してもよい。
 *
 * <p>ナビゲーションキーワード（navigation_keywords）は投入のたびに作り直す。類義語辞書は代表の語でグループを特定して上書きする
 * （管理画面で追加したグループは残す）。
 */
@Service
class SeedLoader {

    /** 公開範囲の広さ（小さいほど広い）。制度・FAQ は根拠文書より広くしてはいけない */
    private static final Map<String, Integer> VISIBILITY_RANK =
            Map.of("INTERNAL", 0, "MANAGERS", 1, "RESTRICTED", 2, "CONFIDENTIAL", 3);

    private static final String DEFAULT_CONTACT = "HR-REP";

    private final JdbcClient jdbc;
    private final DocumentImport documentImport;
    private final PasswordEncoder passwordEncoder;

    SeedLoader(JdbcClient jdbc, DocumentImport documentImport, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.documentImport = documentImport;
        this.passwordEncoder = passwordEncoder;
    }

    record Summary(
            int departments,
            int contacts,
            int categories,
            int users,
            int documents,
            int programs,
            int lifeEvents,
            int faqs,
            int keywords,
            int synonymGroups) {}

    @Transactional
    public Summary load(Path dir, String demoUserPassword) {
        int departments = loadDepartments(list(dir, "departments.yaml"));
        int contacts = loadContacts(list(dir, "contacts.yaml"));
        int categories = loadCategories(list(dir, "categories.yaml"));
        int users = loadUsers(list(dir, "users.yaml"), passwordEncoder.encode(demoUserPassword));
        loadTags(list(dir, "tags.yaml"));
        int documents = loadDocuments(dir.resolve("documents"));
        List<Map<String, Object>> programs = list(dir, "programs.yaml");
        loadPrograms(programs);
        List<Map<String, Object>> lifeEvents = list(dir, "life_events.yaml");
        loadLifeEvents(lifeEvents);
        int faqs = loadFaqs(list(dir, "faqs.yaml"));
        int keywords = rebuildKeywords(programs, lifeEvents, list(dir, "contacts.yaml"));
        int synonymGroups = loadSynonyms(list(dir, "synonyms.yaml"));
        jdbc.sql("""
                        INSERT INTO app_settings (key, value) VALUES ('navigation.default_contact_code', :code)
                        ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()
                        """).param("code", DEFAULT_CONTACT).update();
        return new Summary(
                departments,
                contacts,
                categories,
                users,
                documents,
                programs.size(),
                lifeEvents.size(),
                faqs,
                keywords,
                synonymGroups);
    }

    // ------------------------------------------------------------------ 組織

    private int loadDepartments(List<Map<String, Object>> rows) {
        for (var d : rows) {
            jdbc.sql("""
                            INSERT INTO departments (code, name, description, sort_order)
                            VALUES (:code, :name, :description, :sort)
                            ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
                              sort_order = EXCLUDED.sort_order, row_version = departments.row_version + 1, updated_at = now()
                            """)
                    .param("code", str(d, "code"))
                    .param("name", str(d, "name"))
                    .param("description", str(d, "description"))
                    .param("sort", intValue(d, "sort_order"))
                    .update();
        }
        // 親部署はすべての行を作ってから設定する
        for (var d : rows) {
            jdbc.sql("""
                            UPDATE departments SET parent_id = (SELECT id FROM departments WHERE code = :parent)
                            WHERE code = :code
                            """)
                    .param("parent", str(d, "parent"))
                    .param("code", str(d, "code"))
                    .update();
        }
        return rows.size();
    }

    private int loadContacts(List<Map<String, Object>> rows) {
        // 代表窓口の入れ替えで一意制約に触れないよう、先にすべて外す
        jdbc.sql("UPDATE contacts SET is_primary = false").update();
        for (var c : rows) {
            jdbc.sql("""
                            INSERT INTO contacts (code, department_id, name, description, email, phone_ext, chat_url,
                              office_hours, is_primary)
                            VALUES (:code, :dept, :name, :description, :email, :phone, :chat, :hours, :primary)
                            ON CONFLICT (code) DO UPDATE SET department_id = EXCLUDED.department_id,
                              name = EXCLUDED.name, description = EXCLUDED.description, email = EXCLUDED.email,
                              phone_ext = EXCLUDED.phone_ext, chat_url = EXCLUDED.chat_url,
                              office_hours = EXCLUDED.office_hours, is_primary = EXCLUDED.is_primary,
                              row_version = contacts.row_version + 1, updated_at = now()
                            """)
                    .param("code", str(c, "code"))
                    .param("dept", departmentId(str(c, "department")))
                    .param("name", str(c, "name"))
                    .param("description", str(c, "description"))
                    .param("email", str(c, "email"))
                    .param("phone", str(c, "phone_ext"))
                    .param("chat", str(c, "chat_url"))
                    .param("hours", str(c, "office_hours"))
                    .param("primary", Boolean.TRUE.equals(c.get("is_primary")))
                    .update();
        }
        return rows.size();
    }

    private int loadCategories(List<Map<String, Object>> rows) {
        for (var c : rows) {
            jdbc.sql("""
                            INSERT INTO categories (slug, name, sort_order) VALUES (:slug, :name, :sort)
                            ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order
                            """)
                    .param("slug", str(c, "slug"))
                    .param("name", str(c, "name"))
                    .param("sort", intValue(c, "sort_order"))
                    .update();
        }
        for (var c : rows) {
            jdbc.sql(
                            "UPDATE categories SET parent_id = (SELECT id FROM categories WHERE slug = :parent) WHERE slug = :slug")
                    .param("parent", str(c, "parent"))
                    .param("slug", str(c, "slug"))
                    .update();
        }
        return rows.size();
    }

    /** タグの語彙（種類つき）。文書の front matter の tags はこの名前を使う */
    private void loadTags(List<Map<String, Object>> rows) {
        for (var t : rows) {
            jdbc.sql("""
                            INSERT INTO tags (name, tag_type) VALUES (:name, :type)
                            ON CONFLICT (name) DO UPDATE SET tag_type = EXCLUDED.tag_type
                            """)
                    .param("name", str(t, "name"))
                    .param("type", str(t, "type"))
                    .update();
        }
    }

    private int loadUsers(List<Map<String, Object>> rows, String passwordHash) {
        for (var u : rows) {
            jdbc.sql("""
                            INSERT INTO users (email, display_name, department_id, role, employment_type, joined_on,
                              password_hash)
                            VALUES (:email, :name, :dept, :role, :type, :joined, :hash)
                            ON CONFLICT (email) DO UPDATE SET display_name = EXCLUDED.display_name,
                              department_id = EXCLUDED.department_id, role = EXCLUDED.role,
                              employment_type = EXCLUDED.employment_type, joined_on = EXCLUDED.joined_on,
                              password_hash = EXCLUDED.password_hash, is_active = true,
                              row_version = users.row_version + 1, updated_at = now()
                            """)
                    .param("email", str(u, "email"))
                    .param("name", str(u, "display_name"))
                    .param("dept", departmentId(str(u, "department")))
                    .param("role", str(u, "role"))
                    .param("type", str(u, "employment_type"))
                    .param("joined", date(u.get("joined_on")))
                    .param("hash", passwordHash)
                    .update();
        }
        return rows.size();
    }

    // ------------------------------------------------------------------ 文書

    private int loadDocuments(Path dir) {
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path file : files) {
            String markdown = read(file);
            Map<String, Object> meta = frontMatter(markdown, file);
            documentImport.importPublished(new ImportedDocument(
                    str(meta, "code"),
                    str(meta, "title"),
                    str(meta, "summary"),
                    str(meta, "document_type"),
                    str(meta, "category"),
                    departmentId(str(meta, "owner_department")),
                    str(meta, "visibility"),
                    str(meta, "version_label"),
                    date(meta.get("effective_from")),
                    // 本文には front matter（文書の属性）を含めない。管理画面で新しい版を作るときの初期値になるため
                    withoutFrontMatter(markdown)));
            documentImport.replaceTags(str(meta, "code"), strings(meta.get("tags")));
        }
        return files.size();
    }

    // ------------------------------------------------------------------ 制度・きっかけ・FAQ

    private void loadPrograms(List<Map<String, Object>> rows) {
        for (var p : rows) {
            String code = str(p, "code");
            String visibility = str(p, "visibility");
            // 管理 API（Program.update）と同じく制度の説明だけから作る。keywords は navigation_keywords で照合する
            String searchText = TextNormalizer.normalize(String.join(
                    " ",
                    Stream.of("name", "name_kana", "summary", "eligibility", "benefit", "how_to_apply")
                            .map(k -> str(p, k))
                            .filter(Objects::nonNull)
                            .toList()));
            UUID id = jdbc.sql("""
                            INSERT INTO programs (code, name, name_kana, summary, eligibility, benefit, how_to_apply,
                              deadline_note, required_documents, category_id, owner_department_id, visibility, status,
                              search_text)
                            VALUES (:code, :name, :kana, :summary, :eligibility, :benefit, :how, :deadline, :docs,
                              (SELECT id FROM categories WHERE slug = :category), :dept, :visibility, 'PUBLISHED',
                              :search)
                            ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, name_kana = EXCLUDED.name_kana,
                              summary = EXCLUDED.summary, eligibility = EXCLUDED.eligibility, benefit = EXCLUDED.benefit,
                              how_to_apply = EXCLUDED.how_to_apply, deadline_note = EXCLUDED.deadline_note,
                              required_documents = EXCLUDED.required_documents, category_id = EXCLUDED.category_id,
                              owner_department_id = EXCLUDED.owner_department_id, visibility = EXCLUDED.visibility,
                              status = 'PUBLISHED', search_text = EXCLUDED.search_text, updated_at = now()
                            RETURNING id
                            """)
                    .param("code", code)
                    .param("name", str(p, "name"))
                    .param("kana", str(p, "name_kana"))
                    .param("summary", str(p, "summary"))
                    .param("eligibility", str(p, "eligibility"))
                    .param("benefit", str(p, "benefit"))
                    .param("how", str(p, "how_to_apply"))
                    .param("deadline", str(p, "deadline_note"))
                    .param("docs", str(p, "required_documents"))
                    .param("category", str(p, "category"))
                    .param("dept", departmentId(str(p, "owner_department")))
                    .param("visibility", visibility)
                    .param("search", searchText)
                    .query(UUID.class)
                    .single();

            jdbc.sql("DELETE FROM program_basis WHERE program_id = :id")
                    .param("id", id)
                    .update();
            int order = 0;
            for (var basis : maps(p.get("basis"))) {
                DocumentRef doc = document(str(basis, "document"));
                checkNotWider(code, visibility, doc);
                String article = str(basis, "article");
                jdbc.sql("""
                                INSERT INTO program_basis (program_id, document_id, section_id, article_no, sort_order)
                                VALUES (:program, :document, :section, :article, :order)
                                """)
                        .param("program", id)
                        .param("document", doc.id())
                        .param("section", article == null ? null : section(doc, article))
                        .param("article", article)
                        .param("order", order++)
                        .update();
            }

            if (maps(p.get("contacts")).isEmpty()) {
                throw new IllegalStateException(code + ": 公開する制度には窓口が1件以上必要です");
            }
            jdbc.sql("DELETE FROM program_contacts WHERE program_id = :id")
                    .param("id", id)
                    .update();
            order = 0;
            for (var contact : maps(p.get("contacts"))) {
                jdbc.sql("""
                                INSERT INTO program_contacts (program_id, contact_id, role, sort_order)
                                VALUES (:program, :contact, :role, :order)
                                """)
                        .param("program", id)
                        .param("contact", contactId(str(contact, "contact")))
                        .param("role", str(contact, "role"))
                        .param("order", order++)
                        .update();
            }
        }
    }

    private void loadLifeEvents(List<Map<String, Object>> rows) {
        int sort = 0;
        for (var e : rows) {
            UUID id = jdbc.sql("""
                            INSERT INTO life_events (code, name, icon, description, sort_order)
                            VALUES (:code, :name, :icon, :description, :sort)
                            ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, icon = EXCLUDED.icon,
                              description = EXCLUDED.description, sort_order = EXCLUDED.sort_order,
                              row_version = life_events.row_version + 1, updated_at = now()
                            RETURNING id
                            """)
                    .param("code", str(e, "code"))
                    .param("name", str(e, "name"))
                    .param("icon", str(e, "icon"))
                    .param("description", str(e, "description"))
                    .param("sort", sort += 10)
                    .query(UUID.class)
                    .single();
            jdbc.sql("DELETE FROM life_event_programs WHERE life_event_id = :id")
                    .param("id", id)
                    .update();
            int order = 0;
            for (var link : maps(e.get("programs"))) {
                jdbc.sql("""
                                INSERT INTO life_event_programs (life_event_id, program_id, relation, sort_order)
                                VALUES (:event, :program, :relation, :order)
                                """)
                        .param("event", id)
                        .param("program", programId(str(link, "program")))
                        .param("relation", str(link, "relation"))
                        .param("order", order++)
                        .update();
            }
        }
    }

    private int loadFaqs(List<Map<String, Object>> rows) {
        for (var f : rows) {
            String code = str(f, "code");
            String visibility = Objects.requireNonNullElse(str(f, "visibility"), "INTERNAL");
            UUID id = jdbc.sql("""
                            INSERT INTO faqs (code, question, answer_markdown, category_id, contact_id,
                              owner_department_id, visibility, status, search_text)
                            VALUES (:code, :question, :answer, (SELECT id FROM categories WHERE slug = :category),
                              :contact, :dept, :visibility, 'PUBLISHED', :search)
                            ON CONFLICT (code) DO UPDATE SET question = EXCLUDED.question,
                              answer_markdown = EXCLUDED.answer_markdown, category_id = EXCLUDED.category_id,
                              contact_id = EXCLUDED.contact_id, owner_department_id = EXCLUDED.owner_department_id,
                              visibility = EXCLUDED.visibility, status = 'PUBLISHED',
                              search_text = EXCLUDED.search_text, updated_at = now()
                            RETURNING id
                            """)
                    .param("code", code)
                    .param("question", str(f, "question"))
                    .param("answer", str(f, "answer"))
                    .param("category", str(f, "category"))
                    .param("contact", str(f, "contact") == null ? null : contactId(str(f, "contact")))
                    .param("dept", departmentId(str(f, "owner_department")))
                    .param("visibility", visibility)
                    .param("search", TextNormalizer.normalize(str(f, "question") + " " + str(f, "answer")))
                    .query(UUID.class)
                    .single();

            jdbc.sql("DELETE FROM faq_links WHERE faq_id = :id").param("id", id).update();
            for (var link : maps(f.get("links"))) {
                String type;
                UUID target;
                if (link.containsKey("program")) {
                    type = "PROGRAM";
                    target = programId(str(link, "program"));
                } else {
                    DocumentRef doc = document(str(link, "document"));
                    checkNotWider(code, visibility, doc);
                    String article = str(link, "article");
                    type = article == null ? "DOCUMENT" : "SECTION";
                    target = article == null ? doc.id() : section(doc, article);
                }
                jdbc.sql("""
                                INSERT INTO faq_links (faq_id, target_type, target_id) VALUES (:faq, :type, :target)
                                ON CONFLICT DO NOTHING
                                """)
                        .param("faq", id)
                        .param("type", type)
                        .param("target", target)
                        .update();
            }
        }
        return rows.size();
    }

    // ------------------------------------------------------------------ ナビゲーション辞書

    private int rebuildKeywords(
            List<Map<String, Object>> programs,
            List<Map<String, Object>> lifeEvents,
            List<Map<String, Object>> contacts) {
        jdbc.sql("DELETE FROM navigation_keywords").update();
        List<Object[]> entries = new ArrayList<>();
        // 名前は言い換えより強く効かせる
        for (var p : programs) {
            UUID id = programId(str(p, "code"));
            entries.add(new Object[] {str(p, "name"), "PROGRAM", id, 2.0f});
            strings(p.get("keywords")).forEach(k -> entries.add(new Object[] {k, "PROGRAM", id, 1.0f}));
        }
        for (var e : lifeEvents) {
            UUID id = jdbc.sql("SELECT id FROM life_events WHERE code = :code")
                    .param("code", str(e, "code"))
                    .query(UUID.class)
                    .single();
            entries.add(new Object[] {str(e, "name"), "LIFE_EVENT", id, 2.0f});
            strings(e.get("keywords")).forEach(k -> entries.add(new Object[] {k, "LIFE_EVENT", id, 1.5f}));
        }
        for (var c : contacts) {
            UUID id = contactId(str(c, "code"));
            strings(c.get("topics")).forEach(t -> entries.add(new Object[] {t, "CONTACT", id, 1.0f}));
        }
        int count = 0;
        for (Object[] e : entries) {
            count += jdbc.sql("""
                            INSERT INTO navigation_keywords (term, normalized_term, target_type, target_id, weight)
                            VALUES (:term, :normalized, :type, :target, :weight)
                            ON CONFLICT (normalized_term, target_type, target_id) DO NOTHING
                            """)
                    .param("term", e[0])
                    .param("normalized", TextNormalizer.normalize((String) e[0]))
                    .param("type", e[1])
                    .param("target", e[2])
                    .param("weight", e[3])
                    .update();
        }
        return count;
    }

    /**
     * 類義語のグループを、代表の語（正規化後）を含むグループに上書きする。無ければ作る。
     * 語がほかのグループに入っていれば失敗にする（1つの語は1つのグループにだけ入る）
     */
    private int loadSynonyms(List<Map<String, Object>> groups) {
        for (var g : groups) {
            String canonical = str(g, "canonical").strip();
            List<String> terms = new ArrayList<>();
            terms.add(canonical);
            terms.addAll(strings(g.get("terms")));
            UUID id = synonymGroupOf(TextNormalizer.normalize(canonical))
                    .orElseGet(() -> jdbc.sql("INSERT INTO synonym_groups (canonical) VALUES (:c) RETURNING id")
                            .param("c", canonical)
                            .query(UUID.class)
                            .single());
            jdbc.sql("""
                            UPDATE synonym_groups SET canonical = :c, row_version = row_version + 1, updated_at = now()
                            WHERE id = :id
                            """).param("c", canonical).param("id", id).update();
            jdbc.sql("DELETE FROM synonym_terms WHERE group_id = :id")
                    .param("id", id)
                    .update();
            for (String term : terms) {
                String normalized = TextNormalizer.normalize(term);
                var owner = synonymGroupOf(normalized);
                if (owner.isPresent() && !owner.get().equals(id)) {
                    throw new IllegalStateException("synonyms: 「" + term + "」がほかのグループに入っています（" + canonical + "）");
                }
                if (owner.isEmpty()) {
                    jdbc.sql("INSERT INTO synonym_terms (group_id, term, normalized_term) VALUES (:g, :t, :n)")
                            .param("g", id)
                            .param("t", term.strip())
                            .param("n", normalized)
                            .update();
                }
            }
        }
        return groups.size();
    }

    private java.util.Optional<UUID> synonymGroupOf(String normalizedTerm) {
        return jdbc.sql("SELECT group_id FROM synonym_terms WHERE normalized_term = :n")
                .param("n", normalizedTerm)
                .query(UUID.class)
                .optional();
    }

    // ------------------------------------------------------------------ 参照の解決

    private record DocumentRef(UUID id, String code, String visibility, UUID currentVersionId) {}

    private DocumentRef document(String code) {
        return jdbc.sql("SELECT id, code, visibility, current_version_id FROM documents WHERE code = :code")
                .param("code", code)
                .query((rs, i) -> new DocumentRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("visibility"),
                        rs.getObject("current_version_id", UUID.class)))
                .optional()
                .orElseThrow(() -> new IllegalStateException("document not found: " + code));
    }

    private UUID section(DocumentRef doc, String articleNo) {
        return jdbc.sql("SELECT id FROM sections WHERE document_version_id = :version AND article_no = :article")
                .param("version", doc.currentVersionId())
                .param("article", articleNo)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException(doc.code() + " has no " + articleNo));
    }

    private static void checkNotWider(String code, String visibility, DocumentRef doc) {
        if (VISIBILITY_RANK.get(visibility) < VISIBILITY_RANK.get(doc.visibility())) {
            throw new IllegalStateException(
                    code + ": 公開範囲 " + visibility + " が根拠文書 " + doc.code() + " の " + doc.visibility() + " より広くなっています");
        }
    }

    private UUID departmentId(String code) {
        return idByCode("departments", "code", code);
    }

    private UUID contactId(String code) {
        return idByCode("contacts", "code", code);
    }

    private UUID programId(String code) {
        return idByCode("programs", "code", code);
    }

    private UUID idByCode(String table, String column, String value) {
        if (value == null) {
            return null;
        }
        // table / column はこのクラス内の定数のみ（利用者の入力ではない）
        return jdbc.sql("SELECT id FROM " + table + " WHERE " + column + " = :value")
                .param("value", value)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException(table + " not found: " + value));
    }

    // ------------------------------------------------------------------ YAML

    private static List<Map<String, Object>> list(Path dir, String file) {
        List<Map<String, Object>> rows = new Yaml().load(read(dir.resolve(file)));
        return rows == null ? List.of() : rows;
    }

    private static String withoutFrontMatter(String markdown) {
        return markdown.split("(?m)^---\\s*$", 3)[2].stripLeading();
    }

    private static Map<String, Object> frontMatter(String markdown, Path file) {
        if (!markdown.startsWith("---")) {
            throw new IllegalStateException("front matter がありません: " + file.getFileName());
        }
        String yaml = markdown.split("(?m)^---\\s*$", 3)[1];
        return new Yaml().load(yaml);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static int intValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.intValue() : 0;
    }

    /** YAML の日付は java.util.Date として読まれるため LocalDate に変換する */
    private static LocalDate date(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date d) {
            return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    private static List<String> strings(Object value) {
        return value == null
                ? List.of()
                : ((Collection<?>) value).stream().map(Object::toString).collect(Collectors.toList());
    }
}
