package com.example.knavi.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.knavi.ingestion.domain.RegulationPdfParser.ParsedSection;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegulationPdfParserTest {

    private final RegulationPdfParser parser = new RegulationPdfParser();

    private static final String FULL = "社員が業務のために出張するときは、所属長の承認を得てから出張し、旅費を精算する。この";

    @Test
    void removesHeadersFootersAndJoinsWrappedLines() {
        var parsed = parser.parse(new PdfText(
                "旅費規程",
                List.of(
                        List.of("旅費規程（REG-1）", "旅費規程", "第1章 総則", "第1条（目的）", FULL, "規程は旅費について定める。", "- 1 -"),
                        List.of("旅費規程（REG-1）", "第2条（対象）", "全社員に適用する。", "- 2 -"))));

        assertThat(parsed.title()).isEqualTo("旅費規程");
        assertThat(parsed.pageCount()).isEqualTo(2);
        assertThat(parsed.markdown())
                .isEqualTo("## 第1章 総則\n\n### 第1条（目的）\n\n" + FULL + "規程は旅費について定める。\n\n### 第2条（対象）\n\n全社員に適用する。\n");
        assertThat(parsed.sections())
                .containsExactly(new ParsedSection("第1条（目的）", "第1条", 1, 1), new ParsedSection("第2条（対象）", "第2条", 2, 2));
    }

    @Test
    void doesNotTreatWrappedReferencesAsHeadings() {
        var parsed = parser.parse(new PdfText(
                null,
                List.of(List.of(
                        "第1条（目的）",
                        FULL,
                        "第42条）。",
                        "第2条（返却）",
                        // 番号が続かない「第5条（…）」は、本文中の参照が行頭に来たものとみなす
                        "第5条（結婚祝金）",
                        "第3条（費用）",
                        "会社が負担する。"))));

        assertThat(parsed.sections()).extracting(ParsedSection::articleNo).containsExactly("第1条", "第2条", "第3条");
        assertThat(parsed.markdown()).contains(FULL + "第42条）。").contains("\n\n第5条（結婚祝金）\n\n");
    }

    @Test
    void keepsItemsBulletsAndTheAppendixAndRecordsPagesAcrossBreaks() {
        var parsed = parser.parse(new PdfText(
                null,
                List.of(
                        List.of("第1条（定義）", "1 社員とは、会社と雇用契約を結んだ者をいう。", "・正社員", "- 1 -"),
                        List.of("・契約社員", "附則", "1 この規程は、2026年4月1日から施行する。", "- 2 -"))));

        assertThat(parsed.markdown()).contains("1. 社員とは、会社と雇用契約を結んだ者をいう。\n\n- 正社員\n\n- 契約社員\n\n## 附則\n\n1. この規程は");
        assertThat(parsed.sections())
                .containsExactly(new ParsedSection("第1条（定義）", "第1条", 1, 2), new ParsedSection("附則", null, 2, 2));
    }

    @Test
    void dropsTableHeadersRepeatedAtTheTopOfTheNextPage() {
        var parsed = parser.parse(new PdfText(
                null, List.of(List.of("第1条（宿泊費）", "地域 上限額", "東京23区 12,000円"), List.of("地域 上限額", "その他の地域 10,000円"))));

        assertThat(parsed.markdown()).containsOnlyOnce("地域 上限額");
    }

    @Test
    void rejectsPdfsWithoutText() {
        assertThatThrownBy(() -> parser.parse(new PdfText(null, List.of(List.of(), List.of("- 2 -")))))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("画像だけの PDF");
    }
}
