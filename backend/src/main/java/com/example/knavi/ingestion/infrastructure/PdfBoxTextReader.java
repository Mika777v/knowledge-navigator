package com.example.knavi.ingestion.infrastructure;

import com.example.knavi.ingestion.application.PdfTextReader;
import com.example.knavi.ingestion.domain.PdfExtractionException;
import com.example.knavi.ingestion.domain.PdfText;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Apache PDFBox でページごとに文字を取り出す（ADR-0010） */
@Component
class PdfBoxTextReader implements PdfTextReader {

    @Override
    public PdfText read(byte[] pdf) {
        // 開くのにパスワードが要る PDF は読めない（印刷制限などだけの PDF は読める）
        try (PDDocument document = Loader.loadPDF(pdf)) {
            var stripper = new PDFTextStripper();
            // 文字の並び順を、ファイル内の順ではなく紙面の位置（上から下・左から右）にする
            stripper.setSortByPosition(true);
            List<List<String>> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document)
                        .lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty())
                        .toList());
            }
            String title = document.getDocumentInformation().getTitle();
            return new PdfText(title == null || title.isBlank() ? null : title.strip(), pages);
        } catch (InvalidPasswordException e) {
            throw new PdfExtractionException("パスワードで保護された PDF は取り込めません。保護を外してからアップロードしてください。", e);
        } catch (IOException e) {
            throw new PdfExtractionException("PDF を読み込めませんでした。ファイルが壊れている可能性があります。", e);
        }
    }
}
