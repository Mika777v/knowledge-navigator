package com.example.knavi.navigation.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 類義語辞書による質問の広げ方（docs/ARCHITECTURE.md §4.2 の 3、E12-T2）。
 *
 * <p>質問に含まれる類義語辞書の語（{@link Pair#matched()}）ごとに、同じグループのほかの語（{@link Pair#synonym()}）でも探す。
 *
 * <ul>
 *   <li>辞書の照合には、一致した語を類義語に置き換えた質問を使う（育休明けに戻れる → 復職に戻れる）
 *   <li>全文検索には、類義語そのものと、質問の語の一部を置き換えた語を足す（マイカー通勤 → 車・車通勤）
 * </ul>
 *
 * <p>広げた語での一致は、質問にそのまま含まれる語より弱くする（重みは呼び出し側で掛ける）。
 */
public final class SynonymExpansion {

    /** 辞書の照合に使う、言い換えた質問の上限 */
    public static final int MAX_VARIANTS = 6;
    /** 全文検索に足す語の上限 */
    public static final int MAX_EXTRA_TERMS = 6;

    /**
     * @param matched 質問に含まれていた語（正規化済み）
     * @param synonym 同じグループのほかの語（正規化済み）
     */
    public record Pair(String matched, String synonym) {}

    private SynonymExpansion() {}

    /** より長い一致に含まれるもの（「育休明け」に対する「育休」など）を除く */
    public static List<Pair> longest(List<Pair> pairs) {
        return pairs.stream()
                .filter(p -> pairs.stream()
                        .noneMatch(o -> compact(o.matched()).length()
                                        > compact(p.matched()).length()
                                && compact(o.matched()).contains(compact(p.matched()))))
                .toList();
    }

    /**
     * 一致した語を類義語に置き換えた質問（辞書の照合用）。
     *
     * @param compactQuery 正規化して空白を除いた質問
     */
    public static List<String> variants(String compactQuery, List<Pair> pairs) {
        Set<String> result = new LinkedHashSet<>();
        for (Pair p : pairs) {
            if (result.size() >= MAX_VARIANTS) {
                break;
            }
            String variant = compactQuery.replace(compact(p.matched()), compact(p.synonym()));
            if (!variant.equals(compactQuery)) {
                result.add(variant);
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 全文検索に足す語。質問の語に一致した語が含まれていれば、その部分を置き換えた語を先にする（より具体的なため）。
     *
     * @param terms 質問から取り出した語（これらは足さない）
     */
    public static List<String> extraTerms(List<String> terms, List<Pair> pairs) {
        Set<String> result = new LinkedHashSet<>();
        for (Pair p : pairs) {
            for (String term : terms) {
                if (term.contains(p.matched()) && !term.equals(p.matched())) {
                    result.add(term.replace(p.matched(), p.synonym()));
                }
            }
        }
        pairs.forEach(p -> result.add(p.synonym()));
        result.removeAll(terms);
        return result.stream().limit(MAX_EXTRA_TERMS).toList();
    }

    private static String compact(String term) {
        return term.replace(" ", "");
    }
}
