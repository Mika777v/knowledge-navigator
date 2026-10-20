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
    CONFIDENTIAL
}
