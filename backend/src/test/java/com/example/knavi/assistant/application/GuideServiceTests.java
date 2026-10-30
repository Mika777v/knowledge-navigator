package com.example.knavi.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.assistant.application.GuideViews.GuideProgram;
import com.example.knavi.assistant.application.GuideViews.GuideQuestion;
import com.example.knavi.assistant.application.GuideViews.GuideResult;
import com.example.knavi.assistant.domain.Eligibility.Status;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

/**
 * 案内（ADR-0014 の決定 1）。見られる制度だけを判定し、見られない制度の条件からは質問を作らない（ADR-0004）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class GuideServiceTests {

    @Autowired
    GuideService service;

    @Autowired
    JdbcClient jdbc;

    UUID dept;
    String lifeEvent;
    UUID gift;
    UUID managersOnly;
    UUID noConditions;
    KnaviUserDetails employee;
    KnaviUserDetails manager;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        dept = jdbc.sql("INSERT INTO departments (code, name) VALUES (:c, 'テスト部') RETURNING id")
                .param("c", "GD-" + suffix)
                .query(UUID.class)
                .single();
        UUID contact = jdbc.sql("INSERT INTO contacts (code, name, department_id, email)"
                        + " VALUES (:c, 'テスト窓口', :d, 'guide@example.com') RETURNING id")
                .param("c", "GD-" + suffix)
                .param("d", dept)
                .query(UUID.class)
                .single();
        UUID employment = question(
                "T_EMP_" + suffix,
                "EMPLOYMENT_TYPE",
                1,
                "[{\"value\":\"REGULAR\",\"label\":\"正社員\"},{\"value\":\"CONTRACT\",\"label\":\"契約社員\"},"
                        + "{\"value\":\"PART_TIME\",\"label\":\"パートタイム社員\"}]");
        UUID tenure = question(
                "T_TEN_" + suffix,
                null,
                2,
                "[{\"value\":\"YES\",\"label\":\"はい\"},{\"value\":\"NO\",\"label\":\"いいえ\"}]");

        gift = program("GD-GIFT-" + suffix, "INTERNAL", contact);
        condition(gift, tenure, "YES");
        managersOnly = program("GD-MGR-" + suffix, "MANAGERS", contact);
        condition(managersOnly, employment, "REGULAR");
        noConditions = program("GD-FREE-" + suffix, "INTERNAL", contact);

        lifeEvent = "GD-" + suffix;
        UUID event = jdbc.sql("INSERT INTO life_events (code, name) VALUES (:c, 'テストのきっかけ') RETURNING id")
                .param("c", lifeEvent)
                .query(UUID.class)
                .single();
        link(event, gift, "PRIMARY", 1);
        link(event, managersOnly, "PRIMARY", 2);
        link(event, noConditions, "RELATED", 1);

        employee = user("EMPLOYEE", "PART_TIME");
        manager = user("MANAGER", "REGULAR");
    }

    @Test
    void employeeIsNotAskedAboutConditionsOfProgramsTheyCannotSee() {
        GuideResult result = service.guide(lifeEvent, Map.of(), employee);

        assertThat(result.programs()).extracting(GuideProgram::id).containsExactly(gift, noConditions);
        assertThat(result.questions()).extracting(GuideQuestion::code).containsExactly(code("T_TEN_"));
        assertThat(result.questions().getFirst().answerSource()).isEqualTo(GuideService.SOURCE_NONE);
        assertThat(status(result, gift)).isEqualTo(Status.UNKNOWN);
        assertThat(status(result, noConditions)).isEqualTo(Status.ELIGIBLE);
        assertThat(result.programs().getFirst().contacts())
                .extracting(c -> c.email())
                .containsExactly("guide@example.com");
    }

    @Test
    void managerSeesBothQuestionsAndEmploymentTypeIsPrefilledFromTheProfile() {
        GuideResult result = service.guide(lifeEvent, Map.of(), manager);

        assertThat(result.questions()).extracting(GuideQuestion::code).containsExactly(code("T_EMP_"), code("T_TEN_"));
        GuideQuestion employment = result.questions().getFirst();
        assertThat(employment.answer()).isEqualTo("REGULAR");
        assertThat(employment.answerSource()).isEqualTo(GuideService.SOURCE_PROFILE);
        assertThat(employment.choices()).extracting(c -> c.label()).containsExactly("正社員", "契約社員", "パートタイム社員");
        assertThat(status(result, managersOnly)).isEqualTo(Status.ELIGIBLE);
    }

    @Test
    void answersDecideTheStatusAndOnlyEligibleOrUnknownProgramsAreInTheChecklist() {
        GuideResult no = service.guide(lifeEvent, Map.of(code("T_TEN_"), "NO"), employee);

        assertThat(status(no, gift)).isEqualTo(Status.NOT_ELIGIBLE);
        assertThat(no.programs().getFirst().unmetQuestions()).containsExactly(code("T_TEN_"));
        assertThat(no.checklist()).extracting(item -> item.programId()).containsExactly(noConditions);

        GuideResult yes = service.guide(lifeEvent, Map.of(code("T_TEN_"), "YES"), employee);

        assertThat(status(yes, gift)).isEqualTo(Status.ELIGIBLE);
        assertThat(yes.questions().getFirst().answerSource()).isEqualTo(GuideService.SOURCE_USER);
        assertThat(yes.checklist()).extracting(item -> item.programId()).containsExactly(gift, noConditions);
        assertThat(yes.checklist().getFirst().requiredDocuments()).isEqualTo("証明書");
        assertThat(yes.checklist().getFirst().deadlineNote()).isEqualTo("6か月以内");
    }

    @Test
    void userAnswerOverridesTheProfile() {
        GuideResult result = service.guide(lifeEvent, Map.of(code("T_EMP_"), "PART_TIME"), manager);

        assertThat(result.questions().getFirst().answerSource()).isEqualTo(GuideService.SOURCE_USER);
        assertThat(status(result, managersOnly)).isEqualTo(Status.NOT_ELIGIBLE);
    }

    @Test
    void rejectsAnswersOutsideTheChoicesAndUnknownLifeEvents() {
        assertThatThrownBy(() -> service.guide(lifeEvent, Map.of(code("T_TEN_"), "MAYBE"), employee))
                .isInstanceOf(ErrorResponseException.class);
        assertThatThrownBy(() -> service.guide("NO-SUCH-EVENT", Map.of(), employee))
                .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void extractionIsUnavailableWhenTheLlmIsTurnedOff() {
        GuideViews.GuideExtraction extraction = service.extract(lifeEvent, "入社して2年です", employee);

        assertThat(extraction.available()).isFalse();
        assertThat(extraction.answers()).isEmpty();
        assertThatThrownBy(() -> service.extract(lifeEvent, " ", employee)).isInstanceOf(ErrorResponseException.class);
    }

    private String code(String prefix) {
        return prefix + lifeEvent.substring(3);
    }

    private static Status status(GuideResult result, UUID program) {
        return result.programs().stream()
                .filter(p -> p.id().equals(program))
                .findFirst()
                .orElseThrow()
                .status();
    }

    private UUID question(String code, String profile, int sort, String choices) {
        return jdbc.sql("INSERT INTO assistant_questions (code, text, choices, profile_attribute, sort_order)"
                        + " VALUES (:c, :t, CAST(:choices AS jsonb), :p, :s) RETURNING id")
                .param("c", code)
                .param("t", "質問 " + code)
                .param("choices", choices)
                .param("p", profile)
                .param("s", sort)
                .query(UUID.class)
                .single();
    }

    private UUID program(String code, String visibility, UUID contact) {
        UUID id = jdbc.sql("""
                        INSERT INTO programs (code, name, summary, eligibility, required_documents, deadline_note,
                                              owner_department_id, visibility, status, search_text)
                        VALUES (:code, :code, '概要', '対象者', '証明書', '6か月以内', :dept, :vis, 'PUBLISHED', '制度')
                        RETURNING id
                        """)
                .param("code", code)
                .param("dept", dept)
                .param("vis", visibility)
                .query(UUID.class)
                .single();
        jdbc.sql("INSERT INTO program_contacts (program_id, contact_id, role) VALUES (:p, :c, 'APPLY')")
                .param("p", id)
                .param("c", contact)
                .update();
        return id;
    }

    private void condition(UUID program, UUID question, String allowed) {
        jdbc.sql("INSERT INTO program_conditions (program_id, question_id, allowed_values)"
                        + " VALUES (:p, :q, ARRAY[CAST(:v AS text)])")
                .param("p", program)
                .param("q", question)
                .param("v", allowed)
                .update();
    }

    private void link(UUID event, UUID program, String relation, int order) {
        jdbc.sql("INSERT INTO life_event_programs (life_event_id, program_id, relation, sort_order)"
                        + " VALUES (:e, :p, :r, :o)")
                .param("e", event)
                .param("p", program)
                .param("r", relation)
                .param("o", order)
                .update();
    }

    private KnaviUserDetails user(String role, String employmentType) {
        String email = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        UUID id = jdbc.sql("INSERT INTO users (email, display_name, department_id, role, employment_type)"
                        + " VALUES (:e, 'テスト', :d, :r, :t) RETURNING id")
                .param("e", email)
                .param("d", dept)
                .param("r", role)
                .param("t", employmentType)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, dept, email, null, role, true);
    }
}
