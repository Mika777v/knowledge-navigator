package com.example.knavi.content.domain;

import com.example.knavi.shared.id.Ids;
import com.example.knavi.shared.text.JapaneseNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** 版を条・見出し単位に分割したもの。検索・引用・Embedding の最小単位 */
@Entity
@Table(name = "sections")
public class Section {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_version_id")
    private DocumentVersion version;

    @Column(nullable = false)
    private int ordinal;

    @Column(length = 50)
    private String path;

    @Column(name = "article_no", length = 30)
    private String articleNo;

    @Column(length = 200)
    private String heading;

    @Column(nullable = false)
    private String content;

    @Column(name = "page_from")
    private Integer pageFrom;

    @Column(name = "page_to")
    private Integer pageTo;

    @Column(name = "search_text", nullable = false)
    private String searchText;

    /** 表記ゆれを吸収する検索列（search_text の正規化形。ADR-0011。辞書がなければ空） */
    @Column(name = "search_norm", nullable = false)
    private String searchNorm;

    protected Section() {}

    Section(DocumentVersion version, SectionDraft draft, String searchText) {
        this.id = Ids.newId();
        this.version = version;
        this.ordinal = draft.ordinal();
        this.path = draft.path();
        this.articleNo = draft.articleNo();
        this.heading = draft.heading();
        this.content = draft.content();
        this.pageFrom = draft.pageFrom();
        this.pageTo = draft.pageTo();
        this.searchText = searchText;
        this.searchNorm = JapaneseNormalizer.normalize(searchText);
    }

    public UUID getId() {
        return id;
    }

    public DocumentVersion getVersion() {
        return version;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getPath() {
        return path;
    }

    public String getArticleNo() {
        return articleNo;
    }

    public String getHeading() {
        return heading;
    }

    public String getContent() {
        return content;
    }

    public Integer getPageFrom() {
        return pageFrom;
    }

    public Integer getPageTo() {
        return pageTo;
    }

    public String getSearchText() {
        return searchText;
    }
}
