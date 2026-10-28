package com.example.knavi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.DocumentImport.ImportedDocument;
import com.example.knavi.intelligence.AnswerGenerator;
import com.example.knavi.intelligence.AnswerGenerator.GeneratedAnswer;
import com.example.knavi.intelligence.AnswerGenerator.Passage;
import com.example.knavi.navigation.application.AnswerService;
import com.example.knavi.navigation.application.AnswerService.AnswerSink;
import com.example.knavi.navigation.application.AssistantAnswer;
import com.example.knavi.navigation.application.AssistantUnanswerable;
import com.example.knavi.navigation.application.NavigationResult;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ロール × 公開範囲 × 経路（一覧・詳細・関連・検索）の権限マトリクス（#38、ADR-0004）。
 *
 * <p>文書・制度・FAQ を公開範囲ごとに1件ずつ用意し（すべて総務部の所管）、利用者ごとに次を確認する。
 *
 * <ul>
 *   <li>一覧・詳細（VIEW）: 見えるものだけが一覧に出て、見えないものの詳細は 404
 *   <li>関連（VIEW）: 制度の根拠・FAQ・きっかけの制度など、別の資源を経由しても見えないものは出ない
 *   <li>検索（SEARCH）: 見えるもの（CONFIDENTIAL を除く）だけが出る。見えないものだけに含まれる語で検索しても、
 *       件数・抜粋・窓口・関連制度のどこにも痕跡が出ない（該当なしと同じ応答になる）
 *   <li>回答の文脈（E14-T4・T7）: 生成のプロバイダに渡す条も、検索と同じく見えるものだけ。社外に送るプロバイダには、
 *       さらに送ってよい公開範囲だけを渡す
 * </ul>
 *
 * <p>RESTRICTED は人事部に、CONFIDENTIAL はユーザー「機密閲覧者」に access_rules で許可する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@TestPropertySource(properties = "knavi.assistant.enabled=true")
class PermissionMatrixTests {

    static final List<String> VISIBILITIES = List.of("INTERNAL", "MANAGERS", "RESTRICTED", "CONFIDENTIAL");

    /** 公開範囲ごとの、その資源だけに含まれる語（検索で存在が漏れないかを確かめる） */
    static final Map<String, String> SECRET_WORDS =
            Map.of("INTERNAL", "アルファ", "MANAGERS", "ブラボー", "RESTRICTED", "チャーリー", "CONFIDENTIAL", "デルタ");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DocumentImport documentImport;

    @Autowired
    AnswerService answerService;

    /** 生成は差し替え、渡された条を記録する */
    @MockitoBean
    AnswerGenerator generator;

    UUID ga;
    UUID hr;
    Map<String, KnaviUserDetails> users;
    /** 公開範囲 → ID */
    Map<String, UUID> documents = new LinkedHashMap<>();

    Map<String, UUID> programs = new LinkedHashMap<>();
    Map<String, UUID> faqs = new LinkedHashMap<>();
    UUID hubProgram;

