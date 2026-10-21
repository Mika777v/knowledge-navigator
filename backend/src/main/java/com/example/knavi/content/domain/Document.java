package com.example.knavi.content.domain;

import com.example.knavi.shared.id.Ids;
import com.example.knavi.shared.security.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * 文書（集約ルート）。版（{@link DocumentVersion}）を持ち、公開中の版は最大1つ。
 *
 * <p>不変条件
 *
 * <ul>
 *   <li>公開中の文書は公開版（currentVersionId）を持つ
 *   <li>公開できるのは、この文書自身の版だけ
 *   <li>公開した版の本文は変更できない（{@link DocumentVersion}）
 * </ul>
 */
@Entity
@Table(name = "documents")
public class Document implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "owner_department_id", nullable = false)
    private UUID ownerDepartmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "review_due_on")
    private LocalDate reviewDueOn;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** ID をアプリで採番するため、新規かどうかを明示する（DocumentVersion と同じ理由） */
    @Transient
    private boolean isNew = true;

    protected Document() {}

    public Document(
            String code,
            String title,
            String summary,
            DocumentType documentType,
            Category category,
            UUID ownerDepartmentId,
            Visibility visibility,
            LocalDate reviewDueOn,
            UUID actor) {
        this.id = Ids.newId();
        this.code = code;
        this.status = ContentStatus.DRAFT;
        this.ownerDepartmentId = ownerDepartmentId;
        this.createdBy = actor;
        update(title, summary, documentType, category, visibility, reviewDueOn, actor);
    }

    public void update(
            String title,
            String summary,
            DocumentType documentType,
            Category category,
            Visibility visibility,
            LocalDate reviewDueOn,
            UUID actor) {
        this.title = title;
        this.summary = summary;
        this.documentType = documentType;
        this.category = category;
        this.visibility = visibility;
        this.reviewDueOn = reviewDueOn;
        this.updatedBy = actor;
    }

    public DocumentVersion newVersion(String versionLabel, LocalDate effectiveFrom, String changeNote) {
        return new DocumentVersion(this, versionLabel, effectiveFrom, changeNote);
    }

    /** 指定した版を公開版にする。以前の公開版は履歴として残る */
    public void publish(DocumentVersion version, Instant at, UUID actor) {
        if (!version.getDocument().getId().equals(id)) {
            throw new ContentRuleViolation("他の文書の版は公開できません。");
        }
        if (version.getSections().isEmpty()) {
            throw new ContentRuleViolation("本文（セクション）が空の版は公開できません。");
        }
        version.markPublished(at);
        this.currentVersionId = version.getId();
        this.status = ContentStatus.PUBLISHED;
        this.updatedBy = actor;
    }

    /** 公開をやめる。版の履歴は残す */
    public void archive(UUID actor) {
        if (status == ContentStatus.DRAFT) {
            throw new ContentRuleViolation("一度も公開していない文書はアーカイブできません。");
        }
        this.status = ContentStatus.ARCHIVED;
        this.updatedBy = actor;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public Category getCategory() {
        return category;
    }

    public UUID getOwnerDepartmentId() {
        return ownerDepartmentId;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public LocalDate getReviewDueOn() {
        return reviewDueOn;
    }

    public int getRowVersion() {
        return rowVersion == null ? 0 : rowVersion;
    }
}
