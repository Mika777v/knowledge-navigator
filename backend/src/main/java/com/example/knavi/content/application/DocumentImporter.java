package com.example.knavi.content.application;

import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.DocumentPdfImport;
import com.example.knavi.content.domain.Document;
import com.example.knavi.content.domain.DocumentType;
import com.example.knavi.content.domain.DocumentVersion;
import com.example.knavi.content.domain.MarkdownSectionSplitter;
import com.example.knavi.content.infrastructure.CategoryRepository;
import com.example.knavi.content.infrastructure.DocumentRepository;
import com.example.knavi.content.infrastructure.DocumentVersionRepository;
import com.example.knavi.shared.security.Visibility;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DocumentImporter implements DocumentImport {

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final CategoryRepository categories;
    private final Clock clock;
    private final DocumentTags tags;
    private final JdbcClient jdbc;
    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter();

    DocumentImporter(
            DocumentRepository documents,
            DocumentVersionRepository versions,
            CategoryRepository categories,
            Clock clock,
            DocumentTags tags,
            JdbcClient jdbc) {
        this.documents = documents;
        this.versions = versions;
        this.categories = categories;
        this.clock = clock;
        this.tags = tags;
        this.jdbc = jdbc;
    }

    @Override
    public int attachSourceToCurrentVersion(
            String documentCode, UUID sourceFileId, List<DocumentPdfImport.PageRange> pages) {
        Document document = documents
                .findByCode(documentCode)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + documentCode));
        UUID versionId = document.getCurrentVersionId();
        if (versionId == null) {
            throw new IllegalStateException("公開中の版がありません: " + documentCode);
        }
        jdbc.sql("UPDATE document_versions SET source_file_id = :file WHERE id = :version")
                .param("file", sourceFileId)
                .param("version", versionId)
                .update();
        int attached = 0;
        var remaining = new java.util.ArrayList<>(pages);
        var sections = jdbc.sql("SELECT id, article_no, heading FROM sections WHERE document_version_id = :version"
                        + " ORDER BY ordinal")
                .param("version", versionId)
                .query((rs, i) ->
                        new String[] {rs.getString("id"), rs.getString("article_no"), rs.getString("heading")})
                .list();
        for (String[] s : sections) {
            String key = s[1] != null ? s[1] : s[2];
            var match = remaining.stream().filter(p -> p.key().equals(key)).findFirst();
            if (match.isEmpty()) {
                continue;
            }
            remaining.remove(match.get());
            jdbc.sql("UPDATE sections SET page_from = :from, page_to = :to WHERE id = CAST(:id AS uuid)")
                    .param("from", match.get().pageFrom())
                    .param("to", match.get().pageTo())
                    .param("id", s[0])
                    .update();
            attached++;
        }
        return attached;
    }

    @Override
    public void replaceTags(String documentCode, List<String> tagNames) {
        Document document = documents
                .findByCode(documentCode)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + documentCode));
        tags.replace(document.getId(), tagNames);
    }

    @Override
    public ImportResult importPublished(ImportedDocument in) {
        var category = categories
                .findBySlug(in.categorySlug())
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + in.categorySlug()));
        var type = DocumentType.valueOf(in.documentType());
        var visibility = Visibility.valueOf(in.visibility());

        Document document = documents.findByCode(in.code()).orElse(null);
        if (document == null) {
            document = new Document(
                    in.code(),
                    in.title(),
                    in.summary(),
                    type,
                    category,
                    in.ownerDepartmentId(),
                    visibility,
                    null,
                    null);
            documents.save(document);
        } else {
            document.update(in.title(), in.summary(), type, category, visibility, document.getReviewDueOn(), null);
        }

        if (versions.existsByDocumentIdAndVersionLabel(document.getId(), in.versionLabel())) {
            return new ImportResult(document.getId(), document.getCurrentVersionId(), false);
        }
        final Document doc = document;
        DocumentVersion version = doc.newVersion(in.versionLabel(), in.effectiveFrom(), null);
        version.replaceBody(
                in.bodyMarkdown(), splitter.split(in.bodyMarkdown()), d -> DocumentService.searchText(doc, d));
        versions.save(version);
        doc.publish(version, clock.instant(), null);
        documents.flush();
        return new ImportResult(doc.getId(), version.getId(), true);
    }
}
