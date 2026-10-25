package com.example.knavi.content.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.content.DocumentPdfImport;
import com.example.knavi.content.domain.Document;
import com.example.knavi.content.domain.DocumentVersion;
import com.example.knavi.content.domain.MarkdownSectionSplitter;
import com.example.knavi.content.domain.SectionDraft;
import com.example.knavi.content.infrastructure.DocumentRepository;
import com.example.knavi.content.infrastructure.DocumentVersionRepository;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PDF の解析結果から下書きの版を作る（{@link DocumentPdfImport} の実装） */
@Service
@Transactional
class PdfDraftVersions implements DocumentPdfImport {

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final AccessPolicy accessPolicy;
    private final AuditLog auditLog;
    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter();

    PdfDraftVersions(
            DocumentRepository documents,
            DocumentVersionRepository versions,
            AccessPolicy accessPolicy,
            AuditLog auditLog) {
        this.documents = documents;
        this.versions = versions;
        this.accessPolicy = accessPolicy;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanEdit(UUID documentId, AuthenticatedUser user) {
        Document document = find(documentId);
        accessPolicy.checkCanEditContent(user, document.getOwnerDepartmentId());
    }

    @Override
    public UUID createDraftVersion(DraftVersion draft) {
        Document document = find(draft.documentId());
        if (versions.existsByDocumentIdAndVersionLabel(document.getId(), draft.versionLabel())) {
            throw ApiErrors.conflict("版 " + draft.versionLabel() + " は既に存在します。別の版ラベルで取り込んでください。");
        }
        DocumentVersion version = document.newVersion(draft.versionLabel(), draft.effectiveFrom(), draft.changeNote());
        version.attachSourceFile(draft.sourceFileId());
        List<SectionDraft> sections = withPages(splitter.split(draft.bodyMarkdown()), draft.pages());
        version.replaceBody(draft.bodyMarkdown(), sections, d -> DocumentService.searchText(document, d));
        // 呼び出し元（取り込みジョブ）が同じトランザクションの中で、JDBC でこの版を参照するため、すぐに書き込む
        versions.saveAndFlush(version);
        auditLog.record(
                draft.actorId(),
                "DOCUMENT_VERSION_CREATED",
                "DOCUMENT",
                document.getId(),
                Map.of("versionLabel", version.getVersionLabel(), "sourceFileId", draft.sourceFileId()));
        return version.getId();
    }

    /** 見出しの順に、条番号（条でなければ見出し）が同じページ範囲を割り当てる */
    static List<SectionDraft> withPages(List<SectionDraft> sections, List<PageRange> pages) {
        var remaining = new ArrayList<>(pages);
        var result = new ArrayList<SectionDraft>();
        for (SectionDraft s : sections) {
            String key = s.articleNo() != null ? s.articleNo() : s.heading();
            var match = remaining.stream().filter(p -> p.key().equals(key)).findFirst();
            if (match.isPresent()) {
                remaining.remove(match.get());
                result.add(s.withPages(match.get().pageFrom(), match.get().pageTo()));
            } else {
                result.add(s);
            }
        }
        return result;
    }

    private Document find(UUID documentId) {
        return documents.findById(documentId).orElseThrow(() -> ApiErrors.notFound("文書が見つかりません。"));
    }
}
