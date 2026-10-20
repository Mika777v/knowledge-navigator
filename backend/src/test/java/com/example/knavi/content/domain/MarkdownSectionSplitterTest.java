package com.example.knavi.content.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MarkdownSectionSplitterTest {

    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter();

    @Test
    void splitsArticlesWithChapterPath() {
        var sections = splitter.split("""
                ## 第1章 総則

                ### 第1条（目的）

                この規程は、慶弔見舞金について定める。

                ## 第2章 慶事

                ### 第5条（結婚祝金）

                1. 社員が婚姻したときは、結婚祝金として30,000円を支給する。
                2. 申請は、入籍日から6か月以内に行う。
                """);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0)).isEqualTo(new SectionDraft(0, "1/1", "第1条", "目的", "この規程は、慶弔見舞金について定める。"));
        var marriage = sections.get(1);
        assertThat(marriage.ordinal()).isEqualTo(1);
        assertThat(marriage.path()).isEqualTo("2/5");
        assertThat(marriage.articleNo()).isEqualTo("第5条");
        assertThat(marriage.heading()).isEqualTo("結婚祝金");
        assertThat(marriage.content()).startsWith("1. 社員が婚姻したときは").contains("入籍日から6か月以内");
    }

    @Test
    void removesFrontMatterAndPreambleNotice() {
        var sections = splitter.split("""
                ---
                code: REG-X
                title: テスト規程
                ---

                > 本規程は架空企業のサンプルです。

                ### 第1条（目的）

                目的を定める。
                """);

        assertThat(sections).extracting(SectionDraft::articleNo).containsExactly("第1条");
    }

    @Test
    void keepsPreambleTextThatIsNotANotice() {
        var sections = splitter.split("""
                この手順書は、在宅勤務の申請方法を説明する。

                ## 申請の流れ

                勤怠システムで申請する。
                """);

        assertThat(sections).extracting(SectionDraft::heading).containsExactly("前文", "申請の流れ");
        assertThat(sections.get(1).articleNo()).isNull();
    }

    @Test
    void treatsSupplementaryProvisionsOutsideChapters() {
        var sections = splitter.split("""
                ## 第9章 雑則

                ### 第45条（改廃）

                改廃する。

                ## 附則

                1. この規則は、2026年4月1日から施行する。
                """);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(1).heading()).isEqualTo("附則");
        assertThat(sections.get(1).path()).isNull();
    }

    @Test
    void acceptsFullWidthDigitsInHeadings() {
        var sections = splitter.split("### 第１２条（在宅勤務）\n\n本文\n");

        assertThat(sections.get(0).articleNo()).isEqualTo("第12条");
        assertThat(sections.get(0).path()).isEqualTo("12");
    }

    static Stream<Path> seedDocuments() throws IOException {
        return Files.list(Path.of("..", "data", "seed", "documents"))
                .filter(p -> p.toString().endsWith(".md"));
    }

    /** data/seed の規程は「全条 + 附則」に分割され、条の数が見出しの数と一致する */
    @ParameterizedTest
    @MethodSource("seedDocuments")
    void splitsEverySeedRegulation(Path file) throws IOException {
        String markdown = Files.readString(file);
        long articles = Pattern.compile("^### 第\\d+条", Pattern.MULTILINE)
                .matcher(markdown)
                .results()
                .count();

        var sections = splitter.split(markdown);

        assertThat(sections.stream().filter(s -> s.articleNo() != null)).hasSize((int) articles);
        assertThat(sections).last().extracting(SectionDraft::heading).isEqualTo("附則");
        assertThat(sections).allSatisfy(s -> assertThat(s.content()).isNotBlank());
        assertThat(sections).extracting(SectionDraft::heading).doesNotContain("前文");
    }
}
