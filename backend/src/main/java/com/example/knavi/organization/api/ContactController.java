package com.example.knavi.organization.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 部署・窓口の一覧（社内向けの公開情報のため、ログインユーザー全員が閲覧できる）。
 * API の型名（OpenAPI のスキーマ名）が content の ContactResponse と重ならないよう、Directory を付けている
 */
@Tag(name = "organization", description = "部署・問い合わせ窓口")
@RestController
@RequestMapping("/api/v1")
class ContactController {

    private final JdbcClient jdbc;

    ContactController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record DirectoryContact(
            String code,
            String name,
            String description,
            String email,
            String phoneExt,
            String chatUrl,
            String officeHours,
            boolean primary) {}

    record DirectoryDepartment(
            String code, String name, String description, String parentCode, List<DirectoryContact> contacts) {}

    @Operation(summary = "部署ごとの問い合わせ窓口")
    @GetMapping("/contacts")
    @Transactional(readOnly = true)
    List<DirectoryDepartment> contacts() {
        record Dept(java.util.UUID id, String code, String name, String description, String parentCode) {}
        var departments = jdbc.sql("""
                        SELECT d.id, d.code, d.name, d.description, p.code AS parent_code
                        FROM departments d LEFT JOIN departments p ON p.id = d.parent_id
                        WHERE d.is_active ORDER BY d.sort_order, d.code
                        """)
                .query((rs, i) -> new Dept(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("parent_code")))
                .list();
        return departments.stream()
                .map(d -> new DirectoryDepartment(
                        d.code(),
                        d.name(),
                        d.description(),
                        d.parentCode(),
                        jdbc.sql("""
                                        SELECT code, name, description, email, phone_ext, chat_url, office_hours, is_primary
                                        FROM contacts WHERE department_id = :id ORDER BY is_primary DESC, sort_order, code
                                        """)
                                .param("id", d.id())
                                .query((rs, i) -> new DirectoryContact(
                                        rs.getString("code"),
                                        rs.getString("name"),
                                        rs.getString("description"),
                                        rs.getString("email"),
                                        rs.getString("phone_ext"),
                                        rs.getString("chat_url"),
                                        rs.getString("office_hours"),
                                        rs.getBoolean("is_primary")))
                                .list()))
                .toList();
    }
}
