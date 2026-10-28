package com.example.knavi.navigation.application;

import com.example.knavi.intelligence.AnswerGenerator;
import com.example.knavi.intelligence.AnswerGenerator.GeneratedAnswer;
import com.example.knavi.intelligence.AnswerGenerator.Passage;
import com.example.knavi.navigation.application.AssistantAnswer.AssistantEvidence;
import com.example.knavi.navigation.application.NavigationResult.BasisRef;
import com.example.knavi.navigation.application.NavigationResult.DocumentHit;
import com.example.knavi.navigation.application.NavigationResult.ProgramHit;
import com.example.knavi.navigation.application.NavigationResult.SectionRef;
import com.example.knavi.navigation.infrastructure.NavigationQueries;
import com.example.knavi.navigation.infrastructure.NavigationQueries.PassageRow;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 根拠付き回答（E14-T4・ADR-0009・0013）。
 *
 * <ol>
 *   <li>ナビゲーション検索で4ブロックの結果を作り、すぐに返す（窓口は DB の値。ADR-0009）
 *   <li>上位の制度の根拠の条と、上位の文書の条を、検索の順に文脈の候補にする
 *   <li>候補を、検索と同じ権限（SEARCH）で読み直し、生成のプロバイダに送ってよい公開範囲だけに絞る（ADR-0004・0008）
 *   <li>番号を付けて回答文を作らせ、引用された番号を、こちらで持っている条に戻して根拠にする
 * </ol>
 *
 * <p>回答文を作れないときも、4ブロックの結果は返っている。窓口は検索で決まったものを案内する。
 */
@Service
public class AnswerService {

    static final int MAX_TITLE_LENGTH = 200;
    /** ai-service が受け付ける1件の長さ（ai-service の PassageIn） */
    static final int MAX_TEXT_LENGTH = 8000;

    private final NavigationService navigation;
    private final NavigationQueries queries;
    private final AnswerGenerator generator;
    private final AssistantProperties properties;

    AnswerService(
            NavigationService navigation,
            NavigationQueries queries,
            AnswerGenerator generator,
            AssistantProperties properties) {
        this.navigation = navigation;
        this.queries = queries;
        this.generator = generator;
        this.properties = properties;
    }

    /** 回答を返す先（SSE）。results → answer か unanswerable の順に1回ずつ呼ぶ */
    public interface AnswerSink {
        void results(NavigationResult result);

        void answer(AssistantAnswer answer);

        void unanswerable(AssistantUnanswerable unanswerable);
    }

    /** 応答を始める前に、質問の形を確かめる（SSE を始めてからでは 400 を返せないため） */
    public void validate(String question) {
        if (question == null || question.isBlank()) {
            throw ApiErrors.badRequest("知りたいことを入力してください。");
        }
        if (question.length() > NavigationService.MAX_QUERY_LENGTH) {
            throw ApiErrors.badRequest("質問は" + NavigationService.MAX_QUERY_LENGTH + "文字以内で入力してください。");
        }
    }

    public void answer(String question, AuthenticatedUser user, AnswerSink sink) {
        NavigationResult result = navigation.navigate(question, user);
        sink.results(result);
        if (!properties.enabled()) {
            sink.unanswerable(new AssistantUnanswerable("DISABLED"));
            return;
        }
        if (result.fallback()) {
            sink.unanswerable(new AssistantUnanswerable("NO_EVIDENCE"));
            return;
        }
        List<PassageRow> rows = passages(result, user, generator.sendableVisibilities());
        if (rows.isEmpty()) {
            sink.unanswerable(new AssistantUnanswerable("NO_EVIDENCE"));
            return;
        }
        List<Passage> passages = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            PassageRow row = rows.get(i);
            passages.add(new Passage(i + 1, title(row), truncate(row.content(), MAX_TEXT_LENGTH)));
        }
        GeneratedAnswer generated = generator.generate(question, passages);
        if (!generated.answerable()) {
            sink.unanswerable(new AssistantUnanswerable(generated.reason()));
            return;
        }
        // 文脈に無い番号は根拠にしない。根拠が1つも残らなければ回答文を出さない（ARCHITECTURE.md §5.5）
        List<AssistantEvidence> evidence = generated.citations().stream()
                .distinct()
                .filter(ref -> ref >= 1 && ref <= rows.size())
                .map(ref -> evidence(ref, rows.get(ref - 1)))
                .toList();
        if (evidence.isEmpty()
                || generated.answer() == null
                || generated.answer().isBlank()) {
            sink.unanswerable(new AssistantUnanswerable("VERIFICATION_FAILED"));
            return;
        }
        sink.answer(new AssistantAnswer(generated.answer(), evidence, generated.model()));
    }

    /** 文脈にする条。検索の順（上位の制度の根拠 → 上位の文書の条）に、権限と送ってよい公開範囲で絞って並べる */
    List<PassageRow> passages(NavigationResult result, AuthenticatedUser user, Set<String> sendable) {
        if (sendable.isEmpty()) {
            return List.of();
        }
        Set<UUID> candidates = new LinkedHashSet<>();
        result.programs().stream()
                .limit(NavigationService.TOP_PROGRAMS_FOR_LINKS)
                .map(ProgramHit::basis)
                .flatMap(List::stream)
                .map(BasisRef::sectionId)
                .filter(Objects::nonNull)
                .forEach(candidates::add);
        result.documents().stream()
                .map(DocumentHit::sections)
                .flatMap(List::stream)
                .map(SectionRef::id)
                .forEach(candidates::add);
        // 結果に出ていても、読み直して権限を確かめる（結果を作った後に公開範囲が変わることもあるため）
        Map<UUID, PassageRow> readable = queries.passages(candidates, AccessScope.of(user)).stream()
                .collect(Collectors.toMap(PassageRow::sectionId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return candidates.stream()
                .map(readable::get)
                .filter(Objects::nonNull)
                .filter(row -> sendable.contains(row.visibility()))
                .filter(row -> row.content() != null && !row.content().isBlank())
                .limit(properties.maxPassages())
                .toList();
    }

    private static String title(PassageRow row) {
        StringBuilder title = new StringBuilder(row.documentTitle());
        if (row.articleNo() != null && !row.articleNo().isBlank()) {
            title.append(' ').append(row.articleNo());
        }
        if (row.heading() != null && !row.heading().isBlank()) {
            title.append('（').append(row.heading()).append('）');
        }
        return truncate(title.toString(), MAX_TITLE_LENGTH);
    }

    private static AssistantEvidence evidence(int ref, PassageRow row) {
        return new AssistantEvidence(
                ref,
                row.documentId(),
                row.documentTitle(),
                row.versionLabel(),
                row.sectionId(),
                row.articleNo(),
                row.heading(),
                row.pageFrom(),
                row.pageTo());
    }

    private static String truncate(String text, int max) {
        String value = text == null ? "" : text.strip();
        return value.length() <= max ? value : value.substring(0, max);
    }
}
