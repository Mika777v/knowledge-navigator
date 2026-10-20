package com.example.knavi.content.infrastructure;

import com.example.knavi.content.domain.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    boolean existsByCode(String code);

    Optional<Document> findByCode(String code);

    @EntityGraph(attributePaths = "category")
    List<Document> findByOwnerDepartmentIdOrderByCode(UUID ownerDepartmentId);

    @EntityGraph(attributePaths = "category")
    Optional<Document> findWithCategoryById(UUID id);
}
