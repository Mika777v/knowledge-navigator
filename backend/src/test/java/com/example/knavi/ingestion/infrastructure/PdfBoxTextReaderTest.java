package com.example.knavi.ingestion.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.knavi.ingestion.domain.PdfExtractionException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

class PdfBoxTextReaderTest {

    private final PdfBoxTextReader reader = new PdfBoxTextReader();

    @Test
    void rejectsPasswordProtectedPdfs() throws IOException {
        byte[] pdf;
        try (var document = new PDDocument();
                var out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            var policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(out);
            pdf = out.toByteArray();
        }

        assertThatThrownBy(() -> reader.read(pdf))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("パスワードで保護された PDF");
    }

    @Test
    void rejectsBrokenFiles() {
        assertThatThrownBy(() -> reader.read("%PDF-1.4\nこわれたファイル".getBytes()))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("壊れている可能性");
    }
}
