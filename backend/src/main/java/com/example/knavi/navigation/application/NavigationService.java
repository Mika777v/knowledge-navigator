package com.example.knavi.navigation.application;

import com.example.knavi.insight.SearchLogRecorder;
import com.example.knavi.insight.SearchLogRecorder.SearchLogEntry;
import com.example.knavi.insight.SearchLogRecorder.TopHit;
import com.example.knavi.navigation.application.NavigationResult.BasisRef;
import com.example.knavi.navigation.application.NavigationResult.ContactHit;
import com.example.knavi.navigation.application.NavigationResult.DocumentHit;
import com.example.knavi.navigation.application.NavigationResult.FaqHit;
import com.example.knavi.navigation.application.NavigationResult.LifeEventRef;
import com.example.knavi.navigation.application.NavigationResult.ProgramHit;
import com.example.knavi.navigation.application.NavigationResult.QueryInfo;
import com.example.knavi.navigation.application.NavigationResult.RelatedProgram;
import com.example.knavi.navigation.application.NavigationResult.SectionRef;
import com.example.knavi.navigation.application.SearchPort.SearchCandidates;
import com.example.knavi.navigation.domain.QueryAnalyzer;
import com.example.knavi.navigation.domain.QueryAnalyzer.AnalyzedQuery;
import com.example.knavi.navigation.infrastructure.NavigationQueries;
import com.example.knavi.navigation.infrastructure.NavigationQueries.BasisRow;
import com.example.knavi.navigation.infrastructure.NavigationQueries.ContactRow;
import com.example.knavi.navigation.infrastructure.NavigationQueries.FaqRow;
import com.example.knavi.navigation.infrastructure.NavigationQueries.KeywordMatch;
import com.example.knavi.navigation.infrastructure.NavigationQueries.LifeEventProgramRow;
import com.example.knavi.navigation.infrastructure.NavigationQueries.ProgramContactRow;
import com.example.knavi.navigation.infrastructure.NavigationQueries.ProgramRow;
import com.example.knavi.navigation.infrastructure.NavigationQueries.SectionRow;
import com.example.knavi.shared.security.AccessScope;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ナビゲーション検索（docs/ARCHITECTURE.md §4.2）。質問から、使える制度・根拠の規程・FAQ・問い合わせ先をまとめて返す。
 *
 * <ol>
 *   <li>質問の正規化と語の抽出（{@link QueryAnalyzer}）
 *   <li>辞書の照合: 質問に含まれるキーワード（最長一致）から、制度・きっかけ・窓口に直接案内する
 *   <li>全文検索（{@link SearchPort}）: 制度・条文・FAQ
 *   <li>統合とスコアリング（{@link NavigationWeights}）。上位の制度の根拠文書・FAQ も結果に加える
 *   <li>窓口の導出: 上位の制度の窓口 → 辞書で見つかった窓口 → FAQ の窓口。1件もなければ総合窓口（必ず1件以上返す）
 *   <li>検索ログの記録（ユーザーは仮名化。記録に失敗しても結果は返す）
 * </ol>
 *
 * <p>制度・文書・FAQ はすべて閲覧権限（SEARCH）で絞り込んでから並べる。見えないものは件数・窓口・関連制度にも影響させない。
 */
@Service
@Transactional(readOnly = true)
public class NavigationService {

    public static final int MAX_QUERY_LENGTH = 200;
    static final int PROGRAM_LIMIT = 10;
    static final int DOCUMENT_LIMIT = 5;
    static final int SECTIONS_PER_DOCUMENT = 3;
    static final int FAQ_LIMIT = 5;
    static final int CONTACT_LIMIT = 4;
    static final int RELATED_LIMIT = 5;
    /** 根拠文書・FAQ・窓口を結果に加える対象にする上位の制度の数 */
    static final int TOP_PROGRAMS_FOR_LINKS = 3;

    private static final int CANDIDATE_LIMIT = 50;
    private static final int SNIPPET_LENGTH = 100;

