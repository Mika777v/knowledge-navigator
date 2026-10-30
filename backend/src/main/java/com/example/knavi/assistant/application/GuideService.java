package com.example.knavi.assistant.application;

import com.example.knavi.assistant.application.GuideViews.GuideBasis;
import com.example.knavi.assistant.application.GuideViews.GuideChecklistItem;
import com.example.knavi.assistant.application.GuideViews.GuideChoice;
import com.example.knavi.assistant.application.GuideViews.GuideContact;
import com.example.knavi.assistant.application.GuideViews.GuideExtraction;
import com.example.knavi.assistant.application.GuideViews.GuideLifeEvent;
import com.example.knavi.assistant.application.GuideViews.GuideProgram;
import com.example.knavi.assistant.application.GuideViews.GuideQuestion;
import com.example.knavi.assistant.application.GuideViews.GuideResult;
import com.example.knavi.assistant.domain.Eligibility;
import com.example.knavi.assistant.domain.Eligibility.Condition;
import com.example.knavi.assistant.domain.Eligibility.Judgement;
import com.example.knavi.assistant.domain.Eligibility.Status;
import com.example.knavi.intelligence.AnswerExtractor;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AccessPolicy.Purpose;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.security.SqlPredicate;
import com.example.knavi.shared.web.ApiErrors;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 案内（ADR-0014 の決定 1・E15-T2〜T4）。きっかけの制度を、確認質問の答えで対象・対象外・要確認に分け、手続きを並べる。
 *
 * <ul>
 *   <li>LLM を使わず、DB の条件と答えだけで決める。AI が止まっていても同じ結果を返す
 *   <li>制度は AccessPolicy で見られるものだけ（ADR-0004）。質問も、見られる制度の条件に出てくるものだけを出す
 *   <li>根拠・窓口・書類・期限は DB の値（ADR-0009）。答えは保存しない
 * </ul>
 */
@Service
public class GuideService {

    /** 利用者の属性から初めに選んでおく値（assistant_questions.profile_attribute） */
    static final String PROFILE_EMPLOYMENT_TYPE = "EMPLOYMENT_TYPE";

    static final String SOURCE_USER = "USER";
    static final String SOURCE_PROFILE = "PROFILE";
    static final String SOURCE_NONE = "NONE";

    /** 自由な文の長さの上限（検索の質問と同じ） */
    public static final int MAX_TEXT_LENGTH = 200;

    private final JdbcClient jdbc;
    private final AccessPolicy accessPolicy;
    private final AnswerExtractor extractor;
    /** LLM の上乗せを使うか（ADR-0014 の決定 2。根拠付き回答と同じ設定） */
    private final boolean llmEnabled;

