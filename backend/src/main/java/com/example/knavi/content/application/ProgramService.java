package com.example.knavi.content.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.content.domain.Category;
import com.example.knavi.content.domain.ContactRole;
import com.example.knavi.content.domain.ContentRuleViolation;
import com.example.knavi.content.domain.Document;
import com.example.knavi.content.domain.Program;
import com.example.knavi.content.domain.ProgramContact;
import com.example.knavi.content.domain.Section;
import com.example.knavi.content.infrastructure.CategoryRepository;
import com.example.knavi.content.infrastructure.DocumentRepository;
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
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 制度の作成・更新・公開（管理画面から使う） */
@Service
@Transactional
public class ProgramService {

    public record Attributes(
            String name,
            String nameKana,
            String summary,
            String eligibility,
            String benefit,
            String howToApply,
            String deadlineNote,
            String requiredDocuments,
            String categorySlug,
            Visibility visibility) {}

    public record BasisItem(String documentCode, String articleNo) {}

    public record ContactItem(String contactCode, ContactRole role) {}

    private final ProgramRepository programs;
    private final DocumentRepository documents;
    private final SectionRepository sections;
    private final CategoryRepository categories;
    private final DepartmentDirectory departments;
    private final ContactDirectory contacts;
    private final AccessPolicy accessPolicy;
    private final AuditLog auditLog;

    ProgramService(
            ProgramRepository programs,
            DocumentRepository documents,
            SectionRepository sections,
            CategoryRepository categories,
            DepartmentDirectory departments,
            ContactDirectory contacts,
            AccessPolicy accessPolicy,
            AuditLog auditLog) {
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
    public List<Program> listOwnedBy(AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        return programs.findByOwnerDepartmentIdOrderByCode(user.departmentId());
    }

    @Transactional(readOnly = true)
    public Program get(UUID id, AuthenticatedUser user) {
        Program program = programs.findById(id).orElseThrow(() -> ApiErrors.notFound("制度が見つかりません: " + id));
        accessPolicy.checkCanEditContent(user, program.getOwnerDepartmentId());
        return program;
    }

    public Program create(String code, String ownerDepartmentCode, Attributes attributes, AuthenticatedUser user) {
        UUID owner = departments
                .findByCode(ownerDepartmentCode)
                .orElseThrow(() -> ApiErrors.badRequest("部署 " + ownerDepartmentCode + " が存在しません。"))
                .id();
        accessPolicy.checkCanEditContent(user, owner);
        if (programs.existsByCode(code)) {
            throw ApiErrors.conflict("制度コード " + code + " は既に使われています。");
        }
        Program program = rule(() -> new Program(code, owner, toDomain(attributes)));
        programs.save(program);
        audit(user, "PROGRAM_CREATED", program, Map.of("code", code, "name", program.getName()));
        return program;
    }

    public Program update(UUID id, int expectedRowVersion, Attributes attributes, AuthenticatedUser user) {
        Program program = getForUpdate(id, expectedRowVersion, user);
        rule(() -> {
            program.update(toDomain(attributes));
            return program;
        });
        programs.flush();
        audit(
                user,
                "PROGRAM_UPDATED",
                program,
                Map.of("name", program.getName(), "visibility", program.getVisibility()));
        return program;
    }

    /** 根拠の条をすべて置き換える。条は文書の公開版から探す */
    public Program replaceBasis(UUID id, int expectedRowVersion, List<BasisItem> items, AuthenticatedUser user) {
        Program program = getForUpdate(id, expectedRowVersion, user);
        record Resolved(Document document, Section section, String articleNo) {}
        List<Resolved> resolved = new ArrayList<>();
        for (BasisItem item : items) {
            Document doc = documents
                    .findByCode(item.documentCode())
                    .orElseThrow(() -> ApiErrors.badRequest("文書 " + item.documentCode() + " が存在しません。"));
            Section section = null;
            if (item.articleNo() != null) {
                if (doc.getCurrentVersionId() == null) {
                    throw ApiErrors.conflict("文書「" + doc.getTitle() + "」はまだ公開されていません。");
                }
                section = sections.findByVersionIdAndArticleNo(doc.getCurrentVersionId(), item.articleNo())
                        .orElseThrow(
                                () -> ApiErrors.badRequest(doc.getTitle() + "の公開版に " + item.articleNo() + " がありません。"));
            }
            resolved.add(new Resolved(doc, section, item.articleNo()));
        }
        program.clearBasis();
        // 既存の根拠の削除を先に反映しないと (program, document, article) の一意制約に触れる
        programs.flush();
        rule(() -> {
            for (Resolved r : resolved) {
                program.addBasis(r.document(), r.section(), r.articleNo());
            }
            program.checkInvariantsIfPublished();
            return program;
        });
        programs.flush();
        audit(user, "PROGRAM_BASIS_REPLACED", program, Map.of("count", items.size()));
        return program;
    }

    public Program replaceContacts(UUID id, int expectedRowVersion, List<ContactItem> items, AuthenticatedUser user) {
        Program program = getForUpdate(id, expectedRowVersion, user);
        List<ProgramContact> resolved = new ArrayList<>();
        for (ContactItem item : items) {
            UUID contactId = contacts.findByCode(item.contactCode())
                    .orElseThrow(() -> ApiErrors.badRequest("窓口 " + item.contactCode() + " が存在しません。"))
                    .id();
            resolved.add(new ProgramContact(contactId, item.role(), resolved.size()));
        }
        rule(() -> {
            program.replaceContacts(resolved);
            program.checkInvariantsIfPublished();
            return program;
        });
        programs.flush();
        audit(user, "PROGRAM_CONTACTS_REPLACED", program, Map.of("count", items.size()));
        return program;
    }

    public Program publish(UUID id, int expectedRowVersion, AuthenticatedUser user) {
        Program program = getForUpdate(id, expectedRowVersion, user);
        rule(() -> {
            program.publish();
            return program;
        });
        programs.flush();
        audit(user, "PROGRAM_PUBLISHED", program, Map.of());
        return program;
    }

    public Program archive(UUID id, int expectedRowVersion, AuthenticatedUser user) {
        Program program = getForUpdate(id, expectedRowVersion, user);
        rule(() -> {
            program.archive();
            return program;
        });
        programs.flush();
        audit(user, "PROGRAM_ARCHIVED", program, Map.of());
        return program;
    }

    private Program getForUpdate(UUID id, int expectedRowVersion, AuthenticatedUser user) {
        Program program = get(id, user);
        if (program.getRowVersion() != expectedRowVersion) {
            throw ApiErrors.preconditionFailed();
        }
        return program;
    }

    private Program.Attributes toDomain(Attributes a) {
        return new Program.Attributes(
                a.name(),
                a.nameKana(),
                a.summary(),
                a.eligibility(),
                a.benefit(),
                a.howToApply(),
                a.deadlineNote(),
                a.requiredDocuments(),
                category(a.categorySlug()),
                a.visibility());
    }

    private Category category(String slug) {
        return categories.findBySlug(slug).orElseThrow(() -> ApiErrors.badRequest("カテゴリ " + slug + " が存在しません。"));
    }

    private static <T> T rule(Supplier<T> action) {
        try {
            return action.get();
        } catch (ContentRuleViolation e) {
            throw ApiErrors.conflict(e.getMessage());
        }
    }

    private void audit(AuthenticatedUser user, String action, Program program, Map<String, ?> details) {
        auditLog.record(user.id(), action, "PROGRAM", program.getId(), details);
    }
}
