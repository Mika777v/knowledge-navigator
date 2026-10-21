package com.example.knavi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

@Embeddable
public record FaqLink(
        @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 20)
        LinkTargetType targetType,

        @Column(name = "target_id", nullable = false) UUID targetId) {}