    private static final Map<String, String> CONTACT_ROLE_LABELS =
            Map.of("APPLY", "申請窓口", "INQUIRY", "問い合わせ先", "PAYMENT", "支給窓口");

    private final NavigationQueries queries;
    private final SearchPort searchPort;
    private final NavigationWeights weights;
    private final SearchLogRecorder searchLogs;

    NavigationService(
            NavigationQueries queries, SearchPort searchPort, NavigationWeights weights, SearchLogRecorder searchLogs) {
        this.queries = queries;
        this.searchPort = searchPort;
        this.weights = weights;
        this.searchLogs = searchLogs;
    }

    public NavigationResult navigate(String q, AuthenticatedUser user) {
        if (q == null || q.isBlank()) {
            throw ApiErrors.badRequest("知りたいことを入力してください。");
        }
        if (q.length() > MAX_QUERY_LENGTH) {
            throw ApiErrors.badRequest("質問は" + MAX_QUERY_LENGTH + "文字以内で入力してください。");
        }
        long started = System.nanoTime();
        AccessScope scope = AccessScope.of(user);
        AnalyzedQuery query = QueryAnalyzer.analyze(q);

        // 2. 辞書の照合
        List<KeywordMatch> keywords =
                longestMatches(queries.keywordsIn(query.normalized().replace(" ", "")));
        query = query.withTerms(
                keywords.stream().map(KeywordMatch::term).distinct().toList());

        Scores programs = new Scores();
        Scores documents = new Scores();
        Scores faqs = new Scores();
        Map<UUID, String> dictionaryContacts = new LinkedHashMap<>();
        Map<UUID, Double> lifeEventWeights = new LinkedHashMap<>();
        for (KeywordMatch k : keywords) {
            double score = weights.dictionary() * k.weight();
            switch (k.targetType()) {
                case "PROGRAM" -> programs.add(k.targetId(), score, "KEYWORD:" + k.term());
                case "DOCUMENT" -> documents.add(k.targetId(), score, "KEYWORD:" + k.term());
                case "FAQ" -> faqs.add(k.targetId(), score, "KEYWORD:" + k.term());
                case "CONTACT" -> dictionaryContacts.putIfAbsent(k.targetId(), k.term());
                case "LIFE_EVENT" -> lifeEventWeights.merge(k.targetId(), k.weight(), Math::max);
                default -> {}
            }
        }
        Map<String, LifeEventRef> matchedLifeEvents = new LinkedHashMap<>();
        List<UUID> relatedCandidates = new ArrayList<>();
        for (LifeEventProgramRow row : queries.lifeEventPrograms(lifeEventWeights.keySet())) {
            matchedLifeEvents.putIfAbsent(row.code(), new LifeEventRef(row.code(), row.name()));
            if ("PRIMARY".equals(row.relation())) {
                double score = weights.dictionary() * lifeEventWeights.get(row.lifeEventId());
                programs.add(row.programId(), score, "LIFE_EVENT:" + row.name());
            } else {
                relatedCandidates.add(row.programId());
            }
        }

        // 3. 全文検索
        SearchCandidates candidates = searchPort.search(query.terms(), scope, CANDIDATE_LIMIT);
        candidates.programs().forEach(h -> programs.add(h.id(), h.score(), textReasons(h.matchedTerms())));
        candidates.faqs().forEach(h -> faqs.add(h.id(), h.score(), textReasons(h.matchedTerms())));
        Map<UUID, List<SearchPort.SectionHit>> sectionsByDocument = new HashMap<>();
        for (SearchPort.SectionHit h : candidates.sections()) {
            sectionsByDocument
                    .computeIfAbsent(h.documentId(), k -> new ArrayList<>())
                    .add(h);
            documents.add(h.documentId(), 0, textReasons(h.matchedTerms()));
            documents.raiseTo(h.documentId(), h.score());
        }

        // 4. 制度（閲覧できるものだけ）を並べ、上位の制度の根拠・FAQ を加える
        List<ProgramRow> visiblePrograms =
                programs.rank(queries.programs(programs.ids(), scope), ProgramRow::id, ProgramRow::code);
        double topScore = visiblePrograms.isEmpty()
                ? 0
                : programs.score(visiblePrograms.getFirst().id());
        List<ProgramRow> shownPrograms = visiblePrograms.stream()
                .filter(p -> programs.score(p.id()) >= topScore * weights.minRelativeScore())
                .limit(PROGRAM_LIMIT)
                .toList();
        List<UUID> shownProgramIds = shownPrograms.stream().map(ProgramRow::id).toList();
        List<UUID> topProgramIds =
                shownProgramIds.stream().limit(TOP_PROGRAMS_FOR_LINKS).toList();

        Map<UUID, List<BasisRow>> basisByProgram =
                queries.basis(shownProgramIds, scope).stream().collect(Collectors.groupingBy(BasisRow::programId));
        Map<UUID, List<UUID>> basisSections = new LinkedHashMap<>();
        for (UUID programId : topProgramIds) {
            for (BasisRow b : basisByProgram.getOrDefault(programId, List.of())) {
                documents.add(
                        b.documentId(), programs.score(programId) * weights.basis(), "BASIS:" + b.documentTitle());
                if (b.sectionId() != null) {
                    basisSections
                            .computeIfAbsent(b.documentId(), k -> new ArrayList<>())
                            .add(b.sectionId());
                }
            }
        }
        for (var link : queries.faqsLinkedTo(topProgramIds)) {
            faqs.add(link.faqId(), programs.score(link.programId()) * weights.linkedFaq(), "PROGRAM_FAQ");
        }

        List<ProgramHit> programHits = shownPrograms.stream()
                .map(p -> new ProgramHit(
                        p.id(),
                        p.code(),
                        p.name(),
                        p.summary(),
                        p.benefit(),
                        p.deadlineNote(),
                        basisByProgram.getOrDefault(p.id(), List.of()).stream()
                                .map(b -> new BasisRef(
                                        b.documentId(),
                                        b.documentTitle(),
                                        b.versionLabel(),
                                        b.sectionId(),
                                        b.articleNo()))
                                .toList(),
                        round(programs.score(p.id())),
                        programs.reasons(p.id())))
                .toList();

        List<DocumentHit> documentHits =
                documentHits(documents, sectionsByDocument, basisSections, query.terms(), scope);

        List<FaqRow> shownFaqs = faqs.rank(queries.faqs(faqs.ids(), scope), FaqRow::id, FaqRow::code).stream()
                .limit(FAQ_LIMIT)
                .toList();
        List<FaqHit> faqHits = shownFaqs.stream()
                .map(f -> new FaqHit(f.id(), f.code(), f.question(), round(faqs.score(f.id()))))
                .toList();

        // 5. 窓口の導出
        boolean fallback = programHits.isEmpty() && documentHits.isEmpty() && faqHits.isEmpty();
        List<ContactHit> contacts = contacts(shownPrograms, topProgramIds, dictionaryContacts, shownFaqs, fallback);

        Set<UUID> shown = Set.copyOf(shownProgramIds);
        List<UUID> relatedIds = relatedCandidates.stream()
                .distinct()
                .filter(id -> !shown.contains(id))
                .toList();
        Map<UUID, ProgramRow> relatedRows = queries.programs(relatedIds, scope).stream()
                .collect(Collectors.toMap(ProgramRow::id, Function.identity()));
        List<RelatedProgram> related = relatedIds.stream()
                .map(relatedRows::get)
                .filter(java.util.Objects::nonNull)
                .limit(RELATED_LIMIT)
                .map(p -> new RelatedProgram(p.id(), p.code(), p.name()))
                .toList();

        UUID searchLogId = searchLogs
                .record(new SearchLogEntry(
                        user.id(),
                        user.departmentId(),
                        query.raw(),
                        query.cleaned(),
                        Map.of(
                                "programs", programHits.size(),
                                "documents", documentHits.size(),
                                "faqs", faqHits.size(),
                                "contacts", contacts.size()),
                        topHits(programHits, documentHits, faqHits),
                        (System.nanoTime() - started) / 1_000_000))
                .orElse(null);

        return new NavigationResult(
                searchLogId,
                new QueryInfo(query.raw(), query.cleaned(), query.terms(), List.copyOf(matchedLifeEvents.values())),
                programHits,
                documentHits,
                faqHits,
                contacts,
                related,
                fallback);
    }

