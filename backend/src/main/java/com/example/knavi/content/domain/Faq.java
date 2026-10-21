package com.example.knavi.content.domain;

import com.example.knavi.shared.id.Ids;
import com.example.knavi.shared.security.Visibility;
import com.example.knavi.shared.text.TextNormalizer;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * よくある質問。公開するには「窓口」か「関連制度」のどちらかが必要（回答で解決しないときの行き先を必ず示すため）。
 *
 * <p>関連先の文書との公開範囲の関係はアプリケーション層で確認する（{@link #checkPublishable(boolean)}）。
 */
@Entity
@Table(name = "faqs")
public class Faq implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false)
    private String question;

    @Column(name = "answer_markdown", nullable = false)
    private String answerMarkdown;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "owner_department_id", nullable = false)
    private UUID ownerDepartmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @Column(name = "search_text", nullable = false)
    private String searchText;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @ElementCollection
    @CollectionTable(name = "faq_links", joinColumns = @JoinColumn(name = "faq_id"))
    private Set<FaqLink> links = new LinkedHashSet<>();

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    protected Faq() {}

    public Faq(String code, UUID ownerDepartmentId) {
        this.id = Ids.newId();
        this.code = code;
        this.ownerDepartmentId = ownerDepartmentId;
        this.status = ContentStatus.DRAFT;
    }

    public void update(
            String question,
            String answerMarkdown,
            Category category,
            UUID contactId,
            Visibility visibility,
            List<FaqLink> newLinks) {
        this.question = question;
        this.answerMarkdown = answerMarkdown;
        this.category = category;
        this.contactId = contactId;
        this.visibility = visibility;
        this.searchText = TextNormalizer.normalize(question + " " + answerMarkdown);
        this.links.clear();
        this.links.addAll(newLinks);
    }

    /** @param linkedDocumentsAreWider 関連先の文書・条の公開範囲がすべてこの FAQ 以上に広いか（アプリ層で判定） */
    public void publish(boolean linkedDocumentsAreWider) {
        checkPublishable(linkedDocumentsAreWider);
        this.status = ContentStatus.PUBLISHED;
    }

    public void checkPublishable(boolean linkedDocumentsAreWider) {
        boolean hasProgram = links.stream().anyMatch(l -> l.targetType() == LinkTargetType.PROGRAM);
        if (contactId == null && !hasProgram) {
            throw new ContentRuleViolation("公開する FAQ には窓口か関連制度のどちらかが必要です。");
        }
        if (!linkedDocumentsAreWider) {
            throw new ContentRuleViolation("FAQ の公開範囲が、関連する文書の公開範囲より広くなっています。");
        }
    }

    public void archive() {
        if (status == ContentStatus.DRAFT) {
            throw new ContentRuleViolation("一度も公開していない FAQ はアーカイブできません。");
        }
        this.status = ContentStatus.ARCHIVED;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    // persist() の時点で新規でなくなる（INSERT は flush まで遅れるため、PostPersist だけでは delete() などが新規と誤判定する）
    @PrePersist
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

    public String getQuestion() {
        return question;
    }

    public String getAnswerMarkdown() {
        return answerMarkdown;
    }

    public Category getCategory() {
        return category;
    }

    public UUID getContactId() {
        return contactId;
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

    public Set<FaqLink> getLinks() {
        return Collections.unmodifiableSet(links);
    }

    public int getRowVersion() {
        return rowVersion == null ? 0 : rowVersion;
    }
}