    @BeforeEach
    void setUp() {
        ga = insertId("INSERT INTO departments (code, name) VALUES ('GA', '総務部') RETURNING id");
        hr = insertId("INSERT INTO departments (code, name) VALUES ('HR', '人事部') RETURNING id");
        jdbc.sql("INSERT INTO categories (slug, name) VALUES ('general', '総務')").update();
        UUID generalContact = insertId("""
                INSERT INTO contacts (code, name, department_id, phone_ext) VALUES ('GENERAL', '総合窓口', '%s', '1000') RETURNING id
                """.formatted(hr));
        jdbc.sql("INSERT INTO app_settings (key, value) VALUES ('navigation.default_contact_code', 'GENERAL')")
                .update();

        users = Map.of(
                "社員（総務部）", insertUser(ga, "employee@example.com", "EMPLOYEE"),
                "管理職（総務部）", insertUser(ga, "manager@example.com", "MANAGER"),
                "社員（人事部）", insertUser(hr, "hr-employee@example.com", "EMPLOYEE"),
                "所管部署の担当者", insertUser(ga, "ga-editor@example.com", "CONTENT_EDITOR"),
                "他部署の担当者", insertUser(hr, "hr-editor@example.com", "CONTENT_EDITOR"),
                "システム管理者", insertUser(ga, "admin@example.com", "SYSTEM_ADMIN"),
                "機密閲覧者", insertUser(ga, "confidential@example.com", "EMPLOYEE"));

        for (String v : VISIBILITIES) {
            String secret = SECRET_WORDS.get(v);
            documents.put(v, importDocument("DOC-" + v, v, "マトリクス " + secret + " の規程。"));
            programs.put(v, insertProgram("PRG-" + v, v, "マトリクス制度 " + secret));
            faqs.put(v, insertFaq("FAQ-" + v, v, "マトリクス " + secret + " について？"));
            for (String type : List.of("DOCUMENT", "PROGRAM", "FAQ")) {
                UUID id = switch (type) {
                    case "DOCUMENT" -> documents.get(v);
                    case "PROGRAM" -> programs.get(v);
                    default -> faqs.get(v);
                };
                if (v.equals("RESTRICTED")) {
                    accessRule(type, id, "DEPARTMENT", hr.toString());
                } else if (v.equals("CONFIDENTIAL")) {
                    accessRule(type, id, "USER", users.get("機密閲覧者").id().toString());
                }
            }
        }

        // 社内公開の制度が、すべての公開範囲の文書・FAQ・制度とつながっている（関連を経由した漏れを確かめる）
        hubProgram = insertProgram("PRG-HUB", "INTERNAL", "ハブ制度");
        UUID lifeEvent = insertId("INSERT INTO life_events (code, name) VALUES ('HUB', 'ハブ') RETURNING id");
        for (String v : VISIBILITIES) {
            jdbc.sql("INSERT INTO program_basis (program_id, document_id, article_no) VALUES (:p, :d, '第1条')")
                    .param("p", hubProgram)
                    .param("d", documents.get(v))
                    .update();
            jdbc.sql("INSERT INTO faq_links (faq_id, target_type, target_id) VALUES (:f, 'PROGRAM', :p)")
                    .param("f", faqs.get(v))
                    .param("p", hubProgram)
                    .update();
            jdbc.sql("INSERT INTO life_event_programs (life_event_id, program_id, relation) VALUES (:e, :p, 'RELATED')")
                    .param("e", lifeEvent)
                    .param("p", programs.get(v))
                    .update();
            // 見えない制度の窓口が、検索結果の窓口に出ないことも確かめる
            UUID contact = insertId(
                    "INSERT INTO contacts (code, name, department_id, phone_ext) VALUES ('CT-%s', '窓口 %s', '%s', '2000') RETURNING id"
                            .formatted(v, SECRET_WORDS.get(v), ga));
            jdbc.sql("INSERT INTO program_contacts (program_id, contact_id) VALUES (:p, :c)")
                    .param("p", programs.get(v))
                    .param("c", contact)
                    .update();
        }
        jdbc.sql("INSERT INTO life_event_programs (life_event_id, program_id, relation) VALUES (:e, :p, 'PRIMARY')")
                .param("e", lifeEvent)
                .param("p", hubProgram)
                .update();
        jdbc.sql("""
                        INSERT INTO navigation_keywords (term, normalized_term, target_type, target_id)
                        VALUES ('ハブ', 'ハブ', 'LIFE_EVENT', :e)
                        """).param("e", lifeEvent).update();
        assertThat(generalContact).isNotNull();
    }