    /** 検索ログに残す上位の結果（ブロックごとに上位3件） */
    private static List<TopHit> topHits(List<ProgramHit> programs, List<DocumentHit> documents, List<FaqHit> faqs) {
        List<TopHit> hits = new ArrayList<>();
        programs.stream().limit(3).forEach(p -> hits.add(new TopHit("PROGRAM", p.code(), p.score())));
        documents.stream().limit(3).forEach(d -> hits.add(new TopHit("DOCUMENT", d.code(), d.score())));
        faqs.stream().limit(3).forEach(f -> hits.add(new TopHit("FAQ", f.code(), f.score())));
        return hits;
    }

    private List<DocumentHit> documentHits(
            Scores documents,
            Map<UUID, List<SearchPort.SectionHit>> sectionsByDocument,
            Map<UUID, List<UUID>> basisSections,
            List<String> terms,
            AccessScope scope) {
        var shown = documents.rank(queries.documents(documents.ids(), scope), d -> d.id(), d -> d.code()).stream()
                .limit(DOCUMENT_LIMIT)
                .toList();
        // 文書ごとに、根拠の条 → 語が一致した条（スコア順）の順に選ぶ
        Map<UUID, List<UUID>> chosen = new LinkedHashMap<>();
        for (var d : shown) {
            Set<UUID> ids = new LinkedHashSet<>(basisSections.getOrDefault(d.id(), List.of()));
            sectionsByDocument.getOrDefault(d.id(), List.of()).stream()
                    .sorted(Comparator.comparingDouble(SearchPort.SectionHit::score)
                            .reversed())
                    .forEach(h -> ids.add(h.sectionId()));
            chosen.put(d.id(), ids.stream().limit(SECTIONS_PER_DOCUMENT).toList());
        }
        Map<UUID, SectionRow> sections =
                queries.sections(chosen.values().stream().flatMap(List::stream).toList()).stream()
                        .collect(Collectors.toMap(SectionRow::id, Function.identity()));
        return shown.stream()
                .map(d -> new DocumentHit(
                        d.id(),
                        d.code(),
                        d.title(),
                        d.versionLabel(),
                        d.effectiveFrom(),
                        chosen.get(d.id()).stream()
                                .map(sections::get)
                                .filter(java.util.Objects::nonNull)
                                // 公開中の版の条だけを出す（根拠が旧版の条を指している場合は除く）
                                .filter(s -> s.documentId().equals(d.id()))
                                .map(s ->
                                        new SectionRef(s.id(), s.articleNo(), s.heading(), snippet(s.content(), terms)))
                                .toList(),
                        round(documents.score(d.id()))))
                .toList();
    }

