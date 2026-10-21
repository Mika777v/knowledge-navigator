package com.example.knavi.navigation.infrastructure;

import com.example.knavi.navigation.domain.KeywordTargetType;
import com.example.knavi.navigation.domain.NavigationKeyword;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NavigationKeywordRepository extends JpaRepository<NavigationKeyword, UUID> {

    List<NavigationKeyword> findByTargetTypeAndTargetIdOrderByNormalizedTerm(KeywordTargetType type, UUID targetId);

    List<NavigationKeyword> findByNormalizedTermOrderByWeightDesc(String normalizedTerm);

    boolean existsByNormalizedTermAndTargetTypeAndTargetId(
            String normalizedTerm, KeywordTargetType type, UUID targetId);
}
