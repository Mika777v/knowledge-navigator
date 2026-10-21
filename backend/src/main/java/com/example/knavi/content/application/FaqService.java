package com.example.knavi.content.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.content.domain.ContentRuleViolation;
import com.example.knavi.content.domain.ContentStatus;
import com.example.knavi.content.domain.Document;
import com.example.knavi.content.domain.Faq;
import com.example.knavi.content.domain.FaqLink;
import com.example.knavi.content.domain.LinkTargetType;
import com.example.knavi.content.infrastructure.CategoryRepository;
import com.example.knavi.content.infrastructure.DocumentRepository;
import com.example.knavi.content.infrastructure.FaqRepository;
import com.example.knavi.content.infrastructure.ProgramRepository;
import com.example.knavi.content.infrastructure.SectionRepository;
import com.example.knavi.organization.ContactDirectory;
import com.example.knavi.organization.DepartmentDirectory;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.security.Visibility;
import com.example.knavi.shared.web.ApiErrors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FAQ の作成・更新・公開（管理画面から使う） */
@Service
@Transactional
public class FaqService {

    public record Content(
            String question,
            String answerMarkdown,
            String categorySlug,
            String contactCode,
            Visibility visibility,
            List<LinkItem> links) {}

    /** 関連先。program か document（article を指定すると条）のどちらか */
    public record LinkItem(String programCode, String documentCode, String articleNo) {}

    private final FaqRepository faqs;
    private final ProgramRepository programs;
    private final DocumentRepository documents;
    private final SectionRepository sections;
    private final CategoryRepository categories;
    private final DepartmentDirectory departments;
    private final ContactDirectory contacts;
    private final AccessPolicy accessPolicy;
    private final AuditLog auditLog;