    /** 利用者ごとに、閲覧（VIEW）できる公開範囲 */
    static Stream<Arguments> matrix() {
        return Stream.of(
                Arguments.of("社員（総務部）", List.of("INTERNAL")),
                Arguments.of("管理職（総務部）", List.of("INTERNAL", "MANAGERS")),
                Arguments.of("社員（人事部）", List.of("INTERNAL", "RESTRICTED")),
                Arguments.of("所管部署の担当者", VISIBILITIES),
                Arguments.of("他部署の担当者", List.of("INTERNAL", "RESTRICTED")),
                // システム管理者でもコンテンツの閲覧権限は広がらない（職務分離）
                Arguments.of("システム管理者", List.of("INTERNAL")),
                Arguments.of("機密閲覧者", List.of("INTERNAL", "CONFIDENTIAL")));
    }

    @ParameterizedTest(name = "{0}: 一覧に出るのは {1} だけ")
    @MethodSource("matrix")
    void listsShowOnlyViewableItems(String who, List<String> viewable) throws Exception {
        assertThat(codes(getJson("/api/v1/documents", who), "$[*].code"))
                .containsExactlyInAnyOrderElementsOf(prefixed("DOC-", viewable));
        assertThat(codes(getJson("/api/v1/programs", who), "$[*].code"))
                .containsExactlyInAnyOrderElementsOf(withHub(prefixed("PRG-", viewable)));
        assertThat(codes(getJson("/api/v1/faqs", who), "$[*].code"))
                .containsExactlyInAnyOrderElementsOf(prefixed("FAQ-", viewable));
    }

    @ParameterizedTest(name = "{0}: {1} 以外の詳細は 404")
    @MethodSource("matrix")
    void detailsOfOtherItemsAreNotFound(String who, List<String> viewable) throws Exception {
        for (String v : VISIBILITIES) {
            var expected = viewable.contains(v) ? status().isOk() : status().isNotFound();
            for (String path : List.of(
                    "/api/v1/documents/" + documents.get(v),
                    "/api/v1/programs/" + programs.get(v),
                    "/api/v1/faqs/" + faqs.get(v))) {
                mockMvc.perform(get(path).with(user(users.get(who)))).andExpect(expected);
            }
            UUID section =
                    jdbc.sql("""
                            SELECT s.id FROM sections s JOIN documents d ON d.current_version_id = s.document_version_id
                            WHERE d.id = :d ORDER BY s.ordinal LIMIT 1
                            """).param("d", documents.get(v)).query(UUID.class).single();
            mockMvc.perform(get("/api/v1/sections/" + section).with(user(users.get(who))))
                    .andExpect(expected);
        }
    }

    @ParameterizedTest(name = "{0}: 関連をたどっても {1} 以外は出ない")
    @MethodSource("matrix")
    void relationsShowOnlyViewableItems(String who, List<String> viewable) throws Exception {
        String hub = getJson("/api/v1/programs/" + hubProgram, who);
        assertThat(codes(hub, "$.basis[*].documentCode"))
                .containsExactlyInAnyOrderElementsOf(prefixed("DOC-", viewable));
        assertThat(codes(hub, "$.faqs[*].code")).containsExactlyInAnyOrderElementsOf(prefixed("FAQ-", viewable));

        String lifeEvent = getJson("/api/v1/life-events/HUB", who);
        assertThat(codes(lifeEvent, "$.programs[*].program.code"))
                .containsExactlyInAnyOrderElementsOf(withHub(prefixed("PRG-", viewable)));

        String document = getJson("/api/v1/documents/" + documents.get("INTERNAL"), who);
        assertThat(codes(document, "$.programs[*].code")).containsExactly("PRG-HUB");

        for (String v : viewable) {
            String faq = getJson("/api/v1/faqs/" + faqs.get(v), who);
            assertThat(codes(faq, "$.programs[*].code")).containsExactly("PRG-HUB");
        }
        assertNoTraceOfHidden(hub + lifeEvent + document, viewable);
    }

