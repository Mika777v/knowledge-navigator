package com.example.knavi.content.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 管理 API を、テスト用のトランザクションの外から呼ぶ（実際の HTTP リクエストと同じ条件）。
 *
 * <p>他のテストは @Transactional で動くため、エンティティの遅延読み込みがテストのトランザクションの中で成功してしまい、
 * 本番でだけ起きる LazyInitializationException（500）を見逃していた。ここではデータを実際にコミットし、終わったら消す。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminContentApiOutsideTransactionTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    UUID dept;
    UUID category;
    UUID contact;
    UUID document;
    UUID program;
    UUID faq;
    KnaviUserDetails editor;

    @BeforeEach
    void setUp() {
        dept = id("INSERT INTO departments (code, name) VALUES ('LZ', '遅延テスト部') RETURNING id");
        category = id("INSERT INTO categories (slug, name) VALUES ('lazy-test', '遅延テスト') RETURNING id");
        contact = id(
                "INSERT INTO contacts (code, department_id, name, phone_ext) VALUES ('LZ-DESK', '%s', '遅延テスト窓口', '9999') RETURNING id"
                        .formatted(dept));
        UUID userId = id(
                "INSERT INTO users (email, display_name, department_id, role) VALUES ('lazy@example.com', '遅延 テスト', '%s', 'CONTENT_EDITOR') RETURNING id"
                        .formatted(dept));
        editor = new KnaviUserDetails(userId, dept, "lazy@example.com", null, "CONTENT_EDITOR", true);
        document = id("""
                INSERT INTO documents (code, title, document_type, category_id, owner_department_id)
                VALUES ('REG-LZ-001', '遅延テスト規程', 'RULE', '%s', '%s') RETURNING id
                """.formatted(category, dept));
        program = id("""
                INSERT INTO programs (code, name, summary, category_id, owner_department_id, search_text)
                VALUES ('PRG-LZ', '遅延テスト制度', '説明', '%s', '%s', '遅延テスト制度') RETURNING id
                """.formatted(category, dept));
        jdbc.sql("INSERT INTO program_basis (program_id, document_id, article_no) VALUES (:p, :d, '第1条')")
                .param("p", program)
                .param("d", document)
                .update();
        jdbc.sql("INSERT INTO program_contacts (program_id, contact_id) VALUES (:p, :c)")
                .param("p", program)
                .param("c", contact)
                .update();
        faq = id("""
                INSERT INTO faqs (code, question, answer_markdown, category_id, contact_id, owner_department_id, search_text)
                VALUES ('FAQ-LZ', '遅延テスト？', '回答', '%s', '%s', '%s', '遅延テスト') RETURNING id
                """.formatted(category, contact, dept));
        jdbc.sql("INSERT INTO faq_links (faq_id, target_type, target_id) VALUES (:f, 'PROGRAM', :p)")
                .param("f", faq)
                .param("p", program)
                .update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM faqs WHERE id = :id").param("id", faq).update();
        jdbc.sql("DELETE FROM programs WHERE id = :id").param("id", program).update();
        jdbc.sql("DELETE FROM documents WHERE id = :id").param("id", document).update();
        jdbc.sql("DELETE FROM users WHERE email = 'lazy@example.com'").update();
        jdbc.sql("DELETE FROM contacts WHERE id = :id").param("id", contact).update();
        jdbc.sql("DELETE FROM categories WHERE id = :id").param("id", category).update();
        jdbc.sql("DELETE FROM departments WHERE id = :id").param("id", dept).update();
    }

    @Test
    void faqListAndDetailIncludeLinksAndCategory() throws Exception {
        mockMvc.perform(get("/api/v1/admin/faqs").with(user(editor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category.slug").value("lazy-test"))
                .andExpect(jsonPath("$[0].links[0].programCode").value("PRG-LZ"));
        mockMvc.perform(get("/api/v1/admin/faqs/" + faq).with(user(editor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links[0].label").value("遅延テスト制度"));
    }

    @Test
    void programListAndDetailIncludeBasisAndContacts() throws Exception {
        mockMvc.perform(get("/api/v1/admin/programs").with(user(editor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].basis[0].documentCode").value("REG-LZ-001"));
        mockMvc.perform(get("/api/v1/admin/programs/" + program).with(user(editor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.slug").value("lazy-test"))
                .andExpect(jsonPath("$.basis[0].articleNo").value("第1条"));
    }

    private UUID id(String sql) {
        return jdbc.sql(sql).query(UUID.class).single();
    }
}
