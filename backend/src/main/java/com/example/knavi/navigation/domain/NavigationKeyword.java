package com.example.knavi.navigation.domain;

import com.example.knavi.shared.id.Ids;
import com.example.knavi.shared.text.TextNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * 利用者の言葉から案内先への直接の対応（例: 「入籍」→ きっかけ「結婚」）。
 * 検索時は利用者の入力にも同じ正規化をかけて normalizedTerm と照合する。
 */
@Entity
@Table(name = "navigation_keywords")
public class NavigationKeyword implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String term;

    @Column(name = "normalized_term", nullable = false, length = 100)
    private String normalizedTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private KeywordTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(nullable = false)
    private float weight;

    @Transient
    private boolean isNew = true;

    protected NavigationKeyword() {}

    public NavigationKeyword(String term, KeywordTargetType targetType, UUID targetId, float weight) {
        this.id = Ids.newId();
        this.term = term.strip();
        this.normalizedTerm = TextNormalizer.normalize(term);
        this.targetType = targetType;
        this.targetId = targetId;
        this.weight = weight;
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

    public String getTerm() {
        return term;
    }

    public String getNormalizedTerm() {
        return normalizedTerm;
    }

    public KeywordTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public float getWeight() {
        return weight;
    }
}
