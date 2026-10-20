package com.example.knavi.organization.infrastructure;

import com.example.knavi.organization.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "department")
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "department")
    Optional<User> findWithDepartmentById(UUID id);
}
