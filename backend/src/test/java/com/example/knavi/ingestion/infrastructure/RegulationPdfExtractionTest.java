package com.example.knavi.ingestion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.content.domain.MarkdownSectionSplitter;
import com.example.knavi.content.domain.SectionDraft;
import com.example.knavi.ingestion.domain.RegulationPdfParser;
import com.example.knavi.ingestion.domain.RegulationPdfParser.ParsedRegulation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * data/pdf のダミー規程10本（元は data/seed/documents の Markdown）を実際に PDFBox で読み、
 * 元の Markdown と同じ条・見出し・本文になること、ページ範囲が正しいことを確かめる
 */
class RegulationPdfExtractionTest {

    private final PdfBoxTextReader reader = new PdfBoxTextReader();
    private final RegulationPdfParser parser = new RegulationPdfParser();
    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter();

    static Stream<Path> pdfs() throws IOException {
        return Files.list(Path.of("..", "data", "pdf"))
                .filter(p -> p.toString().endsWith(".pdf"))
                .sorted();
    }

    @ParameterizedTest(name = "{index}")
    @MethodSource("pdfs")
    void extractsTheSameArticlesAsTheOriginalMarkdown(Path pdf) throws IOException {
        ParsedRegulation parsed = parser.parse(reader.read(Files.readAllBytes(pdf)));
        List<SectionDraft> fromPdf = articlesAndAppendix(splitter.split(parsed.markdown()));
        List<SectionDraft> original = articlesAndAppendix(splitter.split(Files.readString(original(pdf))));

        assertThat(fromPdf)
                .extracting(SectionDraft::articleNo, SectionDraft::heading)
                .containsExactlyElementsOf(original.stream()
                        .map(s -> org.assertj.core.groups.Tuple.tuple(s.articleNo(), s.heading()))
                        .toList());
        for (int i = 0; i < original.size(); i++) {
            assertThat(comparable(fromPdf.get(i).content()))
                    .as("%s の本文", original.get(i).heading())
                    .isEqualTo(comparable(original.get(i).content()));
        }
    }

    @ParameterizedTest(name = "{index}")
    @MethodSource("pdfs")
    void recordsPageRangesInOrder(Path pdf) throws IOException {
        ParsedRegulation parsed = parser.parse(reader.read(Files.readAllBytes(pdf)));

        String title = Files.readAllLines(original(pdf)).stream()
                .filter(l -> l.startsWith("title: "))
                .findFirst()
                .orElseThrow()
                .substring("title: ".length());
        assertThat(parsed.title()).isEqualTo(title);
        var sections = parsed.sections();
        assertThat(sections.getFirst().pageFrom()).isEqualTo(1);
        assertThat(sections.getLast().pageTo()).isEqualTo(parsed.pageCount());
        for (int i = 0; i < sections.size(); i++) {
            var s = sections.get(i);
            assertThat(s.pageFrom()).isLessThanOrEqualTo(s.pageTo());
            if (i > 0) {
                assertThat(s.pageFrom())
                        .isGreaterThanOrEqualTo(sections.get(i - 1).pageTo());
            }
        }
    }

    /** 条と附則のセクション（前文は PDF にだけある表題・施行日の行のため比べない） */
    private static List<SectionDraft> articlesAndAppendix(List<SectionDraft> sections) {
        return sections.stream().filter(s -> !"前文".equals(s.heading())).toList();
    }

    /** 書式のちがい（空白・改行・表の罫線・箇条書きの記号・強調）を除いて比べる */
    private static String comparable(String content) {
        return Normalizer.normalize(content, Normalizer.Form.NFKC)
                .replaceAll("(?m)^\\s*\\|?\\s*:?-{3,}.*$", "")
                .replaceAll("(?m)^\\s*(?:\\d+\\.|[-*・])\\s+", "")
                .replaceAll("\\*\\*|\\||\\s", "");
    }

    /** ファイル名に日本語を含むため、OS のロケールに依存しないようにコード（REG-GA-001_ など）の接頭辞で探す */
    private static Path original(Path pdf) throws IOException {
        String code = pdf.getFileName()
                .toString()
                .substring(0, pdf.getFileName().toString().indexOf('_') + 1);
        try (var files = Files.list(Path.of("..", "data", "seed", "documents"))) {
            return files.filter(p -> p.getFileName().toString().startsWith(code))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
