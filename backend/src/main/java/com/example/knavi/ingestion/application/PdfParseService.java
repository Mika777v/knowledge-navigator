package com.example.knavi.ingestion.application;

import com.example.knavi.ingestion.domain.PdfExtractionException;
import com.example.knavi.ingestion.domain.RegulationPdfParser;
import com.example.knavi.ingestion.domain.RegulationPdfParser.ParsedRegulation;
import com.example.knavi.shared.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

/** アップロード済みの PDF を解析し、規程の Markdown と見出しごとのページ範囲にする（ADR-0010） */
@Service
public class PdfParseService {

    private final FileUploadService files;
    private final PdfTextReader reader;
    private final RegulationPdfParser parser = new RegulationPdfParser();

    PdfParseService(FileUploadService files, PdfTextReader reader) {
        this.files = files;
        this.reader = reader;
    }

    /**
     * 取り込みジョブ（システム処理）から使う。
     *
     * @throws PdfExtractionException 解析できない PDF（理由は利用者に見せる文）
     */
    ParsedRegulation parseForJob(UUID fileId) {
        return parser.parse(reader.read(files.contentForJob(fileId)));
    }

    /** @throws ErrorResponseException 解析できない PDF は 422（理由つき） */
    public ParsedRegulation parse(UUID fileId, AuthenticatedUser user) {
        byte[] content = files.content(fileId, user);
        try {
            return parser.parse(reader.read(content));
        } catch (PdfExtractionException e) {
            var status = HttpStatus.UNPROCESSABLE_CONTENT;
            throw new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, e.getMessage()), e);
        }
    }
}
