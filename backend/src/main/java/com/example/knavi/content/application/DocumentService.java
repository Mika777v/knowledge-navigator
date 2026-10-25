package com.example.knavi.content.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.content.DocumentPdfImport;
import com.example.knavi.content.domain.Category;
import com.example.knavi.content.domain.ContentRuleViolation;
import com.example.knavi.content.domain.Document;
import com.example.knavi.content.domain.DocumentVersion;
import com.example.knavi.content.domain.MarkdownSectionSplitter;
import com.example.knavi.content.domain.SectionDraft;
import com.example.knavi.content.infrastructure.CategoryRepository;
import com.example.knavi.content.infrastructure.DocumentRepository;
import com.example.knavi.content.infrastructure.DocumentVersionRepository;
import com.example.knavi.organization.DepartmentDirectory;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.text.TextNormalizer;
import com.example.knavi.shared.web.ApiErrors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 文書・版の作成と公開（管理画面から使う） */
@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final CategoryRepository categories;
    private final DepartmentDirectory departments;
    private final AccessPolicy accessPolicy;
    private final AuditLog auditLog;
    private final Clock clock;
    private final DocumentTags tags;
    private final EntityManager entityManager;
    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter();

    DocumentService(
            DocumentRepository documents,
            DocumentVersionRepository versions,
            CategoryRepository categories,
            DepartmentDirectory departments,
            AccessPolicy accessPolicy,
            AuditLog auditLog,
            Clock clock,
            DocumentTags tags,
            EntityManager entityManager) {
        this.documents = documents;
        this.versions = versions;
        this.categories = categories;
        this.departments = departments;
        this.accessPolicy = accessPolicy;
        this.auditLog = auditLog;
        this.clock = clock;
        this.tags = tags;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<Document> listOwnedBy(AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        return documents.findByOwnerDepartmentIdOrderByCode(user.departmentId());
    }

    @Transactional(readOnly = true)
    public Document get(UUID documentId, AuthenticatedUser user) {
        Document document = documents.findWithCategoryById(documentId).orElseThrow(() -> notFound(documentId));
        accessPolicy.checkCanEditContent(user, document.getOwnerDepartmentId());
        return document;
    }

    @Transactional(readOnly = true)
    public List<DocumentVersion> listVersions(UUID documentId, AuthenticatedUser user) {
        get(documentId, user);
        return versions.findByDocumentIdOrderByCreatedAtDesc(documentId);
    }

    @Transactional(readOnly = true)
    public DocumentVersion getVersion(UUID documentId, UUID versionId, AuthenticatedUser user) {
        get(documentId, user);
        return findVersion(documentId, versionId);
    }

    public Document create(DocumentCommands.Create command, AuthenticatedUser user) {
        UUID ownerDepartmentId = departments
                .findByCode(command.ownerDepartmentCode())
                .orElseThrow(() -> ApiErrors.badRequest("部署 " + command.ownerDepartmentCode() + " が存在しません。"))
                .id();
        accessPolicy.checkCanEditContent(user, ownerDepartmentId);
        if (documents.existsByCode(command.code())) {
            throw ApiErrors.conflict("文書コード " + command.code() + " は既に使われています。");
        }
        Document document = new Document(
                command.code(),
                command.title(),
                command.summary(),
                command.documentType(),
                category(command.categorySlug()),
                ownerDepartmentId,
                command.visibility(),
                command.reviewDueOn(),
                user.id());
        documents.save(document);
        audit(user, "DOCUMENT_CREATED", document, Map.of("code", document.getCode(), "title", document.getTitle()));
        return document;
    }

    public Document update(
            UUID documentId, int expectedRowVersion, DocumentCommands.Update command, AuthenticatedUser user) {
        Document document = getForUpdate(documentId, expectedRowVersion, user);
        document.update(
                command.title(),
                command.summary(),
                command.documentType(),
                category(command.categorySlug()),
                command.visibility(),
                command.reviewDueOn(),
                user.id());
        if (command.tags() != null && tags.replace(documentId, command.tags())) {
            // タグだけを変えたときも版を上げる（ほかの人の同時の変更を楽観ロックで検出するため）。
            // 応答の ETag に新しい版を返せるよう、コミットを待たずにその場で上げる
            entityManager.lock(document, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
        documents.flush();
        audit(
                user,
                "DOCUMENT_UPDATED",
                document,
                Map.of(
                        "title", document.getTitle(),
                        "visibility", document.getVisibility(),
                        "tags", tags.namesOf(documentId)));
        return document;
    }

    public DocumentVersion createVersion(
            UUID documentId, DocumentCommands.CreateVersion command, AuthenticatedUser user) {
        Document document = get(documentId, user);
        if (versions.existsByDocumentIdAndVersionLabel(documentId, command.versionLabel())) {
            throw ApiErrors.conflict("版 " + command.versionLabel() + " は既に存在します。");
        }
        DocumentVersion version =
                document.newVersion(command.versionLabel(), command.effectiveFrom(), command.changeNote());
        version.replaceBody(
                command.bodyMarkdown(), splitter.split(command.bodyMarkdown()), d -> searchText(document, d));
        versions.save(version);
        audit(user, "DOCUMENT_VERSION_CREATED", document, Map.of("versionLabel", version.getVersionLabel()));
        return version;
    }

    public DocumentVersion updateVersion(
            UUID documentId, UUID versionId, DocumentCommands.UpdateVersion command, AuthenticatedUser user) {
        Document document = get(documentId, user);
        DocumentVersion version = findVersion(documentId, versionId);
        try {
            version.updateAttributes(command.effectiveFrom(), command.changeNote());
            // PDF から取り込んだ版は、本文を直しても同じ条（条でなければ同じ見出し）の原本のページを引き継ぐ
            List<DocumentPdfImport.PageRange> pages = version.getSections().stream()
                    .filter(s -> s.getPageFrom() != null && s.getPageTo() != null)
                    .map(s -> new DocumentPdfImport.PageRange(
                            s.getArticleNo() != null ? s.getArticleNo() : s.getHeading(),
                            s.getPageFrom(),
                            s.getPageTo()))
                    .toList();
            // 既存セクションの削除を先に反映しないと (version, ordinal) の一意制約に触れる
            version.clearSections();
            versions.flush();
            version.replaceBody(
                    command.bodyMarkdown(),
                    PdfDraftVersions.withPages(splitter.split(command.bodyMarkdown()), pages),
                    d -> searchText(document, d));
        } catch (ContentRuleViolation e) {
            throw ApiErrors.conflict(e.getMessage());
        }
        return version;
    }

    public Document publish(
            UUID documentId, int expectedRowVersion, DocumentCommands.Publish command, AuthenticatedUser user) {
        Document document = getForUpdate(documentId, expectedRowVersion, user);
        DocumentVersion version =
                versions.findById(command.versionId()).orElseThrow(() -> ApiErrors.notFound("版が見つかりません。"));
        try {
            document.publish(version, clock.instant(), user.id());
        } catch (ContentRuleViolation e) {
            throw ApiErrors.conflict(e.getMessage());
        }
        documents.flush();
        audit(user, "DOCUMENT_PUBLISHED", document, Map.of("versionLabel", version.getVersionLabel()));
        return document;
    }

    public Document archive(UUID documentId, int expectedRowVersion, AuthenticatedUser user) {
        Document document = getForUpdate(documentId, expectedRowVersion, user);
        try {
            document.archive(user.id());
        } catch (ContentRuleViolation e) {
            throw ApiErrors.conflict(e.getMessage());
        }
        documents.flush();
        audit(user, "DOCUMENT_ARCHIVED", document, Map.of());
        return document;
    }

    /** 検索用テキスト: 文書名・条番号・見出し・本文をつなげて正規化する（見出しや文書名でもヒットさせるため） */
    static String searchText(Document document, SectionDraft draft) {
        String joined = String.join(
                " ",
                document.getTitle(),
                draft.articleNo() == null ? "" : draft.articleNo(),
                draft.heading() == null ? "" : draft.heading(),
                draft.content());
        return TextNormalizer.normalize(joined);
    }

    private Document getForUpdate(UUID documentId, int expectedRowVersion, AuthenticatedUser user) {
        Document document = get(documentId, user);
        if (document.getRowVersion() != expectedRowVersion) {
            throw ApiErrors.preconditionFailed();
        }
        return document;
    }

    private DocumentVersion findVersion(UUID documentId, UUID versionId) {
        return versions.findById(versionId)
                .filter(v -> v.getDocument().getId().equals(documentId))
                .orElseThrow(() -> ApiErrors.notFound("版が見つかりません。"));
    }

    private Category category(String slug) {
        return categories.findBySlug(slug).orElseThrow(() -> ApiErrors.badRequest("カテゴリ " + slug + " が存在しません。"));
    }

    private void audit(AuthenticatedUser user, String action, Document document, Map<String, ?> details) {
        auditLog.record(user.id(), action, "DOCUMENT", document.getId(), details);
    }

    private static RuntimeException notFound(UUID documentId) {
        return ApiErrors.notFound("文書が見つかりません: " + documentId);
    }
}
