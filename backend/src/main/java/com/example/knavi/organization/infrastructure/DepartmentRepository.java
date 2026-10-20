package com.example.knavi.organization.infrastructure;

import com.example.knavi.organization.domain.Department;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByCode(String code);
}
