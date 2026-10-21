package com.example.knavi.content.infrastructure;

import com.example.knavi.content.domain.Faq;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, UUID> {

    boolean existsByCode(String code);

    List<Faq> findByOwnerDepartmentIdOrderByCode(UUID ownerDepartmentId);
}
