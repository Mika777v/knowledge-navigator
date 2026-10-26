package com.example.knavi.shared.text;

import com.worksap.nlp.sudachi.Config;
import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.Morpheme;
import com.worksap.nlp.sudachi.Tokenizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 起動時に Sudachi の辞書を読み込み、{@link JapaneseNormalizer} に入れる（ADR-0011）。
 *
 * <p>辞書の場所は {@code knavi.text.sudachi.dictionary}（ファイル、または .dic を1つ含むフォルダ）。
 * 指定がない・読めないときは警告を出し、形態素解析なしで動く（検索は止めない）。
 */
@Component
class SudachiSetup implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SudachiSetup.class);

    private Dictionary dictionary;

    SudachiSetup(@Value("${knavi.text.sudachi.dictionary:}") String location) {
        JapaneseNormalizer.install(load(location));
    }

    private JapaneseNormalizer.Analyzer load(String location) {
        if (location == null || location.isBlank()) {
            log.warn("形態素解析の辞書（knavi.text.sudachi.dictionary）が指定されていません。表記ゆれの吸収なしで検索します");
            return null;
        }
        try {
            Path file = resolve(Path.of(location));
            dictionary = Dictionary.load(Config.defaultConfig().systemDictionary(file));
            String version = file.getFileName() + ":" + Files.size(file);
            log.info("形態素解析の辞書を読み込みました: {}", version);
            return new SudachiAnalyzer(dictionary, version);
        } catch (IOException | RuntimeException e) {
            log.warn("形態素解析の辞書を読み込めませんでした（{}）。表記ゆれの吸収なしで検索します", location, e);
            return null;
        }
    }

    /** フォルダなら、その中の .dic を1つ使う */
    static Path resolve(Path location) throws IOException {
        if (!Files.isDirectory(location)) {
            return location;
        }
        try (Stream<Path> files = Files.list(location)) {
            List<Path> dics = files.filter(p -> p.toString().endsWith(".dic")).toList();
            if (dics.size() != 1) {
                throw new IOException(location + " に .dic が " + dics.size() + " 個あります（1個にしてください）");
            }
            return dics.getFirst();
        }
    }

    @Override
    public void destroy() throws IOException {
        if (dictionary != null) {
            dictionary.close();
        }
    }

    /** Tokenizer はスレッドごとに作る（同時に使えないため） */
    private static final class SudachiAnalyzer implements JapaneseNormalizer.Analyzer {

        private final ThreadLocal<Tokenizer> tokenizer;
        private final String version;

        SudachiAnalyzer(Dictionary dictionary, String version) {
            this.tokenizer = ThreadLocal.withInitial(dictionary::tokenizer);
            this.version = version;
        }

        @Override
        public List<JapaneseNormalizer.Token> tokenize(String text) {
            List<JapaneseNormalizer.Token> tokens = new ArrayList<>();
            for (Morpheme m : tokenizer.get().tokenize(Tokenizer.SplitMode.C, text)) {
                List<String> pos = m.partOfSpeech();
                tokens.add(new JapaneseNormalizer.Token(
                        m.surface(), m.normalizedForm(), pos.get(0), pos.size() > 1 ? pos.get(1) : ""));
            }
            return tokens;
        }

        @Override
        public String dictionaryVersion() {
            return version;
        }
    }
}
