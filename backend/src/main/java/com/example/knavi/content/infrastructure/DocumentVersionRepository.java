package com.example.knavi.content.infrastructure;

import com.example.knavi.content.domain.DocumentVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    boolean existsByDocumentIdAndVersionLabel(UUID documentId, String versionLabel);
}
