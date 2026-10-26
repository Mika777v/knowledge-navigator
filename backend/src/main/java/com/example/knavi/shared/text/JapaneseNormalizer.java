package com.example.knavi.shared.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 形態素解析による表記ゆれ・活用のちがいの吸収（ADR-0011）。文書の検索列 search_norm と、質問の語の両方に使う。
 *
 * <p>語は Sudachi の正規化形にそろえる（振り込まれる → 振り込む、引越 → 引っ越し、子ども → 子供）。
 * 辞書がないとき（{@link #available()} が false）は、検索列は空、質問の語も足さない。検索は今の方法（search_text）だけで動く。
 *
 * <p>{@link TextNormalizer} と同じく、エンティティからも呼べるよう static にしている。
 * 解析器は起動時に {@code SudachiSetup} が入れる。
 */
public final class JapaneseNormalizer {

    /** 形態素解析の結果の1語 */
    public record Token(String surface, String normalizedForm, String pos, String posDetail) {}

    /** 形態素解析の実装（テストでは差し替える） */
    public interface Analyzer {

        List<Token> tokenize(String text);

        /** 検索列を作り直す必要があるかの判定に使う（辞書の版） */
        String dictionaryVersion();
    }

    /** 質問の語にしない動詞・形容詞（どの質問にも出てきて手がかりにならない） */
    private static final Set<String> GENERIC_PREDICATES =
            Set.of("する", "ある", "いる", "なる", "できる", "言う", "思う", "貰う", "呉れる", "見る", "欲しい", "良い", "無い", "為る", "使う", "行く");

    /** 単独では語にしない名詞（多くの制度・規程の名前に付き、それだけでは手がかりにならない。まとまりの中では使う） */
    private static final Set<String> GENERIC_NOUNS =
            Set.of("手当", "制度", "規程", "規定", "方法", "場合", "手続", "手続き", "申請", "会社", "社内", "休み");

    /** 名詞のまとまりに入れる品詞（接頭辞「お」などは入れない） */
    private static final Set<String> NOUN_LIKE = Set.of("名詞", "接尾辞");

    private static final Set<String> PREDICATES = Set.of("動詞", "形容詞", "形状詞");

    private static final Set<String> SKIPPED = Set.of("補助記号", "空白", "記号");

    private static volatile Analyzer analyzer;

    private JapaneseNormalizer() {}

    /** 解析器を入れる（null で外す） */
    public static void install(Analyzer value) {
        analyzer = value;
    }

    /** 今の解析器（テストで元に戻すため） */
    static Analyzer installed() {
        return analyzer;
    }

    public static boolean available() {
        return analyzer != null;
    }

    /** @return 辞書の版（辞書がなければ空） */
    public static String dictionaryVersion() {
        Analyzer a = analyzer;
        return a == null ? "" : a.dictionaryVersion();
    }

    /**
     * 検索列（search_norm）の値。記号・空白を除いた各語の正規化形を空白でつなぐ（例: 「慶弔見舞金はいつ振り込まれますか」→
     * 「慶弔見舞い金 は いつ 振り込む れる ます か」）。辞書がなければ空。
     */
    public static String normalize(String text) {
        Analyzer a = analyzer;
        if (a == null || text == null || text.isBlank()) {
            return "";
        }
        List<String> forms = new ArrayList<>();
        for (Token t : a.tokenize(text)) {
            if (!SKIPPED.contains(t.pos())) {
                forms.add(t.normalizedForm());
            }
        }
        return TextNormalizer.normalize(String.join(" ", forms));
    }

    /**
     * 質問から検索に使う語（正規化形）。辞書がなければ空。
     *
     * <ul>
     *   <li>名詞が続くところは、空白でつないだまとまり（満員 電車）と、2文字以上の各名詞（「手当」など、それだけでは
     *       手がかりにならない名詞は除く）
     *   <li>動詞・形容詞は正規化形（振り込まれる → 振り込む）。どの質問にも出てくる語（する・ある など）は除く
     * </ul>
     */
    public static List<String> contentTerms(String text) {
        Analyzer a = analyzer;
        if (a == null || text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        List<String> run = new ArrayList<>();
        for (Token t : a.tokenize(text)) {
            String form = TextNormalizer.normalize(t.normalizedForm());
            if (NOUN_LIKE.contains(t.pos()) && !"数詞".equals(t.posDetail())) {
                run.add(form);
                continue;
            }
            addRun(run, terms);
            if (PREDICATES.contains(t.pos()) && form.length() >= 2 && !GENERIC_PREDICATES.contains(form)) {
                terms.add(form);
            }
        }
        addRun(run, terms);
        return List.copyOf(terms);
    }

    private static void addRun(List<String> run, Set<String> terms) {
        // 接尾辞だけのまとまり（「中」など）は使わない
        if (run.size() > 1) {
            terms.add(String.join(" ", run));
        }
        run.stream().filter(n -> n.length() >= 2 && !GENERIC_NOUNS.contains(n)).forEach(terms::add);
        run.clear();
    }
}