    private List<ContactHit> contacts(
            List<ProgramRow> shownPrograms,
            List<UUID> topProgramIds,
            Map<UUID, String> dictionaryContacts,
            List<FaqRow> shownFaqs,
            boolean fallback) {
        Map<String, ContactHit> result = new LinkedHashMap<>();
        Map<UUID, String> programNames =
                shownPrograms.stream().collect(Collectors.toMap(ProgramRow::id, ProgramRow::name));
        Map<UUID, List<ProgramContactRow>> byProgram = queries.programContacts(topProgramIds).stream()
                .collect(Collectors.groupingBy(ProgramContactRow::programId));
        for (UUID programId : topProgramIds) {
            for (ProgramContactRow pc : byProgram.getOrDefault(programId, List.of())) {
                String reason = programNames.get(programId) + "の" + CONTACT_ROLE_LABELS.getOrDefault(pc.role(), "窓口");
                result.putIfAbsent(pc.contact().code(), contactHit(pc.contact(), reason));
            }
        }
        Map<UUID, ContactRow> others = new HashMap<>();
        List<UUID> otherIds = new ArrayList<>(dictionaryContacts.keySet());
        shownFaqs.stream()
                .map(FaqRow::contactId)
                .filter(java.util.Objects::nonNull)
                .forEach(otherIds::add);
        queries.contacts(otherIds).forEach(c -> others.put(c.id(), c));
        dictionaryContacts.forEach((id, term) -> {
            ContactRow c = others.get(id);
            if (c != null) {
                result.putIfAbsent(c.code(), contactHit(c, "「" + term + "」の相談窓口"));
            }
        });
        for (FaqRow f : shownFaqs) {
            ContactRow c = f.contactId() == null ? null : others.get(f.contactId());
            if (c != null) {
                result.putIfAbsent(c.code(), contactHit(c, "FAQ「" + f.question() + "」の問い合わせ先"));
            }
        }
        if (result.isEmpty()) {
            queries.defaultContact()
                    .ifPresent(c -> result.put(
                            c.code(), contactHit(c, fallback ? "該当する案内が見つからないときの総合窓口" : "どこに聞けばよいか迷ったときの総合窓口")));
        }
        return result.values().stream().limit(CONTACT_LIMIT).toList();
    }

