package com.example.knavi.content.domain;

import com.example.knavi.shared.id.Ids;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 文書のある時点の内容。公開された版の本文とセクションは変更できない（改定は新しい版で行う）。
 * 過去の版を残すことで、AI 回答の引用（どの版の何条か）を後から検証できる。
 */
@Entity
@Table(name = "document_versions")
public class DocumentVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "version_label", nullable = false, length = 20)
    private String versionLabel;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "body_markdown")
    private String bodyMarkdown;

    @Column(name = "source_file_id")
    private UUID sourceFileId;

    @Column(name = "change_note")
    private String changeNote;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal")
    private List<Section> sections = new ArrayList<>();

    protected DocumentVersion() {}

    DocumentVersion(Document document, String versionLabel, LocalDate effectiveFrom, String changeNote) {
        this.id = Ids.newId();
        this.document = document;
        this.versionLabel = versionLabel;
        this.effectiveFrom = effectiveFrom;
        this.changeNote = changeNote;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    /**
     * 本文を設定し、セクションを作り直す。公開済みの版は変更できない。
     *
     * <p>既存のセクションを消してから追加すると (version, ordinal) の一意制約に触れるため、
     * 呼び出し側で {@link #clearSections()} → flush → この メソッドの順に呼ぶ。
     */
    public void replaceBody(String markdown, List<SectionDraft> drafts, Function<SectionDraft, String> searchText) {
        ensureEditable();
        this.bodyMarkdown = markdown;
        for (SectionDraft draft : drafts) {
            sections.add(new Section(this, draft, searchText.apply(draft)));
        }
    }

    public void clearSections() {
        ensureEditable();
        sections.clear();
    }

    public void updateAttributes(LocalDate effectiveFrom, String changeNote) {
        ensureEditable();
        this.effectiveFrom = effectiveFrom;
        this.changeNote = changeNote;
    }

    void markPublished(Instant at) {
        if (publishedAt == null) {
            publishedAt = at;
        }
    }

    private void ensureEditable() {
        if (isPublished()) {
            throw new ContentRuleViolation("公開済みの版は変更できません。改定する場合は新しい版を作成してください。");
        }
    }

    public UUID getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getBodyMarkdown() {
        return bodyMarkdown;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Section> getSections() {
        return Collections.unmodifiableList(sections);
    }
}
