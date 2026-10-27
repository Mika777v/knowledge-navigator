package com.example.knavi.navigation.application;

import com.example.knavi.shared.security.AccessScope;
import java.util.List;
import java.util.UUID;

/**
 * 全文検索の差し替え口（docs/ARCHITECTURE.md §4.5）。
 * キーワード検索は pg_bigm（PgBigmSearchAdapter）。Phase4 からは、ベクトル検索を RRF で合わせる HybridSearchAdapter を使う（ADR-0007）。
 *
 * <p>実装は閲覧権限（{@code AccessPolicy.readPredicate(..., SEARCH)}）で絞り込んだ候補だけを返すこと。
 */
public interface SearchPort {

    /**
     * @param question 利用者が入力した質問そのもの（意味検索で、質問をベクトルにするのに使う）
     * @param terms 質問から取り出した語（キーワード検索に使う）
     */
    SearchCandidates search(String question, List<Term> terms, AccessScope scope, int limit);

    /**
     * 検索する語。
     *
     * @param weight 一致したときのスコアに掛ける（質問にそのまま含まれる語は 1.0、類義語で広げた語はそれより小さい）
     */
    record Term(String text, double weight) {

        public static Term of(String text) {
            return new Term(text, 1.0);
        }
    }

    /**
     * @param score 重み付きの一致度（大きいほど上位）
     * @param matchedTerms 一致した語（結果の理由の表示に使う）
     * @param semantic 意味検索（ベクトル検索）でも近かった
     */
    record Hit(UUID id, double score, List<String> matchedTerms, boolean semantic) {

        public Hit(UUID id, double score, List<String> matchedTerms) {
            this(id, score, matchedTerms, false);
        }
    }

    /** 条文の一致。文書ごとにまとめて表示する */
    record SectionHit(UUID sectionId, UUID documentId, double score, List<String> matchedTerms, boolean semantic) {

        public SectionHit(UUID sectionId, UUID documentId, double score, List<String> matchedTerms) {
            this(sectionId, documentId, score, matchedTerms, false);
        }
    }

    /** @param hybrid ベクトル検索も使った（検索ログの mode に残す。オフ・ai-service に繋がらないときは false） */
    record SearchCandidates(List<Hit> programs, List<SectionHit> sections, List<Hit> faqs, boolean hybrid) {

        public SearchCandidates(List<Hit> programs, List<SectionHit> sections, List<Hit> faqs) {
            this(programs, sections, faqs, false);
        }

        public static SearchCandidates empty() {
            return new SearchCandidates(List.of(), List.of(), List.of());
        }
    }
}
