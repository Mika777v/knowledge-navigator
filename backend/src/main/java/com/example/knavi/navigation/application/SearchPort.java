package com.example.knavi.navigation.application;

import com.example.knavi.shared.security.AccessScope;
import java.util.List;
import java.util.UUID;

/**
 * 全文検索の差し替え口（docs/ARCHITECTURE.md §4.5）。
 * MVP は pg_bigm（PgBigmSearchAdapter）。Phase4 でベクトル検索を合わせた実装に差し替える。
 *
 * <p>実装は閲覧権限（{@code AccessPolicy.readPredicate(..., SEARCH)}）で絞り込んだ候補だけを返すこと。
 */
public interface SearchPort {

    SearchCandidates search(List<Term> terms, AccessScope scope, int limit);

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
     */
    record Hit(UUID id, double score, List<String> matchedTerms) {}

    /** 条文の一致。文書ごとにまとめて表示する */
    record SectionHit(UUID sectionId, UUID documentId, double score, List<String> matchedTerms) {}

    record SearchCandidates(List<Hit> programs, List<SectionHit> sections, List<Hit> faqs) {

        public static SearchCandidates empty() {
            return new SearchCandidates(List.of(), List.of(), List.of());
        }
    }
}