    private static ContactHit contactHit(ContactRow c, String reason) {
        return new ContactHit(
                c.id(),
                c.code(),
                c.name(),
                c.department(),
                c.phoneExt(),
                c.email(),
                c.chatUrl(),
                c.officeHours(),
                reason);
    }

    /** 辞書の一致のうち、より長い一致に含まれるもの（「結婚式」に対する「結婚」など）を除く */
    static List<KeywordMatch> longestMatches(List<KeywordMatch> matches) {
        return matches.stream()
                .filter(m -> matches.stream()
                        .noneMatch(o -> o.term().length() > m.term().length()
                                && o.term().contains(m.term())))
                .toList();
    }

    /** 最初に一致した語の前後を切り出す（改行は空白にする） */
    static String snippet(String content, List<String> terms) {
        String text = content.replaceAll("\\s+", " ").strip();
        String lower = text.toLowerCase(Locale.ROOT);
        int at = terms.stream()
                .mapToInt(lower::indexOf)
                .filter(i -> i >= 0)
                .min()
                .orElse(0);
        int start = Math.max(0, at - 30);
        int end = Math.min(text.length(), start + SNIPPET_LENGTH);
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }

    private static List<String> textReasons(List<String> terms) {
        return terms.stream().map(t -> "TEXT:" + t).toList();
    }

    private static double round(double score) {
        return Math.round(score * 100) / 100.0;
    }

    /** 候補ごとのスコアと理由を足し合わせる */
    private static final class Scores {

        private final Map<UUID, Double> scores = new LinkedHashMap<>();
        private final Map<UUID, Set<String>> reasons = new HashMap<>();

        void add(UUID id, double score, String reason) {
            add(id, score, List.of(reason));
        }

        void add(UUID id, double score, List<String> newReasons) {
            scores.merge(id, score, Double::sum);
            reasons.computeIfAbsent(id, k -> new LinkedHashSet<>()).addAll(newReasons);
        }

        /** 文書は条ごとの一致を足さず、最も強く一致した条のスコアを使う（条の多い文書が有利にならないように） */
        void raiseTo(UUID id, double score) {
            scores.merge(id, score, Math::max);
        }

        double score(UUID id) {
            return scores.getOrDefault(id, 0.0);
        }

        List<String> reasons(UUID id) {
            return List.copyOf(reasons.getOrDefault(id, Set.of()));
        }

        Set<UUID> ids() {
            return scores.keySet();
        }

        /** 閲覧できる行だけを、スコアの高い順（同点はコード順）に並べる */
        <T> List<T> rank(List<T> rows, Function<T, UUID> id, Function<T, String> code) {
            return rows.stream()
                    .filter(r -> score(id.apply(r)) > 0)
                    .sorted(Comparator.comparingDouble((T r) -> score(id.apply(r)))
                            .reversed()
                            .thenComparing(code))
                    .toList();
        }
    }
}
