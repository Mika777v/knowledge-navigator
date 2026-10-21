package com.example.knavi.organization.api;

import com.example.knavi.organization.application.OrganizationAdminService;
import com.example.knavi.organization.application.OrganizationAdminService.ContactInput;
import com.example.knavi.organization.application.OrganizationAdminService.ContactView;
import com.example.knavi.organization.application.OrganizationAdminService.DepartmentInput;
import com.example.knavi.organization.application.OrganizationAdminService.DepartmentView;
import com.example.knavi.organization.application.OrganizationAdminService.UserInput;
import com.example.knavi.organization.application.OrganizationAdminService.UserView;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ETags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 組織マスタ（部署・窓口・ユーザー）の管理。システム管理者だけが使える。更新は If-Match 必須 */
@Tag(name = "admin-organization", description = "部署・窓口・ユーザーの管理（システム管理者）")
@RestController
@RequestMapping("/api/v1/admin")
class AdminOrganizationController {

    private static final String CODE = "[A-Z0-9_-]+";
    private static final String CODE_MESSAGE = "英大文字・数字・ハイフン・下線で入力してください";

    private final OrganizationAdminService service;

    AdminOrganizationController(OrganizationAdminService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ 部署

    record DepartmentRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            String parentCode,
            @Min(0) @Max(10000) int sortOrder,
            boolean active) {

        DepartmentInput toInput() {
            return new DepartmentInput(name, description, parentCode, sortOrder, active);
        }
    }

    record CreateDepartmentRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = CODE, message = CODE_MESSAGE)
            String code,

            @NotNull @Valid DepartmentRequest content) {}

    @Operation(summary = "部署の一覧")
    @GetMapping("/departments")
    List<DepartmentView> departments(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.departments(user);
    }

    @Operation(summary = "部署を作成する")
    @PostMapping("/departments")
    ResponseEntity<DepartmentView> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        DepartmentView created =
                service.createDepartment(body.code(), body.content().toInput(), user);
        return ResponseEntity.created(URI.create("/api/v1/admin/departments/" + created.code()))
                .eTag(ETags.of(created.rowVersion()))
                .body(created);
    }

    @Operation(summary = "部署を更新する（上位の部署・表示順・有効/無効を含む）")
    @PutMapping("/departments/{code}")
    ResponseEntity<DepartmentView> updateDepartment(
            @PathVariable String code,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody DepartmentRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        DepartmentView updated = service.updateDepartment(code, ETags.parseIfMatch(ifMatch), body.toInput(), user);
        return ResponseEntity.ok().eTag(ETags.of(updated.rowVersion())).body(updated);
    }

    // ------------------------------------------------------------------ 窓口

    record ContactRequest(
            @NotBlank String departmentCode,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Email @Size(max = 255) String email,
            @Size(max = 20) String phoneExt,
            @Size(max = 500) String chatUrl,
            @Size(max = 100) String officeHours,
            boolean primary,
            @Min(0) @Max(10000) int sortOrder) {

        ContactInput toInput() {
            return new ContactInput(
                    departmentCode, name, description, email, phoneExt, chatUrl, officeHours, primary, sortOrder);
        }
    }

    record CreateContactRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = CODE, message = CODE_MESSAGE)
            String code,

            @NotNull @Valid ContactRequest content) {}

    @Operation(summary = "窓口の一覧")
    @GetMapping("/contacts")
    List<ContactView> contacts(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.contacts(user);
    }

    @Operation(summary = "窓口を作成する（代表窓口にすると、その部署の前の代表窓口は外れる）")
    @PostMapping("/contacts")
    ResponseEntity<ContactView> createContact(
            @Valid @RequestBody CreateContactRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        ContactView created = service.createContact(body.code(), body.content().toInput(), user);
        return ResponseEntity.created(URI.create("/api/v1/admin/contacts/" + created.code()))
                .eTag(ETags.of(created.rowVersion()))
                .body(created);
    }

    @Operation(summary = "窓口を更新する")
    @PutMapping("/contacts/{code}")
    ResponseEntity<ContactView> updateContact(
            @PathVariable String code,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ContactRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        ContactView updated = service.updateContact(code, ETags.parseIfMatch(ifMatch), body.toInput(), user);
        return ResponseEntity.ok().eTag(ETags.of(updated.rowVersion())).body(updated);
    }

    // ------------------------------------------------------------------ ユーザー

    record UserRequest(
            @NotBlank @Size(max = 100) String displayName,
            String departmentCode,

            @NotNull @Pattern(regexp = "EMPLOYEE|MANAGER|CONTENT_EDITOR|SYSTEM_ADMIN")
            String role,

            @NotNull @Pattern(regexp = "REGULAR|CONTRACT|PART_TIME")
            String employmentType,

            LocalDate joinedOn,
            boolean active) {

        UserInput toInput() {
            return new UserInput(displayName, departmentCode, role, employmentType, joinedOn, active);
        }
    }

    record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,

            @NotBlank @Size(min = OrganizationAdminService.MIN_PASSWORD_LENGTH, max = 200)
            String initialPassword,

            @NotNull @Valid UserRequest content) {}

    @Operation(summary = "ユーザーの一覧（パスワードは返さない）")
    @GetMapping("/users")
    List<UserView> users(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.users(user);
    }

    @Operation(summary = "ユーザーを作成する（初期パスワードは8文字以上）")
    @PostMapping("/users")
    ResponseEntity<UserView> createUser(
            @Valid @RequestBody CreateUserRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        UserView created = service.createUser(
                body.email(), body.initialPassword(), body.content().toInput(), user);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id()))
                .eTag(ETags.of(created.rowVersion()))
                .body(created);
    }

    @Operation(summary = "ユーザーを更新する（ロール・所属・有効/無効。ロールの変更は次のログインから効く）")
    @PutMapping("/users/{id}")
    ResponseEntity<UserView> updateUser(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UserRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        UserView updated = service.updateUser(id, ETags.parseIfMatch(ifMatch), body.toInput(), user);
        return ResponseEntity.ok().eTag(ETags.of(updated.rowVersion())).body(updated);
    }
}
