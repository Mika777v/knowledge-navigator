package com.example.knavi.organization;

import java.util.Optional;
import java.util.UUID;

/** 他のモジュールが部署を参照するための公開 API */
public interface DepartmentDirectory {

    Optional<DepartmentRef> findByCode(String code);

    Optional<DepartmentRef> findById(UUID id);

    record DepartmentRef(UUID id, String code, String name) {}
}
