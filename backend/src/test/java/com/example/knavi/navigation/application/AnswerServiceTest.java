package com.example.knavi.navigation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.knavi.intelligence.AnswerGenerator;
import com.example.knavi.intelligence.AnswerGenerator.GeneratedAnswer;
import com.example.knavi.intelligence.AnswerGenerator.Passage;
import com.example.knavi.navigation.application.NavigationResult.BasisRef;
import com.example.knavi.navigation.application.NavigationResult.DocumentHit;
import com.example.knavi.navigation.application.NavigationResult.ProgramHit;
import com.example.knavi.navigation.application.NavigationResult.QueryInfo;
import com.example.knavi.navigation.application.NavigationResult.SectionRef;
import com.example.knavi.navigation.infrastructure.NavigationQueries;
import com.example.knavi.navigation.infrastructure.NavigationQueries.PassageRow;
import com.example.knavi.shared.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.ErrorResponseException;

/** 根拠付き回答の組み立て（E14-T4）。検索・権限の読み直し・生成は差し替えて、番号と根拠のつなぎ方を確かめる */
class AnswerServiceTest {

    static final Set<String> LOCAL = Set.of("INTERNAL", "MANAGERS", "RESTRICTED");

    final NavigationService navigation = mock(NavigationService.class);
    final NavigationQueries queries = mock(NavigationQueries.class);
    final AnswerGenerator generator = mock(AnswerGenerator.class);
    final AuthenticatedUser user = new TestUser();
    final RecordingSink sink = new RecordingSink();

    final UUID first = UUID.randomUUID();
    final UUID second = UUID.randomUUID();
    final UUID document = UUID.randomUUID();

    AnswerService service(boolean enabled) {
        return new AnswerService(navigation, queries, generator, new AssistantProperties(enabled, 6));
    }

    @BeforeEach
    void setUp() {
        when(navigation.navigateWithSections(anyString(), any())).thenReturn(navigated(result(false, first, second)));
        when(generator.sendableVisibilities()).thenReturn(LOCAL);
        when(queries.passages(any(), any()))
                .thenReturn(List.of(
                        row(second, "INTERNAL", "第6条", "出産祝金", "出産祝金として20,000円を支給する。"),
                        row(first, "INTERNAL", "第5条", "結婚祝金", "結婚祝金として30,000円を支給する。")));
    }

    @Test
    void numbersThePassagesInSearchOrderAndMapsCitationsBackToSections() {
        when(generator.generate(anyString(), anyList()))
                .thenReturn(new GeneratedAnswer(true, "ANSWERED", "30,000円です。[1]", List.of(1, 9), "qwen3.5:4b"));

        service(true).answer("結婚祝金はいくら？", user, sink);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Passage>> passages = ArgumentCaptor.forClass(List.class);
        verify(generator).generate(org.mockito.ArgumentMatchers.eq("結婚祝金はいくら？"), passages.capture());
        assertThat(passages.getValue())
                .containsExactly(
                        new Passage(1, "慶弔見舞金規程 第5条（結婚祝金）", "結婚祝金として30,000円を支給する。"),
                        new Passage(2, "慶弔見舞金規程 第6条（出産祝金）", "出産祝金として20,000円を支給する。"));
        assertThat(sink.events).containsExactly("results", "answer");
        // 文脈に無い番号（9）は根拠にしない
        assertThat(sink.answer.evidence()).singleElement().satisfies(e -> {
            assertThat(e.ref()).isEqualTo(1);
            assertThat(e.sectionId()).isEqualTo(first);
            assertThat(e.documentTitle()).isEqualTo("慶弔見舞金規程");
            assertThat(e.articleNo()).isEqualTo("第5条");
        });
        assertThat(sink.answer.model()).isEqualTo("qwen3.5:4b");
    }

    @Test
    void noValidCitationMeansVerificationFailed() {
        when(generator.generate(anyString(), anyList()))
                .thenReturn(new GeneratedAnswer(true, "ANSWERED", "30,000円です。[7]", List.of(7), "m"));

        service(true).answer("結婚祝金", user, sink);

        assertThat(sink.events).containsExactly("results", "unanswerable");
        assertThat(sink.reason).isEqualTo("VERIFICATION_FAILED");
    }

    @Test
    void theGeneratorsReasonIsPassedOnWhenItCannotAnswer() {
        when(generator.generate(anyString(), anyList())).thenReturn(GeneratedAnswer.unanswerable("GENERATION_FAILED"));

        service(true).answer("結婚祝金", user, sink);

        assertThat(sink.reason).isEqualTo("GENERATION_FAILED");
    }

    @Test
    void disabledReturnsOnlyTheResults() {
        service(false).answer("結婚祝金", user, sink);

        assertThat(sink.events).containsExactly("results", "unanswerable");
        assertThat(sink.reason).isEqualTo("DISABLED");
        verify(generator, never()).generate(anyString(), anyList());
    }

    @Test
    void nothingFoundMeansNoEvidenceWithoutCallingTheModel() {
        when(navigation.navigateWithSections(anyString(), any())).thenReturn(navigated(result(true)));

        service(true).answer("社員食堂のメニュー", user, sink);

        assertThat(sink.reason).isEqualTo("NO_EVIDENCE");
        verify(generator, never()).generate(anyString(), anyList());
    }