    GuideService(
            JdbcClient jdbc,
            AccessPolicy accessPolicy,
            AnswerExtractor extractor,
            @Value("${knavi.assistant.enabled:false}") boolean llmEnabled) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.extractor = extractor;
        this.llmEnabled = llmEnabled;
    }

    private record ProgramRow(
            UUID id,
            String code,
            String name,
            String summary,
            String relation,
            String eligibility,
            String requiredDocuments,
            String deadlineNote,
            String howToApply) {}

    private record QuestionRow(String code, String text, String profileAttribute, List<GuideChoice> choices) {}

    /** @param answers 質問のコード → 選んだ value。省いた質問は、利用者の属性から選べるものは選び、それ以外は未回答 */
    @Transactional(readOnly = true)
    public GuideResult guide(String lifeEventCode, Map<String, String> answers, AuthenticatedUser user) {
        GuideLifeEvent lifeEvent = lifeEvent(lifeEventCode);
        AccessScope scope = AccessScope.of(user);
        List<ProgramRow> programs = programs(lifeEventCode, scope);
        Set<UUID> programIds = programs.stream().map(ProgramRow::id).collect(Collectors.toSet());
        Map<UUID, List<Condition>> conditions = conditions(programIds);
        List<QuestionRow> questions = askedQuestions(conditions);

        Map<String, String> given = answers == null ? Map.of() : answers;
        Map<String, String> effective = new HashMap<>();
        List<GuideQuestion> questionViews = new ArrayList<>();
        for (QuestionRow q : questions) {
            String userAnswer = given.get(q.code());
            if (userAnswer != null
                    && q.choices().stream().noneMatch(c -> c.value().equals(userAnswer))) {
                throw ApiErrors.badRequest("「" + q.text() + "」の答えが選択肢にありません。");
            }
            String answer = userAnswer;
            String source = SOURCE_USER;
            if (answer == null) {
                answer = profileAnswer(q, user);
                source = answer == null ? SOURCE_NONE : SOURCE_PROFILE;
            }
            if (answer != null) {
                effective.put(q.code(), answer);
            }
            questionViews.add(new GuideQuestion(q.code(), q.text(), q.choices(), answer, source));
        }

        Map<UUID, List<GuideBasis>> basis = basis(programIds, scope);
        Map<UUID, List<GuideContact>> contacts = contacts(programIds);
        List<GuideProgram> programViews = new ArrayList<>();
        List<GuideChecklistItem> checklist = new ArrayList<>();
        for (ProgramRow p : programs) {
            Judgement judgement = Eligibility.judge(conditions.getOrDefault(p.id(), List.of()), effective);
            programViews.add(new GuideProgram(
                    p.id(),
                    p.code(),
                    p.name(),
                    p.summary(),
                    p.relation(),
                    p.eligibility(),
                    judgement.status(),
                    judgement.unmet(),
                    judgement.unanswered(),
                    basis.getOrDefault(p.id(), List.of()),
                    contacts.getOrDefault(p.id(), List.of())));
            if (judgement.status() != Status.NOT_ELIGIBLE) {
                checklist.add(new GuideChecklistItem(
                        p.id(), p.name(), judgement.status(), p.requiredDocuments(), p.deadlineNote(), p.howToApply()));
            }
        }
        return new GuideResult(lifeEvent, questionViews, programViews, checklist);
    }

    /**
     * 自由な文から、確認質問の答えを読み取る（ADR-0014 の決定 2）。送るのは、見られる制度の条件に出てくる質問だけ。
     * 読み取った答えも、決まった選択肢にあるものだけを返す。答えも文も保存しない。
     */
    @Transactional(readOnly = true)
    public GuideExtraction extract(String lifeEventCode, String text, AuthenticatedUser user) {
        String statement = text == null ? "" : text.strip();
        if (statement.isEmpty() || statement.length() > MAX_TEXT_LENGTH) {
            throw ApiErrors.badRequest("状況は" + MAX_TEXT_LENGTH + "文字以内で入力してください。");
        }
        lifeEvent(lifeEventCode);
        if (!llmEnabled) {
            return new GuideExtraction(Map.of(), false);
        }
        Set<UUID> programIds = programs(lifeEventCode, AccessScope.of(user)).stream()
                .map(ProgramRow::id)
                .collect(Collectors.toSet());
        List<QuestionRow> questions = askedQuestions(conditions(programIds));
        if (questions.isEmpty()) {
            return new GuideExtraction(Map.of(), true);
        }
        Map<String, String> read = extractor.extract(
                statement,
                questions.stream()
                        .map(q -> new AnswerExtractor.Question(
                                q.code(),
                                q.text(),
                                q.choices().stream()
                                        .map(c -> new AnswerExtractor.Choice(c.value(), c.label()))
                                        .toList()))
                        .toList());
        Map<String, String> answers = new LinkedHashMap<>();
        for (QuestionRow q : questions) {
            String value = read.get(q.code());
            if (value != null && q.choices().stream().anyMatch(c -> c.value().equals(value))) {
                answers.put(q.code(), value);
            }
        }
        return new GuideExtraction(answers, true);
    }

    private GuideLifeEvent lifeEvent(String code) {
        return jdbc.sql("SELECT code, name, description FROM life_events WHERE code = :code")
                .param("code", code)
                .query((rs, i) ->
                        new GuideLifeEvent(rs.getString("code"), rs.getString("name"), rs.getString("description")))
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("きっかけが見つかりません: " + code));
    }

    /** 見られる制度の条件に出てくる質問（ADR-0004。見られない制度の条件から質問を作らない） */
    private List<QuestionRow> askedQuestions(Map<UUID, List<Condition>> conditions) {
        Set<String> asked = new LinkedHashSet<>();
        conditions.values().forEach(list -> list.forEach(c -> asked.add(c.questionCode())));
        return questions(asked);
    }

    private String profileAnswer(QuestionRow question, AuthenticatedUser user) {
        if (!PROFILE_EMPLOYMENT_TYPE.equals(question.profileAttribute())) {
            return null;
        }
        String value = jdbc.sql("SELECT employment_type FROM users WHERE id = :id")
                .param("id", user.id())
                .query(String.class)
                .optional()
                .orElse(null);
        // 選択肢に無い値（データの食い違い）は選ばない
        return question.choices().stream().anyMatch(c -> c.value().equals(value)) ? value : null;
    }

    /** きっかけの制度のうち、見られるもの（PRIMARY → RELATED、並び順） */
    private List<ProgramRow> programs(String lifeEventCode, AccessScope scope) {
        SqlPredicate visible = accessPolicy.readPredicate(scope, "programs", "p", Purpose.VIEW);
        return jdbc.sql("SELECT p.id, p.code, p.name, p.summary, lp.relation, p.eligibility, p.required_documents,"
                        + " p.deadline_note, p.how_to_apply FROM life_events e"
                        + " JOIN life_event_programs lp ON lp.life_event_id = e.id JOIN programs p ON p.id = lp.program_id"
                        + " WHERE e.code = :code AND " + visible.sql()
                        + " ORDER BY lp.relation, lp.sort_order, p.code")
                .params(visible.params())
                .param("code", lifeEventCode)
                .query((rs, i) -> new ProgramRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("summary"),
                        rs.getString("relation"),
                        rs.getString("eligibility"),
                        rs.getString("required_documents"),
                        rs.getString("deadline_note"),
                        rs.getString("how_to_apply")))
                .list();
    }

    private record ProgramValue<T>(UUID programId, T value) {}

    private static <T> Map<UUID, List<T>> byProgram(List<ProgramValue<T>> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        ProgramValue::programId, Collectors.mapping(ProgramValue::value, Collectors.toList())));
    }

    private Map<UUID, List<Condition>> conditions(Set<UUID> programIds) {
        if (programIds.isEmpty()) {
            return Map.of();
        }
        return byProgram(jdbc.sql("SELECT c.program_id, q.code, c.allowed_values FROM program_conditions c"
                        + " JOIN assistant_questions q ON q.id = c.question_id WHERE c.program_id IN (:ids)"
                        + " ORDER BY q.sort_order, q.code")
                .param("ids", programIds)
                .query((rs, i) -> new ProgramValue<>(
                        rs.getObject("program_id", UUID.class),
                        new Condition(rs.getString("code"), strings(rs.getArray("allowed_values")))))
                .list());
    }

    private List<QuestionRow> questions(Set<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        record ChoiceRow(String code, String text, String profileAttribute, GuideChoice choice) {}
        List<ChoiceRow> rows = jdbc.sql("SELECT q.code, q.text, q.profile_attribute, c.elem ->> 'value' AS value,"
                        + " c.elem ->> 'label' AS label FROM assistant_questions q"
                        + " CROSS JOIN LATERAL jsonb_array_elements(q.choices) WITH ORDINALITY AS c(elem, ord)"
                        + " WHERE q.code IN (:codes) ORDER BY q.sort_order, q.code, c.ord")
                .param("codes", codes)
                .query((rs, i) -> new ChoiceRow(
                        rs.getString("code"),
                        rs.getString("text"),
                        rs.getString("profile_attribute"),
                        new GuideChoice(rs.getString("value"), rs.getString("label"))))
                .list();
        Map<String, QuestionRow> questions = new LinkedHashMap<>();
        for (ChoiceRow row : rows) {
            questions
                    .computeIfAbsent(
                            row.code(),
                            code -> new QuestionRow(code, row.text(), row.profileAttribute(), new ArrayList<>()))
                    .choices()
                    .add(row.choice());
        }
        return List.copyOf(questions.values());
    }

    /** 根拠の条。文書が見られるものだけ（制度より狭い公開範囲の文書は出さない） */
    private Map<UUID, List<GuideBasis>> basis(Set<UUID> programIds, AccessScope scope) {
        if (programIds.isEmpty()) {
            return Map.of();
        }
        SqlPredicate visible = accessPolicy.readPredicate(scope, "documents", "d", Purpose.VIEW);
        return byProgram(jdbc.sql("SELECT pb.program_id, d.id AS document_id, d.title, s.id AS section_id,"
                        + " pb.article_no, s.heading FROM program_basis pb JOIN documents d ON d.id = pb.document_id"
                        + " LEFT JOIN sections s ON s.id = pb.section_id"
                        + " WHERE pb.program_id IN (:ids) AND " + visible.sql()
                        + " ORDER BY pb.program_id, pb.sort_order")
                .params(visible.params())
                .param("ids", programIds)
                .query((rs, i) -> new ProgramValue<>(
                        rs.getObject("program_id", UUID.class),
                        new GuideBasis(
                                rs.getObject("document_id", UUID.class),
                                rs.getString("title"),
                                rs.getObject("section_id", UUID.class),
                                rs.getString("article_no"),
                                rs.getString("heading"))))
                .list());
    }

    /** 窓口（DB の値。ADR-0009）。申請先 → 問い合わせ先 → 支給担当の順 */
    private Map<UUID, List<GuideContact>> contacts(Set<UUID> programIds) {
        if (programIds.isEmpty()) {
            return Map.of();
        }
        return byProgram(jdbc.sql("SELECT pc.program_id, ct.code, ct.name, dep.name AS department, ct.email,"
                        + " ct.phone_ext, ct.chat_url, ct.office_hours, pc.role FROM program_contacts pc"
                        + " JOIN contacts ct ON ct.id = pc.contact_id JOIN departments dep ON dep.id = ct.department_id"
                        + " WHERE pc.program_id IN (:ids)"
                        + " ORDER BY pc.program_id, CASE pc.role WHEN 'APPLY' THEN 0 WHEN 'INQUIRY' THEN 1 ELSE 2 END,"
                        + " pc.sort_order")
                .param("ids", programIds)
                .query((rs, i) -> new ProgramValue<>(
                        rs.getObject("program_id", UUID.class),
                        new GuideContact(
                                rs.getString("code"),
                                rs.getString("name"),
                                rs.getString("department"),
                                rs.getString("email"),
                                rs.getString("phone_ext"),
                                rs.getString("chat_url"),
                                rs.getString("office_hours"),
                                rs.getString("role"))))
                .list());
    }

    private static Set<String> strings(Array array) throws SQLException {
        return array == null ? Set.of() : Set.copyOf(Arrays.asList((String[]) array.getArray()));
    }
}
