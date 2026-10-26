package com.example.knavi.navigation.domain;

import com.example.knavi.shared.text.JapaneseNormalizer;
import com.example.knavi.shared.text.TextNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 質問文を検索に使える形にする（docs/ARCHITECTURE.md §4.2 の 1〜2）。
 *
 * <ol>
 *   <li>正規化: {@link TextNormalizer}（NFKC・小文字化・空白の統一）のあと、記号を空白にする
 *   <li>口語の言い回し（「したら」「ありますか」「教えて」など）を取り除く
 *   <li>語の抽出: 漢字・カタカナ・英数字が2文字以上続く部分を語とする。形態素解析の辞書があれば、
 *       名詞のまとまり・動詞・形容詞の正規化形も語にする（{@link AnalyzedQuery#withContentTerms}。ADR-0011）
 * </ol>
 *
 * <p>語の後ろに付いた一般的な語（「方法」「制度」など）は外す。
 *
 * <p>ひらがなを含む言い方（「子どもが生まれる」「亡くなった」など）は、ナビゲーション辞書の照合で拾う。
 */
public final class QueryAnalyzer {

    /** 1つの質問から取り出す語の上限（全文検索の条件が増えすぎないようにする） */
    public static final int MAX_TERMS = 8;

    private static final Pattern SYMBOLS = Pattern.compile("[\\p{Punct}\\p{IsPunctuation}\\p{S}、。・「」『』（）【】〜]+");
    private static final Pattern SPACES = Pattern.compile(" +");
    private static final Pattern TERM = Pattern.compile("[\\p{IsHan}\\p{InKatakana}ー々a-z0-9]{2,}");

    /** 長いものから順に取り除く（「教えてください」を「教えて」より先に） */
    private static final List<String> COLLOQUIAL = List.of(
            "教えてください", "知りたいです", "どうすればいい", "どうしたらいい", "ありますか", "でしょうか", "ください", "教えて", "知りたい", "について", "なにか", "何か",
            "ですか", "したら", "ある");

    /** どの制度・規程にも出てくるため、それだけでは手がかりにならない語 */
    private static final Set<String> GENERIC_TERMS = Set.of("制度", "規程", "規定", "会社", "社内", "方法", "場合", "手続", "手続き");

    private QueryAnalyzer() {}

    public static AnalyzedQuery analyze(String raw) {
        String normalized = SYMBOLS.matcher(TextNormalizer.normalize(raw)).replaceAll(" ");
        String compact = SPACES.matcher(normalized).replaceAll(" ").strip();
        String cleaned = compact;
        for (String phrase : COLLOQUIAL) {
            cleaned = cleaned.replace(phrase, " ");
        }
        cleaned = SPACES.matcher(cleaned).replaceAll(" ").strip();

        Set<String> terms = new LinkedHashSet<>();
        Matcher m = TERM.matcher(cleaned);
        while (m.find() && terms.size() < MAX_TERMS) {
            String term = stripGenericSuffix(m.group());
            if (!GENERIC_TERMS.contains(term)) {
                terms.add(term);
            }
        }

        return new AnalyzedQuery(raw, compact, cleaned, new ArrayList<>(terms));
    }

    /** 「申請方法」「育児制度」のように後ろに付いた一般的な語を外す（残りが2文字以上のときだけ） */
    static String stripGenericSuffix(String term) {
        for (String generic : GENERIC_TERMS) {
            if (term.endsWith(generic) && term.length() - generic.length() >= 2) {
                return term.substring(0, term.length() - generic.length());
            }
        }
        return term;
    }

    /**
     * @param raw 入力そのまま
     * @param normalized 正規化して記号を除いたもの（辞書の照合に使う）
     * @param cleaned さらに口語の言い回しを除いたもの（画面表示・ログ用）
     * @param terms 全文検索に使う語
     */
    public record AnalyzedQuery(String raw, String normalized, String cleaned, List<String> terms) {

        public AnalyzedQuery {
            terms = List.copyOf(terms);
        }

        /**
         * 形態素解析で取り出した語（正規化形。{@link JapaneseNormalizer#contentTerms}）を加える。
         * 辞書の照合で一致した部分（「子どもが生まれる」など）からは取り出さない（意味は辞書の一致で拾えているため、
         * その中の「子供」で関係の薄いものを強めないように）。
         *
         * @param covered 辞書の照合で一致した語
         */
        public AnalyzedQuery withContentTerms(List<String> covered) {
            String rest = cleaned;
            for (String c : covered) {
                rest = rest.replace(c, " ");
            }
            Set<String> merged = new LinkedHashSet<>(terms);
            for (String term : JapaneseNormalizer.contentTerms(rest)) {
                if (merged.size() >= MAX_TERMS) {
                    break;
                }
                if (!GENERIC_TERMS.contains(term)) {
                    merged.add(term);
                }
            }
            return new AnalyzedQuery(raw, normalized, cleaned, List.copyOf(merged));
        }

        /** 辞書の照合で見つかった語を加える（全文検索でも使う） */
        public AnalyzedQuery withTerms(List<String> extra) {
            Set<String> merged = new LinkedHashSet<>(extra);
            merged.addAll(terms);
            return new AnalyzedQuery(
                    raw, normalized, cleaned, merged.stream().limit(MAX_TERMS).toList());
        }
    }
}
