package com.example.knavi.organization.domain;

/** システムロール。docs/PRD_KNOWLEDGE_NAVIGATOR.md §7 */
public enum Role {
    /** 一般社員・新入社員 */
    EMPLOYEE,
    /** 管理職（管理職限定のコンテンツを閲覧できる） */
    MANAGER,
    /** 人事・総務などのコンテンツ担当（所管部署のコンテンツのみ編集できる） */
    CONTENT_EDITOR,
    /** 情報システム担当（ユーザー・権限・監査を管理する。職務分離のためコンテンツは編集できない） */
    SYSTEM_ADMIN
}
