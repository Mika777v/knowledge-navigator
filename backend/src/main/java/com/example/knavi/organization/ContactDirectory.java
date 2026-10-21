package com.example.knavi.organization;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 他のモジュールが問い合わせ窓口を参照するための公開 API */
public interface ContactDirectory {

    Optional<ContactRef> findByCode(String code);

    Optional<ContactRef> findById(UUID id);

    List<ContactRef> findByIds(Collection<UUID> ids);

    record ContactRef(UUID id, String code, String name, UUID departmentId) {}
}
