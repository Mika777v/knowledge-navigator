package com.example.knavi.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.assistant.application.GuideViews.GuideExtraction;
import com.example.knavi.intelligence.AnswerExtractor;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自由な文からの答えの読み取り（ADR-0014 の決定 2）。LLM の上乗せを ON にして、送る質問と返す答えを確かめる。
 * 見られない制度の条件の質問は送らず、決まった選択肢に無い答えは捨てる。
 */
@SpringBootTest(properties = "knavi.assistant.enabled=true")
@Import(TestcontainersConfiguration.class)
@Transactional
class GuideExtractTests {

    @Autowired
    GuideService service;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    AnswerExtractor extractor;

    String suffix;
    String lifeEvent;
    KnaviUserDetails employee;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID dept = jdbc.sql("INSERT INTO departments (code, name) VALUES (:c, 'テスト部') RETURNING id")
                .param("c", "GX-" + suffix)
                .query(UUID.class)
                .single();
        UUID employment = question("T_EMP_" + suffix, 1, """
                [{"value":"REGULAR","label":"正社員"},{"value":"PART_TIME","label":"パートタイム社員"}]""");
        UUID tenure = question("T_TEN_" + suffix, 2, """
                [{"value":"YES","label":"はい"},{"value":"NO","label":"いいえ"}]""");
        lifeEvent = "GX-" + suffix;
        UUID event = jdbc.sql("INSERT INTO life_events (code, name) VALUES (:c, 'テストのきっかけ') RETURNING id")
                .param("c", lifeEvent)
                .query(UUID.class)
                .single();
        UUID gift = program("GX-GIFT-" + suffix, "INTERNAL", dept);
        UUID managersOnly = program("GX-MGR-" + suffix, "MANAGERS", dept);
        jdbc.sql("""
                        INSERT INTO program_conditions (program_id, question_id, allowed_values)
                        VALUES (:gift, :tenure, ARRAY['YES']), (:mgr, :employment, ARRAY['REGULAR'])
                        """)
                .param("gift", gift)
                .param("tenure", tenure)
                .param("mgr", managersOnly)
                .param("employment", employment)
                .update();
        jdbc.sql("INSERT INTO life_event_programs (life_event_id, program_id) VALUES (:e, :a), (:e, :b)")
                .param("e", event)
                .param("a", gift)
                .param("b", managersOnly)
                .update();
        UUID user = jdbc.sql("INSERT INTO users (email, display_name, department_id, role)"
                        + " VALUES (:e, 'テスト', :d, 'EMPLOYEE') RETURNING id")
                .param("e", "gx-" + suffix + "@example.com")
                .param("d", dept)
                .query(UUID.class)
                .single();
        employee = new KnaviUserDetails(user, dept, "gx-" + suffix + "@example.com", null, "EMPLOYEE", true);
    }

    @Test
    void sendsOnlyQuestionsOfVisibleProgramsAndKeepsOnlyValidChoices() {
        when(extractor.extract(eq("パートで入社して2年です"), anyList()))
                .thenReturn(Map.of("T_TEN_" + suffix, "YES", "T_EMP_" + suffix, "PART_TIME", "OTHER", "X"));

        GuideExtraction extraction = service.extract(lifeEvent, " パートで入社して2年です ", employee);

        assertThat(extraction.available()).isTrue();
        // 一般社員には MANAGERS の制度が見えないので、雇用形態の質問は送らず、読み取った答えも返さない
        assertThat(extraction.answers()).containsExactly(Map.entry("T_TEN_" + suffix, "YES"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnswerExtractor.Question>> sent = ArgumentCaptor.forClass(List.class);
        verify(extractor).extract(eq("パートで入社して2年です"), sent.capture());
        assertThat(sent.getValue()).extracting(AnswerExtractor.Question::code).containsExactly("T_TEN_" + suffix);
        assertThat(sent.getValue().getFirst().choices())
                .extracting(AnswerExtractor.Choice::label)
                .containsExactly("はい", "いいえ");
    }

    @Test
    void choicesOutsideTheQuestionAreDropped() {
        when(extractor.extract(eq("入社したばかりです"), anyList())).thenReturn(Map.of("T_TEN_" + suffix, "MAYBE"));

        assertThat(service.extract(lifeEvent, "入社したばかりです", employee).answers()).isEmpty();
    }

    private UUID question(String code, int sort, String choices) {
        return jdbc.sql("INSERT INTO assistant_questions (code, text, choices, sort_order)"
                        + " VALUES (:c, :t, CAST(:choices AS jsonb), :s) RETURNING id")
                .param("c", code)
                .param("t", "質問 " + code)
                .param("choices", choices)
                .param("s", sort)
                .query(UUID.class)
                .single();
    }

    private UUID program(String code, String visibility, UUID dept) {
        return jdbc.sql("""
                        INSERT INTO programs (code, name, summary, owner_department_id, visibility, status, search_text)
                        VALUES (:code, :code, '概要', :dept, :vis, 'PUBLISHED', '制度') RETURNING id
                        """)
                .param("code", code)
                .param("dept", dept)
                .param("vis", visibility)
                .query(UUID.class)
                .single();
    }
}
