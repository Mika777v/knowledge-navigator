package com.example.knavi.content.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown の本文を条・見出し単位のセクションに分割する。セクションは検索・引用・Embedding の最小単位になる。
 *
 * <p>規程の書式（data/README.md）:
 *
 * <ul>
 *   <li>{@code ## 第N章 見出し} … 章（セクションにはせず、path に使う）
 *   <li>{@code ### 第N条（見出し）} … 条。1条 = 1セクション
 *   <li>上記以外の {@code ##} / {@code ###} 見出し（附則・手順書の見出しなど） … 1見出し = 1セクション
 * </ul>
 *
 * <p>最初の見出しより前の文章のうち、引用（{@code >} で始まる注記）以外があれば「前文」セクションにする。
 */
public final class MarkdownSectionSplitter {

    private static final Pattern HEADING = Pattern.compile("^(#{2,3})\\s+(.+?)\\s*$");
    private static final Pattern CHAPTER = Pattern.compile("^第(\\d+)章\\s*(.*)$");
    private static final Pattern ARTICLE = Pattern.compile("^第(\\d+)条\\s*[（(](.+)[）)]$");
    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\R.*?\\R---\\R", Pattern.DOTALL);

    public List<SectionDraft> split(String markdown) {
        String body = FRONT_MATTER.matcher(markdown).replaceFirst("");
        List<SectionDraft> sections = new ArrayList<>();
        Integer chapter = null;
        Pending current = new Pending(null, null, "前文", true);

        for (String line : body.split("\\R", -1)) {
            Matcher heading = HEADING.matcher(line);
            if (!heading.matches()) {
                current.append(line);
                continue;
            }
            // 全角数字の見出し（第５条 など）も扱えるように見出しだけ NFKC 正規化する
            String text = Normalizer.normalize(heading.group(2), Normalizer.Form.NFKC);
            Matcher chapterMatch = CHAPTER.matcher(text);
            Matcher articleMatch = ARTICLE.matcher(text);

            current.flushTo(sections);
            if (chapterMatch.matches()) {
                chapter = Integer.valueOf(chapterMatch.group(1));
                current = Pending.skip();
            } else if (articleMatch.matches()) {
                String number = articleMatch.group(1);
                String path = chapter == null ? number : chapter + "/" + number;
                current = new Pending(
                        path, "第" + number + "条", articleMatch.group(2).strip(), false);
            } else {
                // 附則など章の外の見出しは章の区切りとみなす
                if (heading.group(1).length() == 2) {
                    chapter = null;
                }
                current = new Pending(null, null, text.strip(), false);
            }
        }
        current.flushTo(sections);
        return List.copyOf(sections);
    }

    /** 組み立て中のセクション */
    private static final class Pending {
        private final String path;
        private final String articleNo;
        private final String heading;
        private final boolean preamble;
        private final boolean skipped;
        private final StringBuilder content = new StringBuilder();

        Pending(String path, String articleNo, String heading, boolean preamble) {
            this(path, articleNo, heading, preamble, false);
        }

        private Pending(String path, String articleNo, String heading, boolean preamble, boolean skipped) {
            this.path = path;
            this.articleNo = articleNo;
            this.heading = heading;
            this.preamble = preamble;
            this.skipped = skipped;
        }

        /** 章見出しの直後など、セクションにしない区間 */
        static Pending skip() {
            return new Pending(null, null, null, false, true);
        }

        void append(String line) {
            // 前文の注記（> で始まる行）はセクションに含めない
            if (preamble && line.startsWith(">")) {
                return;
            }
            content.append(line).append('\n');
        }

        void flushTo(List<SectionDraft> sections) {
            String text = content.toString().strip();
            if (skipped || (preamble && text.isEmpty())) {
                return;
            }
            sections.add(new SectionDraft(sections.size(), path, articleNo, heading, text));
        }
    }
}
