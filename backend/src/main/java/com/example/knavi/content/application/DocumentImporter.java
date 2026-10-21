package com.example.knavi.content.application;

import com.example.knavi.content.DocumentImport;
import com.example.knavi.content.domain.Document;
import com.example.knavi.content.domain.DocumentType;
import com.example.knavi.content.domain.DocumentVersion;
import com.example.knavi.content.domain.MarkdownSectionSplitter;
import com.example.knavi.content.infrastructure.CategoryRepository;
import com.example.knavi.content.infrastructure.DocumentRepository;
import com.example.knavi.content.infrastructure.DocumentVersionRepository;
import com.example.knavi.shared.security.Visibility;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DocumentImporter implements DocumentImport {

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final CategoryRepository categories;
    private final Clock clock;
    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter();

    DocumentImporter(
            DocumentRepository documents,
            DocumentVersionRepository versions,
            CategoryRepository categories,
            Clock clock) {
        this.documents = documents;
        this.versions = versions;
        this.categories = categories;
        this.clock = clock;
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