    @ParameterizedTest(name = "{0}: 検索に出るのは {1} から機密を除いたものだけ")
    @MethodSource("matrix")
    void searchShowsOnlySearchableItems(String who, List<String> viewable) throws Exception {
        List<String> searchable =
                viewable.stream().filter(v -> !v.equals("CONFIDENTIAL")).toList();

        String body = navigate("マトリクス", who);
        assertThat(codes(body, "$.programs[*].code")).containsExactlyInAnyOrderElementsOf(prefixed("PRG-", searchable));
        assertThat(codes(body, "$.documents[*].code"))
                .containsExactlyInAnyOrderElementsOf(prefixed("DOC-", searchable));
        assertThat(codes(body, "$.faqs[*].code")).containsExactlyInAnyOrderElementsOf(prefixed("FAQ-", searchable));
        assertNoTraceOfHidden(body, searchable);

        // きっかけ経由（辞書）でも、根拠・関連制度・FAQ に見えないものは出ない
        String viaLifeEvent = navigate("ハブ", who);
        assertThat(codes(viaLifeEvent, "$.programs[0].basis[*].documentTitle"))
                .containsExactlyInAnyOrderElementsOf(prefixed("DOC-", searchable));
        assertThat(codes(viaLifeEvent, "$.relatedPrograms[*].code"))
                .containsExactlyInAnyOrderElementsOf(prefixed("PRG-", searchable));
        assertNoTraceOfHidden(viaLifeEvent, searchable);
    }

    @ParameterizedTest(name = "{0}: 見えないものだけに含まれる語で検索しても、該当なしと同じ応答になる")
    @MethodSource("matrix")
    void searchingForHiddenItemsLooksLikeNoResult(String who, List<String> viewable) throws Exception {
        for (String v : VISIBILITIES) {
            if (viewable.contains(v) && !v.equals("CONFIDENTIAL")) {
                continue;
            }
            String body = navigate(SECRET_WORDS.get(v), who);
            assertThat(JsonPath.<Boolean>read(body, "$.fallback")).as(v).isTrue();
            assertThat(JsonPath.<List<Object>>read(body, "$.programs")).as(v).isEmpty();
            assertThat(JsonPath.<List<Object>>read(body, "$.documents")).as(v).isEmpty();
            assertThat(JsonPath.<List<Object>>read(body, "$.faqs")).as(v).isEmpty();
            assertThat(JsonPath.<List<Object>>read(body, "$.relatedPrograms"))
                    .as(v)
                    .isEmpty();
            assertThat(codes(body, "$.contacts[*].code")).as(v).containsExactly("GENERAL");
        }
    }

    @ParameterizedTest(name = "{0}: 回答の文脈に入るのは {1} から機密を除いた条だけ")
    @MethodSource("matrix")
    void answerContextHasOnlySearchableSections(String who, List<String> viewable) {
        List<String> searchable =
                viewable.stream().filter(v -> !v.equals("CONFIDENTIAL")).toList();
        when(generator.sendableVisibilities()).thenReturn(Set.of("INTERNAL", "MANAGERS", "RESTRICTED"));

        List<Passage> passages = answerContext("マトリクス", who);

        assertThat(passages).extracting(Passage::title).allSatisfy(title -> assertThat(title)
                .startsWith("DOC-"));
        assertThat(passages.stream().map(p -> p.title().split(" ")[0]).toList())
                .containsExactlyInAnyOrderElementsOf(prefixed("DOC-", searchable));
        assertNoTraceOfHidden(passages.toString(), searchable);
    }

    @ParameterizedTest(name = "{0}: 社外に送るプロバイダには、社内公開の条だけを渡す")
    @MethodSource("matrix")
    void externalProvidersReceiveOnlySendableVisibilities(String who, List<String> viewable) {
        when(generator.sendableVisibilities()).thenReturn(Set.of("INTERNAL"));

        assertThat(answerContext("マトリクス", who))
                .extracting(p -> p.title().split(" ")[0])
                .containsExactly("DOC-INTERNAL");
    }

    // ------------------------------------------------------------------ 補助

