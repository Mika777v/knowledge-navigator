package com.example.knavi.ingestion.application;

import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.DocumentPdfImport.PageRange;
import com.example.knavi.ingestion.SourcePdfImport;
import com.example.knavi.ingestion.domain.RegulationPdfParser;
import com.example.knavi.ingestion.domain.RegulationPdfParser.ParsedRegulation;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link SourcePdfImport} の実装。解析は取り込みジョブと同じ処理を使う */
@Service
@Transactional
class SourcePdfImporter implements SourcePdfImport {

    private final FileUploadService files;
    private final PdfTextReader reader;
    private final DocumentImport documents;
    private final RegulationPdfParser parser = new RegulationPdfParser();

    SourcePdfImporter(FileUploadService files, PdfTextReader reader, DocumentImport documents) {
        this.files = files;
        this.reader = reader;
        this.documents = documents;
    }

    @Override
    public int attachToCurrentVersion(String documentCode, String fileName, byte[] pdf) {
        ParsedRegulation parsed = parser.parse(reader.read(pdf));
        UUID fileId = files.storeForSystem(fileName, pdf);
        return documents.attachSourceToCurrentVersion(
                documentCode,
                fileId,
                parsed.sections().stream()
                        .map(s -> new PageRange(
                                s.articleNo() != null ? s.articleNo() : s.heading() != null ? s.heading() : "前文",
                                s.pageFrom(),
                                s.pageTo()))
                        .toList());
    }
}
