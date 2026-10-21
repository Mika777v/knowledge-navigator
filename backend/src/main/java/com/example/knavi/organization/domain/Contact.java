package com.example.knavi.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** 問い合わせ窓口（参照用。管理機能は組織マスタの Issue で追加する） */
@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 100)
    private String name;

    protected Contact() {}

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getName() {
        return name;
    }
}
