package com.example.knavi.content.infrastructure;

import com.example.knavi.content.domain.Section;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    Optional<Section> findByVersionIdAndArticleNo(UUID versionId, String articleNo);
}
