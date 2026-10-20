package com.example.knavi.content.domain;

/** コンテンツの業務ルール（不変条件）に反する操作。API では 409 Conflict になる */
public class ContentRuleViolation extends RuntimeException {

    public ContentRuleViolation(String message) {
        super(message);
    }
}
