package com.example.knavi.organization.infrastructure;

import com.example.knavi.organization.DepartmentDirectory;
import com.example.knavi.organization.domain.Department;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JpaDepartmentDirectory implements DepartmentDirectory {

    private final DepartmentRepository departments;

    JpaDepartmentDirectory(DepartmentRepository departments) {
        this.departments = departments;
    }

    @Override
    public Optional<DepartmentRef> findByCode(String code) {
        return departments.findByCode(code).map(JpaDepartmentDirectory::toRef);
    }

    @Override
    public Optional<DepartmentRef> findById(UUID id) {
        return departments.findById(id).map(JpaDepartmentDirectory::toRef);
    }

    private static DepartmentRef toRef(Department d) {
        return new DepartmentRef(d.getId(), d.getCode(), d.getName());
    }
}
