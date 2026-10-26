package com.example.knavi.content.domain;

import com.example.knavi.shared.id.Ids;
import com.example.knavi.shared.security.Visibility;
import com.example.knavi.shared.text.JapaneseNormalizer;
import com.example.knavi.shared.text.TextNormalizer;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * 制度（集約ルート）。根拠の条と窓口を持つ。ADR-0003
 *
 * <p>公開中の制度が満たすべき条件（{@link #checkPublishable()}）
 *
 * <ul>
 *   <li>窓口が1件以上ある（検索結果で必ず問い合わせ先を示すため）
 *   <li>根拠が1件以上あり、すべて公開中の文書を指す
 *   <li>公開範囲が根拠文書より広くない（制度経由で見えてはいけない文書が漏れないように）
 * </ul>
 */
@Entity
@Table(name = "programs")
public class Program implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_kana", length = 200)
    private String nameKana;

    @Column(nullable = false)
    private String summary;

    private String eligibility;

    private String benefit;

    @Column(name = "how_to_apply")
    private String howToApply;

    @Column(name = "deadline_note", length = 200)
    private String deadlineNote;

    @Column(name = "required_documents")
    private String requiredDocuments;

    @ManyToOne(fetch = FetchType.LAZY)
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

    @Column(name = "search_text", nullable = false)
    private String searchText;

    /** 表記ゆれを吸収する検索列（search_text の正規化形。ADR-0011。辞書がなければ空） */
    @Column(name = "search_norm", nullable = false)
    private String searchNorm;

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<ProgramBasis> basis = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "program_contacts", joinColumns = @JoinColumn(name = "program_id"))
    @OrderBy("sortOrder")
    private List<ProgramContact> contacts = new ArrayList<>();

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

    protected Program() {}

    public Program(String code, UUID ownerDepartmentId, Attributes attributes) {
        this.id = Ids.newId();
        this.code = code;
        this.ownerDepartmentId = ownerDepartmentId;
        this.status = ContentStatus.DRAFT;
        update(attributes);
    }

    /** 制度の説明（利用者に表示する内容） */
    public record Attributes(
            String name,
            String nameKana,
            String summary,
            String eligibility,
            String benefit,
            String howToApply,
            String deadlineNote,
            String requiredDocuments,
            Category category,
            Visibility visibility) {}

    public void update(Attributes a) {
        this.name = a.name();
        this.nameKana = a.nameKana();
        this.summary = a.summary();
        this.eligibility = a.eligibility();
        this.benefit = a.benefit();
        this.howToApply = a.howToApply();
        this.deadlineNote = a.deadlineNote();
        this.requiredDocuments = a.requiredDocuments();
        this.category = a.category();
        this.visibility = a.visibility();
        this.searchText = TextNormalizer.normalize(Stream.of(name, nameKana, summary, eligibility, benefit, howToApply)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" ")));
        this.searchNorm = JapaneseNormalizer.normalize(searchText);
        checkInvariantsIfPublished();
    }

    /** 根拠をすべて置き換える。既存の根拠の削除を先に反映するため、呼び出し側で clearBasis → flush → この順に呼ぶ */
    public void addBasis(Document document, Section section, String articleNo) {
        if (section != null && !section.getVersion().getId().equals(document.getCurrentVersionId())) {
            throw new ContentRuleViolation("根拠の条は、文書の公開版のものを指定してください。");
        }
        basis.add(new ProgramBasis(this, document, section, articleNo, basis.size()));
    }

    public void clearBasis() {
        basis.clear();
        // 根拠は子エンティティ側で管理される関連のため、変更しても制度自体は更新扱いにならず版が上がらない。
        // 同時編集を検知できるよう、制度の行を更新して版（row_version）を上げる
        this.updatedAt = Instant.now();
    }

    public void replaceContacts(List<ProgramContact> newContacts) {
        contacts.clear();
        contacts.addAll(newContacts);
    }

    /** 根拠・窓口を変更した後に呼ぶ。公開中なら公開の条件を満たしているか確認する */
    public void checkInvariantsIfPublished() {
        if (status == ContentStatus.PUBLISHED) {
            checkPublishable();
        }
    }

    public void publish() {
        checkPublishable();
        this.status = ContentStatus.PUBLISHED;
    }

    public void archive() {
        if (status == ContentStatus.DRAFT) {
            throw new ContentRuleViolation("一度も公開していない制度はアーカイブできません。");
        }
        this.status = ContentStatus.ARCHIVED;
    }

    void checkPublishable() {
        if (contacts.isEmpty()) {
            throw new ContentRuleViolation("公開する制度には窓口が1件以上必要です。");
        }
        if (basis.isEmpty()) {
            throw new ContentRuleViolation("公開する制度には根拠（文書の条）が1件以上必要です。");
        }
        for (ProgramBasis b : basis) {
            Document doc = b.getDocument();
            if (doc.getStatus() != ContentStatus.PUBLISHED) {
                throw new ContentRuleViolation("根拠の文書「" + doc.getTitle() + "」が公開されていません。");
            }
            if (visibility.isWiderThan(doc.getVisibility())) {
                throw new ContentRuleViolation("公開範囲（" + visibility + "）が根拠の文書「" + doc.getTitle() + "」の公開範囲（"
                        + doc.getVisibility() + "）より広くなっています。");
            }
        }
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

    public String getName() {
        return name;
    }

    public String getNameKana() {
        return nameKana;
    }

    public String getSummary() {
        return summary;
    }

    public String getEligibility() {
        return eligibility;
    }

    public String getBenefit() {
        return benefit;
    }

    public String getHowToApply() {
        return howToApply;
    }

    public String getDeadlineNote() {
        return deadlineNote;
    }

    public String getRequiredDocuments() {
        return requiredDocuments;
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

    public List<ProgramBasis> getBasis() {
        return Collections.unmodifiableList(basis);
    }

    public List<ProgramContact> getContacts() {
        return Collections.unmodifiableList(contacts);
    }

    public int getRowVersion() {
        return rowVersion == null ? 0 : rowVersion;
    }
}
