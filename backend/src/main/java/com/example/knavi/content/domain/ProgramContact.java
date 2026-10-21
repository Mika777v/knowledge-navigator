package com.example.knavi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

/** 制度の窓口（窓口は organization モジュールが管理するため ID で持つ） */
@Embeddable
public record ProgramContact(
        @Column(name = "contact_id", nullable = false) UUID contactId,

        @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false, length = 20)
        ContactRole role,

        @Column(name = "sort_order", nullable = false) int sortOrder) {}
