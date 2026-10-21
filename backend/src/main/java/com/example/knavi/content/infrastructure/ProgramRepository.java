package com.example.knavi.content.infrastructure;

import com.example.knavi.content.domain.Program;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, UUID> {

    boolean existsByCode(String code);

    Optional<Program> findByCode(String code);

    List<Program> findByOwnerDepartmentIdOrderByCode(UUID ownerDepartmentId);
}
