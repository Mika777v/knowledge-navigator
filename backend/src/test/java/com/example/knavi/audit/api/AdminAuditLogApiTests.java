package com.example.knavi.audit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.audit.AuditLog;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 監査ログの閲覧 API（#75） */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminAuditLogApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AuditLog auditLog;

    KnaviUserDetails admin;
    UUID editorId;
    UUID document = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        admin = as("SYSTEM_ADMIN");
        editorId = jdbc.sql("""
                        INSERT INTO users (email, display_name, role) VALUES ('editor@example.com', '編集 花子', 'CONTENT_EDITOR')
                        RETURNING id
                        """).query(UUID.class).single();
        auditLog.record(editorId, "TEST_DOCUMENT_CREATED", "DOCUMENT", document, Map.of("code", "REG-TEST"));
        auditLog.record(editorId, "TEST_DOCUMENT_PUBLISHED", "DOCUMENT", document, Map.of("versionLabel", "v1.0"));
        auditLog.record(editorId, "TEST_FAQ_CREATED", "FAQ", UUID.randomUUID(), null);
    }

    @Test
    void listsNewestFirstWithActorAndDetails() throws Exception {
        String body = audit(get("/api/v1/admin/audit-logs").param("actorId", editorId.toString()));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].action"))
                .containsExactly("TEST_FAQ_CREATED", "TEST_DOCUMENT_PUBLISHED", "TEST_DOCUMENT_CREATED");
        assertThat(JsonPath.<String>read(body, "$.items[1].actor.displayName")).isEqualTo("編集 花子");
        assertThat(JsonPath.<String>read(body, "$.items[1].details.versionLabel"))
                .isEqualTo("v1.0");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.items[0].details"))
                .isEmpty();
        assertThat(JsonPath.<Object>read(body, "$.nextCursor")).isNull();
    }

    @Test
    void filtersByActionAndResource() throws Exception {
        String byAction = audit(get("/api/v1/admin/audit-logs").param("action", "TEST_DOCUMENT_PUBLISHED"));
        assertThat(JsonPath.<List<String>>read(byAction, "$.items[*].action"))
                .containsExactly("TEST_DOCUMENT_PUBLISHED");

        String byResource = audit(get("/api/v1/admin/audit-logs")
                .param("resourceType", "DOCUMENT")
                .param("resourceId", document.toString()));
        assertThat(JsonPath.<List<String>>read(byResource, "$.items[*].action"))
                .containsExactly("TEST_DOCUMENT_PUBLISHED", "TEST_DOCUMENT_CREATED");
    }

    @Test
    void pagesWithCursor() throws Exception {
        List<String> actions = new ArrayList<>();
        Integer cursor = null;
        do {
            var request = get("/api/v1/admin/audit-logs")
                    .param("actorId", editorId.toString())
                    .param("size", "2");
            if (cursor != null) {
                request = request.param("cursor", cursor.toString());
            }
            String body = audit(request);
            actions.addAll(JsonPath.read(body, "$.items[*].action"));
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);

        assertThat(actions).containsExactly("TEST_FAQ_CREATED", "TEST_DOCUMENT_PUBLISHED", "TEST_DOCUMENT_CREATED");
    }

    @Test
    void filtersByPeriodInJapanTime() throws Exception {
        String today = audit(get("/api/v1/admin/audit-logs")
                .param("actorId", editorId.toString())
                .param("from", "2000-01-01"));
        assertThat(JsonPath.<List<Object>>read(today, "$.items")).hasSize(3);

        String past = audit(get("/api/v1/admin/audit-logs")
                .param("actorId", editorId.toString())
                .param("from", "2000-01-01")
                .param("to", "2000-12-31"));
        assertThat(JsonPath.<List<Object>>read(past, "$.items")).isEmpty();

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("from", "2026-10-21")
                        .param("to", "2026-10-01")
                        .with(user(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("開始日は終了日以前にしてください。"));
        mockMvc.perform(get("/api/v1/admin/audit-logs").param("size", "201").with(user(admin)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "{0} は監査ログを見られる: {1}")
    @CsvSource({"EMPLOYEE, false", "MANAGER, false", "CONTENT_EDITOR, false", "SYSTEM_ADMIN, true"})
    void onlySystemAdminsCanViewAuditLogs(String role, boolean allowed) throws Exception {
        var who = as(role);
        mockMvc.perform(get("/api/v1/admin/audit-logs").with(user(who)))
                .andExpect(allowed ? status().isOk() : status().isForbidden());
    }

    private String audit(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user(admin)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** その役割のユーザーを登録して返す（セッションのユーザーは、リクエストごとに users テーブルで確かめられるため） */
    private KnaviUserDetails as(String role) {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        UUID id = jdbc.sql("INSERT INTO users (email, display_name, role) VALUES (:e, :e, :r) RETURNING id")
                .param("e", email)
                .param("r", role)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, null, email, null, role, true);
    }
}
