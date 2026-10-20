package com.example.knavi.organization.api;

import com.example.knavi.organization.domain.User;
import java.time.LocalDate;
import java.util.UUID;

/** ログイン中のユーザー情報（GET /api/v1/me） */
public record MeResponse(
        UUID id,
        String email,
        String displayName,
        DepartmentSummary department,
        String role,
        String employmentType,
        LocalDate joinedOn) {

    public record DepartmentSummary(String code, String name) {}

    static MeResponse from(User user) {
        var dept = user.getDepartment();
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                dept == null ? null : new DepartmentSummary(dept.getCode(), dept.getName()),
                user.getRole().name(),
                user.getEmploymentType().name(),
                user.getJoinedOn());
    }
}
