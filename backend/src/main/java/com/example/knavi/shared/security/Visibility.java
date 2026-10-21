package com.example.knavi.shared.security;

/** コンテンツの公開範囲。docs/PRD_KNOWLEDGE_NAVIGATOR.md §7.2、ADR-0004 */
public enum Visibility {
    /** ログインユーザー全員 */
    INTERNAL,
    /** 管理職以上 */
    MANAGERS,
    /** access_rules で許可された部署・ロール・ユーザー */
    RESTRICTED,
    /** 明示的に許可されたユーザーのみ。検索・Embedding の対象外 */
    CONFIDENTIAL;

    /**
     * この公開範囲が other より広い（より多くの人に見える）か。
     *
     * <p>制度・FAQ の公開範囲は、根拠文書より広くしてはいけない（根拠経由で見えてはいけない文書が漏れるため）。
     */
    public boolean isWiderThan(Visibility other) {
        return ordinal() < other.ordinal();
    }
}
