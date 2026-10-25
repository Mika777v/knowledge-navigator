package com.example.knavi.ingestion.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF から取り出した行（{@link PdfText}）を、規程の Markdown（章は {@code ##}、条は {@code ###}）に組み立てる（ADR-0010）。
 * Markdown は管理画面の版の本文になり、既存の {@code MarkdownSectionSplitter} で条に分けられる。あわせて各見出しのページ範囲を返す。
 *
 * <ol>
 *   <li>各ページで繰り返し出る先頭・末尾の行（ヘッダー・フッター）と、ページ番号だけの行を除く
 *   <li>行頭の「第N章 …」「第N条（見出し）」を見出しにする。<b>番号が前の見出しの次の番号のときだけ</b>見出しとみなし、
 *       本文中の参照が折り返しで行頭に来たもの（「第42条）。」「第5条（結婚祝金）による。」など）を見出しにしない
 *   <li>折り返された行をつなぐ。ほぼ全幅まで埋まった行の次の行は、同じ段落の続きとみなす（表の行のように短い行はつながない）
 *   <li>ページをまたぐ表で、次のページの先頭に繰り返される表の見出し行（同じ条の中で既に出た行）を除く
 * </ol>
 */
public final class RegulationPdfParser {

    // 本文の文字は変えずに残すため、全角・半角の数字と空白の両方を受け付ける（\\d・\\s は UNICODE_CHARACTER_CLASS で全角も含む）
    private static final Pattern CHAPTER = Pattern.compile("^第(\\d+)章\\s*(\\S.*)$", Pattern.UNICODE_CHARACTER_CLASS);
    /** 見出しの中のかっこの入れ子（「出生時育児休業（産後パパ育休）」）も許す。誤認は連番の確認で防ぐ */
    private static final Pattern ARTICLE =
            Pattern.compile("^第(\\d+)条\\s*[（(](.+)[）)]$", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern SUPPLEMENTARY = Pattern.compile("^附\\s*則$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ITEM = Pattern.compile("^(\\d+)\\s+(\\S.*)$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern BULLET = Pattern.compile("^[・●○■]\\s*(\\S.*)$", Pattern.UNICODE_CHARACTER_CLASS);
    /** ページ番号だけの行（「- 3 -」「3」「3 / 10」「p.3」など） */
    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "^(?:[-–—]\\s*)?(?:p\\.?\\s*)?\\d+(?:\\s*/\\s*\\d+)?(?:\\s*[-–—])?$", Pattern.CASE_INSENSITIVE);

    /** 見出しとみなす行の長さの上限（本文の1行を見出しと誤認しないため） */
    private static final int MAX_HEADING_LENGTH = 40;
    /** 行の長さが、文書で最も長い行のこの割合以上なら「折り返された行」とみなす */
    private static final double WRAPPED_LINE_RATIO = 0.85;
    /** 文字の少なすぎる PDF は、画像だけ（スキャン）とみなす */
    private static final int MIN_TEXT_LENGTH = 20;

    /**
     * @param heading 見出しの文（前文は null）
     * @param articleNo 条番号（「第5条」。条でない見出しは null）
     * @param pageFrom 見出しのあるページ（1から）
     * @param pageTo その見出しの内容が終わるページ
     */
    public record ParsedSection(String heading, String articleNo, int pageFrom, int pageTo) {}

    /**
     * @param markdown 版の本文にする Markdown
     * @param sections 見出しごとのページ範囲（Markdown の見出しの順。章の見出しは含めない）
     */
    public record ParsedRegulation(String title, int pageCount, String markdown, List<ParsedSection> sections) {}

    public ParsedRegulation parse(PdfText text) {
        List<List<String>> pages = withoutRepeatedLines(text.pages());
        int textLength =
                pages.stream().flatMap(List::stream).mapToInt(String::length).sum();
        if (textLength < MIN_TEXT_LENGTH) {
            throw new PdfExtractionException("PDF から文字を取り出せませんでした。画像だけの PDF（スキャンしたもの）は取り込めません。");
        }
        String title = text.title() != null
                ? text.title()
                : pages.stream().flatMap(List::stream).findFirst().orElse("");
        int wrapWidth = (int) Math.floor(maxLineLength(pages) * WRAPPED_LINE_RATIO);

        var out = new Builder();
        int lastChapter = 0;
        int lastArticle = 0;
        boolean titleSkipped = false;
        for (int p = 0; p < pages.size(); p++) {
            int page = p + 1;
            boolean pageTop = true;
            for (String raw : pages.get(p)) {
                String line = raw.strip();
                boolean repeatedTableHeader = pageTop && page > 1 && out.seenInSection(line);
                pageTop = false;
                if (repeatedTableHeader) {
                    continue;
                }
                // 1ページ目の表題（文書のタイトルと同じ行）は本文に入れない。見出しの形の行は表題とみなさない
                if (!titleSkipped
                        && out.isEmpty()
                        && line.equals(title)
                        && !CHAPTER.matcher(line).matches()
                        && !ARTICLE.matcher(line).matches()) {
                    titleSkipped = true;
                    continue;
                }
                Matcher chapter = CHAPTER.matcher(line);
                Matcher article = ARTICLE.matcher(line);
                if (line.length() <= MAX_HEADING_LENGTH
                        && chapter.matches()
                        && Integer.parseInt(chapter.group(1)) == lastChapter + 1) {
                    lastChapter++;
                    out.chapter("第" + lastChapter + "章 " + chapter.group(2).strip(), page);
                } else if (line.length() <= MAX_HEADING_LENGTH
                        && article.matches()
                        && Integer.parseInt(article.group(1)) == lastArticle + 1) {
                    lastArticle++;
                    out.section(
                            "第" + lastArticle + "条（" + article.group(2).strip() + "）", "第" + lastArticle + "条", page);
                } else if (SUPPLEMENTARY.matcher(line).matches()) {
                    out.supplementary("附則", page);
                } else {
                    Matcher item = ITEM.matcher(line);
                    Matcher bullet = BULLET.matcher(line);
                    if (item.matches()) {
                        out.paragraph(Integer.parseInt(item.group(1)) + ". " + item.group(2), page, wrapWidth);
                    } else if (bullet.matches()) {
                        out.paragraph("- " + bullet.group(1), page, wrapWidth);
                    } else {
                        out.text(line, page, wrapWidth);
                    }
                }
            }
        }
        return new ParsedRegulation(title, pages.size(), out.markdown(), out.sections());
    }

    /** 半分以上のページの先頭（末尾）に同じ形で出る行と、ページ番号だけの末尾の行を除く */
    static List<List<String>> withoutRepeatedLines(List<List<String>> pages) {
        Map<String, Integer> firsts = new HashMap<>();
        Map<String, Integer> lasts = new HashMap<>();
        for (List<String> lines : pages) {
            if (!lines.isEmpty()) {
                firsts.merge(shape(lines.getFirst()), 1, Integer::sum);
                lasts.merge(shape(lines.getLast()), 1, Integer::sum);
            }
        }
        int threshold = Math.max(2, (pages.size() + 1) / 2);
        List<List<String>> result = new ArrayList<>();
        for (List<String> lines : pages) {
            var kept = new ArrayList<>(lines);
            if (!kept.isEmpty() && firsts.getOrDefault(shape(kept.getFirst()), 0) >= threshold) {
                kept.removeFirst();
            }
            if (!kept.isEmpty()
                    && (lasts.getOrDefault(shape(kept.getLast()), 0) >= threshold
                            || PAGE_NUMBER.matcher(kept.getLast()).matches())) {
                kept.removeLast();
            }
            result.add(kept);
        }
        return result;
    }

    /** ページ番号のちがいを無視して比べるため、数字をそろえる */
    private static String shape(String line) {
        return line.replaceAll("\\d+", "#");
    }

    private static int maxLineLength(List<List<String>> pages) {
        return pages.stream()
                .flatMap(List::stream)
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }

    /** Markdown と見出しごとのページ範囲を組み立てる */
    private static final class Builder {

        private final StringBuilder markdown = new StringBuilder();
        private final List<ParsedSection> sections = new ArrayList<>();
        private String heading;
        private String articleNo;
        private int pageFrom = 1;
        private int pageTo = 1;
        private boolean hasContent;
        /** 直前の行（段落の途中なら、その行の長さで次の行をつなぐか決める） */
        private String previousLine;

        /** 今の見出しの中で出た行（ページをまたぐ表の見出し行の繰り返しを見つけるため） */
        private final java.util.Set<String> linesInSection = new java.util.HashSet<>();

        boolean isEmpty() {
            return markdown.isEmpty();
        }

        boolean seenInSection(String line) {
            return linesInSection.contains(line);
        }

        void chapter(String line, int page) {
            flush();
            block("## " + line);
            startSection(null, null, page);
        }

        void supplementary(String line, int page) {
            flush();
            block("## " + line);
            startSection(line, null, page);
            hasContent = true; // 附則は見出しだけでもセクションにする（MarkdownSectionSplitter と同じ）
        }

        void section(String line, String number, int page) {
            flush();
            block("### " + line);
            startSection(line, number, page);
            hasContent = true;
        }

        void paragraph(String line, int page, int wrapWidth) {
            block(line);
            touch(page);
            previousLine = line;
        }

        void text(String line, int page, int wrapWidth) {
            if (previousLine != null && previousLine.length() >= wrapWidth) {
                // 折り返された行: 改行を入れずにつなぐ（英数字どうしの間だけ空白を入れる）
                boolean space = Character.isLetterOrDigit(markdown.charAt(markdown.length() - 1))
                        && markdown.charAt(markdown.length() - 1) < 128
                        && Character.isLetterOrDigit(line.charAt(0))
                        && line.charAt(0) < 128;
                markdown.append(space ? " " : "").append(line);
                previousLine = line;
                touch(page);
                return;
            }
            paragraph(line, page, wrapWidth);
        }

        private void block(String line) {
            linesInSection.add(line);
            if (!markdown.isEmpty()) {
                markdown.append("\n\n");
            }
            markdown.append(line);
            previousLine = null;
        }

        private void startSection(String heading, String articleNo, int page) {
            this.heading = heading;
            this.articleNo = articleNo;
            this.pageFrom = page;
            this.pageTo = page;
            this.hasContent = false;
            linesInSection.clear();
        }

        private void touch(int page) {
            pageTo = page;
            hasContent = true;
        }

        /** 前の見出し（前文を含む）のページ範囲を確定する。章の見出しだけのものは含めない */
        private void flush() {
            if (hasContent && (heading != null || sections.isEmpty())) {
                sections.add(new ParsedSection(heading, articleNo, pageFrom, pageTo));
            }
            hasContent = false;
        }

        String markdown() {
            return markdown + "\n";
        }

        List<ParsedSection> sections() {
            flush();
            return List.copyOf(sections);
        }
    }
}