    @Test
    void onlyVisibilitiesThatMayBeSentGoToTheModel() {
        when(queries.passages(any(), any()))
                .thenReturn(List.of(
                        row(first, "MANAGERS", "第5条", "結婚祝金", "管理職向けの本文"),
                        row(second, "INTERNAL", "第6条", "出産祝金", "社内向けの本文")));

        List<PassageRow> rows =
                service(true).passages(navigated(result(false, first, second)), user, Set.of("INTERNAL"));

        assertThat(rows).extracting(PassageRow::sectionId).containsExactly(second);
        assertThat(service(true).passages(navigated(result(false, first, second)), user, Set.of()))
                .isEmpty();
    }

    @Test
    void sectionsThatCanNoLongerBeReadAreDropped() {
        when(queries.passages(any(), any())).thenReturn(List.of(row(second, "INTERNAL", "第6条", "出産祝金", "本文")));

        assertThat(service(true).passages(navigated(result(false, first, second)), user, LOCAL))
                .extracting(PassageRow::sectionId)
                .containsExactly(second);
    }

    @Test
    void sectionsTheSearchFoundBeyondTheShownOnesAreCandidatesAfterTheProgramBasis() {
        UUID basis = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        NavigationResult result = withProgramBasis(result(false, first, second), basis);
        when(queries.passages(any(), any()))
                .thenReturn(List.of(
                        row(first, "INTERNAL", "第5条", "結婚祝金", "本文"),
                        row(second, "INTERNAL", "第6条", "出産祝金", "本文"),
                        row(hidden, "INTERNAL", "第9条", "弔慰金", "本文"),
                        row(basis, "INTERNAL", "第2条", "対象", "本文")));

        // 検索が拾った条（スコア順）: 画面に出ていない hidden が先、first が次
        List<PassageRow> rows =
                service(true).passages(new NavigationService.Navigation(result, List.of(hidden, first)), user, LOCAL);

        // 制度の根拠 → 検索のスコア順 → 結果に出した文書の残りの条
        assertThat(rows).extracting(PassageRow::sectionId).containsExactly(basis, hidden, first, second);
    }

    @Test
    void theNumberOfPassagesIsCapped() {
        List<UUID> found = new ArrayList<>();
        List<PassageRow> readable = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            found.add(id);
            readable.add(row(id, "INTERNAL", "第" + i + "条", "見出し", "本文"));
        }
        when(queries.passages(any(), any())).thenReturn(readable);

        List<PassageRow> rows =
                service(true).passages(new NavigationService.Navigation(result(false), found), user, LOCAL);

        assertThat(rows).extracting(PassageRow::sectionId).containsExactlyElementsOf(found.subList(0, 6));
    }

    @Test
    void questionsAreValidatedBeforeTheStreamStarts() {
        AnswerService service = service(true);
        for (String bad : new String[] {null, " ", "あ".repeat(NavigationService.MAX_QUERY_LENGTH + 1)}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.validate(bad))
                    .isInstanceOf(ErrorResponseException.class);
        }
    }

    // ------------------------------------------------------------------ 補助

    NavigationResult result(boolean fallback, UUID... sectionIds) {
        List<SectionRef> sections = new ArrayList<>();
        for (UUID id : sectionIds) {
            sections.add(new SectionRef(id, null, null, "抜粋", null, null));
        }
        List<DocumentHit> documents = fallback
                ? List.of()
                : List.of(new DocumentHit(
                        document, "REG-GA-001", "慶弔見舞金規程", "v2.1", LocalDate.of(2026, 4, 1), sections, 1.0, false));
        return new NavigationResult(
                null,
                new QueryInfo("q", "q", List.of(), List.of()),
                List.of(),
                documents,
                List.of(),
                List.of(),
                List.of(),
                fallback);
    }

    NavigationService.Navigation navigated(NavigationResult result) {
        return new NavigationService.Navigation(result, List.of());
    }

    NavigationResult withProgramBasis(NavigationResult result, UUID basisSection) {
        ProgramHit program = new ProgramHit(
                UUID.randomUUID(),
                "PRG-001",
                "慶弔見舞金",
                null,
                null,
                null,
                List.of(new BasisRef(document, "慶弔見舞金規程", "v2.1", basisSection, "第2条")),
                1.0,
                List.of());
        return new NavigationResult(
                result.searchLogId(),
                result.query(),
                List.of(program),
                result.documents(),
                result.faqs(),
                result.contacts(),
                result.relatedPrograms(),
                result.fallback());
    }

    PassageRow row(UUID section, String visibility, String articleNo, String heading, String content) {
        return new PassageRow(
                section, document, "慶弔見舞金規程", "v2.1", visibility, articleNo, heading, content, null, null);
    }

    record TestUser() implements AuthenticatedUser {
        @Override
        public UUID id() {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }

        @Override
        public UUID departmentId() {
            return null;
        }

        @Override
        public String role() {
            return "EMPLOYEE";
        }
    }

    static class RecordingSink implements AnswerService.AnswerSink {
        final List<String> events = new ArrayList<>();
        AssistantAnswer answer;
        String reason;

        @Override
        public void results(NavigationResult result) {
            events.add("results");
        }

        @Override
        public void answer(AssistantAnswer answer) {
            events.add("answer");
            this.answer = answer;
        }

        @Override
        public void unanswerable(AssistantUnanswerable unanswerable) {
            events.add("unanswerable");
            this.reason = unanswerable.reason();
        }
    }
}