    /** 回答を求め、生成のプロバイダに渡された条を返す（渡されなければ空） */
    private List<Passage> answerContext(String q, String who) {
        List<List<Passage>> received = new java.util.ArrayList<>();
        when(generator.generate(anyString(), anyList())).thenAnswer(invocation -> {
            received.add(invocation.getArgument(1));
            return new GeneratedAnswer(true, "ANSWERED", "回答です。[1]", List.of(1), "fake");
        });
        answerService.answer(q, users.get(who), new AnswerSink() {
            @Override
            public void results(NavigationResult result) {}

            @Override
            public void answer(AssistantAnswer answer) {}

            @Override
            public void unanswerable(AssistantUnanswerable unanswerable) {}
        });
        return received.isEmpty() ? List.of() : received.getFirst();
    }

    /** 応答のどこにも（抜粋・窓口名・根拠を含めて）見えない資源のコード・固有の語が含まれない */
    private static void assertNoTraceOfHidden(String body, List<String> visible) {
        for (String v : VISIBILITIES) {
            if (visible.contains(v)) {
                continue;
            }
            assertThat(body).as("%s の痕跡", v).doesNotContain("DOC-" + v, "PRG-" + v, "FAQ-" + v, SECRET_WORDS.get(v));
        }
    }

    private String navigate(String q, String who) throws Exception {
        return mockMvc.perform(get("/api/v1/navigate").param("q", q).with(user(users.get(who))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String getJson(String path, String who) throws Exception {
        return mockMvc.perform(get(path).with(user(users.get(who))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static List<String> codes(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static List<String> prefixed(String prefix, List<String> visibilities) {
        return visibilities.stream().map(v -> prefix + v).toList();
    }

    private static List<String> withHub(List<String> programCodes) {
        return Stream.concat(programCodes.stream(), Stream.of("PRG-HUB")).toList();
    }

    private UUID importDocument(String code, String visibility, String body) {
        return documentImport
                .importPublished(new ImportedDocument(
                        code,
                        code,
                        null,
                        "RULE",
                        "general",
                        ga,
                        visibility,
                        "v1.0",
                        LocalDate.of(2026, 4, 1),
                        "### 第1条（目的）\n\n" + body + "\n"))
                .documentId();
    }

    private UUID insertProgram(String code, String visibility, String name) {
        return jdbc.sql("""
                        INSERT INTO programs (code, name, summary, owner_department_id, visibility, status, search_text)
                        VALUES (:code, :name, :name, :dept, :visibility, 'PUBLISHED', lower(:name))
                        RETURNING id
                        """)
                .param("code", code)
                .param("name", name)
                .param("dept", ga)
                .param("visibility", visibility)
                .query(UUID.class)
                .single();
    }

    private UUID insertFaq(String code, String visibility, String question) {
        return jdbc.sql("""
                        INSERT INTO faqs (code, question, answer_markdown, owner_department_id, visibility, status, search_text)
                        VALUES (:code, :question, '回答', :dept, :visibility, 'PUBLISHED', lower(:question))
                        RETURNING id
                        """)
                .param("code", code)
                .param("question", question)
                .param("dept", ga)
                .param("visibility", visibility)
                .query(UUID.class)
                .single();
    }

    private void accessRule(String resourceType, UUID resourceId, String principalType, String principalId) {
        jdbc.sql("""
                        INSERT INTO access_rules (resource_type, resource_id, principal_type, principal_id)
                        VALUES (:rt, :rid, :pt, :pid)
                        """)
                .param("rt", resourceType)
                .param("rid", resourceId)
                .param("pt", principalType)
                .param("pid", principalId)
                .update();
    }

    private KnaviUserDetails insertUser(UUID department, String email, String role) {
        UUID id = jdbc.sql(
                        "INSERT INTO users (email, display_name, department_id, role) VALUES (:e, :e, :d, :r) RETURNING id")
                .param("e", email)
                .param("d", department)
                .param("r", role)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, department, email, null, role, true);
    }

    private UUID insertId(String sql) {
        return jdbc.sql(sql).query(UUID.class).single();
    }
}
