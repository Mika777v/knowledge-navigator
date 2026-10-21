package com.example.knavi.content.domain;

import com.example.knavi.shared.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** 制度の根拠（文書の条） */
@Entity
@Table(name = "program_basis")
public class ProgramBasis {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(name = "article_no", length = 30)
    private String articleNo;

    @Column(length = 200)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProgramBasis() {}

    ProgramBasis(Program program, Document document, Section section, String articleNo, int sortOrder) {
        this.id = Ids.newId();
        this.program = program;
        this.document = document;
        this.section = section;
        this.articleNo = articleNo;
        this.sortOrder = sortOrder;
    }

    public Document getDocument() {
        return document;
    }

    public Section getSection() {
        return section;
    }

    public String getArticleNo() {
        return articleNo;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
