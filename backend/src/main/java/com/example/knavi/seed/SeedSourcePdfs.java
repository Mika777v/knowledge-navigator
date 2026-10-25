package com.example.knavi.seed;

import com.example.knavi.ingestion.SourcePdfImport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * data/pdf の規程の PDF（data/seed/documents と同じ内容）を、文書の公開中の版に原本として結び付ける。
 * 取り込みと同じ処理で PDF を解析し、条ごとの原本のページも付ける（デモで「原本を開く」「p.3」を見せるため）
 */
@Service
class SeedSourcePdfs {

    private static final Logger log = LoggerFactory.getLogger(SeedSourcePdfs.class);

    private final SourcePdfImport sourcePdfs;

    SeedSourcePdfs(SourcePdfImport sourcePdfs) {
        this.sourcePdfs = sourcePdfs;
    }

    /** @return 原本を結び付けた文書の数 */
    int attach(Path dir) {
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".pdf"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path file : files) {
            // ファイル名は「コード_規程名.pdf」（scripts/data/generate_pdfs.py）
            String name = file.getFileName().toString();
            String code = name.substring(0, name.indexOf('_'));
            try {
                int sections = sourcePdfs.attachToCurrentVersion(code, name, Files.readAllBytes(file));
                log.info("{}: 原本を結び付けました（{} の条・見出しにページ）", code, sections);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files.size();
    }
}
