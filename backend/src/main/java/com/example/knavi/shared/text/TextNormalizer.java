package com.example.knavi.shared.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 検索用のテキスト正規化。文書の search_text と検索クエリの両方に同じ処理をかける。
 *
 * <ul>
 *   <li>NFKC 正規化（全角英数字・記号を半角に、半角カナを全角に）
 *   <li>英字を小文字に
 *   <li>連続する空白・改行を1つの空白に
 * </ul>
 *
 * <p>形態素解析による表記ゆれの吸収は Phase3（#E12-T1）で追加する。
 */
public final class TextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u3000]+");

    private TextNormalizer() {}

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return WHITESPACE.matcher(nfkc).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