    FaqService(
            FaqRepository faqs,
            ProgramRepository programs,
            DocumentRepository documents,
            SectionRepository sections,
            CategoryRepository categories,
            DepartmentDirectory departments,
            ContactDirectory contacts,
            AccessPolicy accessPolicy,
            AuditLog auditLog) {
        this.faqs = faqs;
        this.programs = programs;
        this.documents = documents;
        this.sections = sections;
        this.categories = categories;
        this.departments = departments;
        this.contacts = contacts;
        this.accessPolicy = accessPolicy;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public List<Faq> listOwnedBy(AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        return faqs.findByOwnerDepartmentIdOrderByCode(user.departmentId());
    }

    @Transactional(readOnly = true)
    public Faq get(UUID id, AuthenticatedUser user) {
        Faq faq = faqs.findById(id).orElseThrow(() -> ApiErrors.notFound("FAQ が見つかりません: " + id));
        accessPolicy.checkCanEditContent(user, faq.getOwnerDepartmentId());
        return faq;
    }

    public Faq create(String code, String ownerDepartmentCode, Content content, AuthenticatedUser user) {
        UUID owner = departments
                .findByCode(ownerDepartmentCode)
                .orElseThrow(() -> ApiErrors.badRequest("部署 " + ownerDepartmentCode + " が存在しません。"))
                .id();
        accessPolicy.checkCanEditContent(user, owner);
        if (faqs.existsByCode(code)) {
            throw ApiErrors.conflict("FAQ コード " + code + " は既に使われています。");
        }
        Faq faq = new Faq(code, owner);
        apply(faq, content);
        faqs.save(faq);
        auditLog.record(user.id(), "FAQ_CREATED", "FAQ", faq.getId(), Map.of("code", code));
        return faq;
    }

    public Faq update(UUID id, int expectedRowVersion, Content content, AuthenticatedUser user) {
        Faq faq = getForUpdate(id, expectedRowVersion, user);
        apply(faq, content);
        if (faq.getStatus() == ContentStatus.PUBLISHED) {
            checkPublishable(faq);
        }
        faqs.flush();
        auditLog.record(user.id(), "FAQ_UPDATED", "FAQ", faq.getId(), Map.of("question", faq.getQuestion()));
        return faq;
    }

    public Faq publish(UUID id, int expectedRowVersion, AuthenticatedUser user) {
        Faq faq = getForUpdate(id, expectedRowVersion, user);
        try {
            faq.publish(linkedDocumentsAreWider(faq));
        } catch (ContentRuleViolation e) {
            throw ApiErrors.conflict(e.getMessage());
        }
        faqs.flush();
        auditLog.record(user.id(), "FAQ_PUBLISHED", "FAQ", faq.getId(), Map.of());
        return faq;
    }

    public Faq archive(UUID id, int expectedRowVersion, AuthenticatedUser user) {
        Faq faq = getForUpdate(id, expectedRowVersion, user);
        try {
            faq.archive();
        } catch (ContentRuleViolation e) {
            throw ApiErrors.conflict(e.getMessage());
        }
        faqs.flush();
        auditLog.record(user.id(), "FAQ_ARCHIVED", "FAQ", faq.getId(), Map.of());
        return faq;
    }

    private void apply(Faq faq, Content content) {
        UUID contactId = content.contactCode() == null
                ? null
                : contacts.findByCode(content.contactCode())
                        .orElseThrow(() -> ApiErrors.badRequest("窓口 " + content.contactCode() + " が存在しません。"))
                        .id();
        var category = categories
                .findBySlug(content.categorySlug())
                .orElseThrow(() -> ApiErrors.badRequest("カテゴリ " + content.categorySlug() + " が存在しません。"));
        faq.update(
                content.question(),
                content.answerMarkdown(),
                category,
                contactId,
                content.visibility(),
                resolveLinks(content.links() == null ? List.of() : content.links()));
    }

    private List<FaqLink> resolveLinks(List<LinkItem> items) {
        List<FaqLink> links = new ArrayList<>();
        for (LinkItem item : items) {
            if (item.programCode() != null) {
                var program = programs.findByCode(item.programCode())
                        .orElseThrow(() -> ApiErrors.badRequest("制度 " + item.programCode() + " が存在しません。"));
                links.add(new FaqLink(LinkTargetType.PROGRAM, program.getId()));
            } else if (item.documentCode() != null) {
                Document doc = documents
                        .findByCode(item.documentCode())
                        .orElseThrow(() -> ApiErrors.badRequest("文書 " + item.documentCode() + " が存在しません。"));
                if (item.articleNo() == null) {
                    links.add(new FaqLink(LinkTargetType.DOCUMENT, doc.getId()));
                } else {
                    if (doc.getCurrentVersionId() == null) {
                        throw ApiErrors.conflict("文書「" + doc.getTitle() + "」はまだ公開されていません。");
                    }
                    var section = sections.findByVersionIdAndArticleNo(doc.getCurrentVersionId(), item.articleNo())
                            .orElseThrow(() ->
                                    ApiErrors.badRequest(doc.getTitle() + "の公開版に " + item.articleNo() + " がありません。"));
                    links.add(new FaqLink(LinkTargetType.SECTION, section.getId()));
                }
            } else {
                throw ApiErrors.badRequest("関連先には制度か文書を指定してください。");
            }
        }
        return links;
    }

    /** 関連する文書・条の公開範囲が、すべて FAQ の公開範囲以上に広いか */
    private boolean linkedDocumentsAreWider(Faq faq) {
        for (FaqLink link : faq.getLinks()) {
            Visibility docVisibility = switch (link.targetType()) {
                case DOCUMENT ->
                    documents
                            .findById(link.targetId())
                            .map(Document::getVisibility)
                            .orElse(null);
                case SECTION ->
                    sections.findById(link.targetId())
                            .map(s -> s.getVersion().getDocument().getVisibility())
                            .orElse(null);
                case PROGRAM -> null;
            };
            if (docVisibility != null && faq.getVisibility().isWiderThan(docVisibility)) {
                return false;
            }
        }
        return true;
    }

    private void checkPublishable(Faq faq) {
        try {
            faq.checkPublishable(linkedDocumentsAreWider(faq));
        } catch (ContentRuleViolation e) {
            throw ApiErrors.conflict(e.getMessage());
        }
    }

    private Faq getForUpdate(UUID id, int expectedRowVersion, AuthenticatedUser user) {
        Faq faq = get(id, user);
        if (faq.getRowVersion() != expectedRowVersion) {
            throw ApiErrors.preconditionFailed();
        }
        return faq;
    }
}
