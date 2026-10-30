package com.example.knavi.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.assistant.ConversationRecorder.Citation;
import com.example.knavi.assistant.ConversationRecorder.Reply;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 会話の記録（ADR-0014 の決定 3）。記録は別のトランザクションで書くため、このテストはトランザクションの外で動かし、後で消す。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConversationRecordTests {

    @Autowired
    ConversationService service;

    @Autowired
    JdbcClient jdbc;

    UUID dept;
    UUID sato;
    UUID suzuki;

    @BeforeEach
    void setUp() {
        dept = jdbc.sql("INSERT INTO departments (code, name) VALUES (:c, 'テスト部') RETURNING id")
                .param("c", "REC-" + UUID.randomUUID().toString().substring(0, 8))
                .query(UUID.class)
                .single();
        sato = user("sato");
        suzuki = user("suzuki");
    }

    @AfterEach
    void tearDown() {
        // 会話・メッセージ・根拠は利用者と一緒に消える（ON DELETE CASCADE）
        jdbc.sql("DELETE FROM users WHERE department_id = :d").param("d", dept).update();
        jdbc.sql("DELETE FROM departments WHERE id = :d").param("d", dept).update();
    }

    @Test
    void startsAConversationAndContinuesIt() {
        UUID id = service.record(sato, null, "結婚祝金はいくら？", Reply.answered(
                        "30,000円です。[1]", List.of(new Citation(1, null)), "qwen3.5:4b"))
                .orElseThrow();
        Optional<UUID> continued = service.record(sato, id, "申請先は？", Reply.unanswerable("NO_EVIDENCE"));

        assertThat(continued).contains(id);
        assertThat(jdbc.sql("SELECT title FROM chat_sessions WHERE id = :id")
                        .param("id", id)
                        .query(String.class)
                        .single())
                .isEqualTo("結婚祝金はいくら？");
        record Row(int seq, String role, String content, Boolean answerable, String reason) {}
        assertThat(jdbc.sql("SELECT seq, role, content, answerable, unanswerable_reason FROM chat_messages"
                                + " WHERE session_id = :id ORDER BY seq")
                        .param("id", id)
                        .query((rs, i) -> new Row(
                                rs.getInt(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getObject(4, Boolean.class),
                                rs.getString(5)))
                        .list())
                .containsExactly(
                        new Row(1, "USER", "結婚祝金はいくら？", null, null),
                        new Row(2, "ASSISTANT", "30,000円です。[1]", true, null),
                        new Row(3, "USER", "申請先は？", null, null),
                        new Row(4, "ASSISTANT", "", false, "NO_EVIDENCE"));
        assertThat(jdbc.sql("SELECT count(*) FROM chat_citations c JOIN chat_messages m ON m.id = c.message_id"
                                + " WHERE m.session_id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void doesNotAppendToSomeoneElsesConversation() {
        UUID theirs = service.record(suzuki, null, "鈴木さんの質問", Reply.unanswerable("NO_EVIDENCE"))
                .orElseThrow();

        assertThat(service.record(sato, theirs, "佐藤さんの質問", Reply.unanswerable("NO_EVIDENCE")))
                .isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM chat_messages WHERE session_id = :id")
                        .param("id", theirs)
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
    }

    private UUID user(String name) {
        return jdbc.sql("INSERT INTO users (email, display_name, department_id, role)"
                        + " VALUES (:e, :n, :d, 'EMPLOYEE') RETURNING id")
                .param("e", name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .param("n", name)
                .param("d", dept)
                .query(UUID.class)
                .single();
    }
}
