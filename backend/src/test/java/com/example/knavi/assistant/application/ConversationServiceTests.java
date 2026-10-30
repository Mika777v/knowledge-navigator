package com.example.knavi.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.assistant.application.ConversationViews.ConversationDetail;
import com.example.knavi.assistant.application.ConversationViews.ConversationEvidence;
import com.example.knavi.assistant.application.ConversationViews.ConversationMessage;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

/**
 * 会話の履歴の読み出し・削除・保持期間（ADR-0014 の決定 3）。
 * 本人の会話だけが見え、根拠は開いた時点の権限で読み直す（見られない根拠は数だけ）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConversationServiceTests {

    @Autowired
    ConversationService service;

    @Autowired
    JdbcClient jdbc;

    UUID dept;
    UUID category;
    KnaviUserDetails sato;
    KnaviUserDetails suzuki;

    @BeforeEach
    void setUp() {
        dept = jdbc.sql("INSERT INTO departments (code, name) VALUES (:c, 'テスト部') RETURNING id")
                .param("c", "CHAT-" + UUID.randomUUID().toString().substring(0, 8))
                .query(UUID.class)
                .single();
        category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:s, 'テスト') RETURNING id")
                .param("s", "chat-" + UUID.randomUUID())
                .query(UUID.class)
                .single();
        sato = user("sato");
        suzuki = user("suzuki");
    }

    @Test
    void listsOnlyTheUsersOwnConversationsNewestFirst() {
        UUID older = conversation(sato, "結婚祝金はいくら？", "2 days");
        UUID newer = conversation(sato, "育休は何日？", "1 day");
        conversation(suzuki, "鈴木さんの質問", "1 hour");

        assertThat(service.list(sato))
                .extracting(ConversationViews.ConversationSummary::id)
                .containsExactly(newer, older);
    }

    @Test
    void otherUsersConversationsAreNotFoundAndCannotBeDeleted() {
        UUID theirs = conversation(suzuki, "鈴木さんの質問", "1 hour");

        assertThat(service.isOwnedBy(theirs, sato.id())).isFalse();
        assertThat(service.isOwnedBy(theirs, suzuki.id())).isTrue();
        assertThatThrownBy(() -> service.get(theirs, sato)).isInstanceOf(ErrorResponseException.class);
        assertThatThrownBy(() -> service.delete(theirs, sato)).isInstanceOf(ErrorResponseException.class);
        assertThat(exists(theirs)).isTrue();
    }

    @Test
    void deletesOwnConversationWithItsMessages() {
        UUID mine = conversation(sato, "結婚祝金はいくら？", "1 hour");
        UUID answer = message(mine, 2, "ASSISTANT", "30,000円です。[1]", true);

        service.delete(mine, sato);

        assertThat(exists(mine)).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM chat_messages WHERE id = :id")
                        .param("id", answer)
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    void conversationsPastTheRetentionPeriodAreHiddenAndPurged() {
        UUID expired = conversation(sato, "古い質問", "91 days");
        UUID kept = conversation(sato, "新しい質問", "89 days");

        assertThat(service.list(sato)).extracting(ConversationViews.ConversationSummary::id).containsExactly(kept);
        assertThat(service.isOwnedBy(expired, sato.id())).isFalse();
        assertThatThrownBy(() -> service.get(expired, sato)).isInstanceOf(ErrorResponseException.class);

        assertThat(service.purge()).isGreaterThanOrEqualTo(1);

        assertThat(exists(expired)).isFalse();
        assertThat(exists(kept)).isTrue();
    }

    @Test
    void evidenceIsReReadWithTheViewersCurrentPermissions() {
        UUID internal = publishedDocument("INTERNAL");
        UUID visible = section(internal);
        UUID managersOnly = section(publishedDocument("MANAGERS"));
        UUID removed = section(internal);
        UUID mine = conversation(sato, "結婚祝金はいくら？", "1 hour");
        message(mine, 1, "USER", "結婚祝金はいくら？", null);
        UUID answer = message(mine, 2, "ASSISTANT", "30,000円です。[1][2][3]", true);
        cite(answer, 1, visible);
        cite(answer, 2, managersOnly);
        cite(answer, 3, removed);
        jdbc.sql("DELETE FROM sections WHERE id = :id").param("id", removed).update();

        ConversationDetail detail = service.get(mine, sato);

        assertThat(detail.messages()).extracting(ConversationMessage::role).containsExactly("USER", "ASSISTANT");
        ConversationMessage reply = detail.messages().get(1);
        assertThat(reply.evidence()).extracting(ConversationEvidence::sectionId).containsExactly(visible);
        assertThat(reply.evidence().getFirst().ref()).isEqualTo(1);
        assertThat(reply.evidence().getFirst().current()).isTrue();
        assertThat(reply.hiddenEvidenceCount()).isEqualTo(2);
        assertThat(detail.expiresAt()).isEqualTo(detail.lastMessageAt().plus(Duration.ofDays(90)));
    }

    @Test
    void evidenceFromAnOlderVersionIsShownAsNotCurrent() {
        UUID document = publishedDocument("INTERNAL");
        UUID old = section(document);
        UUID next = jdbc.sql("""
                        INSERT INTO document_versions (document_id, version_label, effective_from, body_markdown)
                        VALUES (:doc, 'v2.0', DATE '2026-10-01', '本文') RETURNING id
                        """).param("doc", document).query(UUID.class).single();
        jdbc.sql("UPDATE documents SET current_version_id = :v WHERE id = :doc")
                .param("v", next)
                .param("doc", document)
                .update();
        UUID mine = conversation(sato, "結婚祝金はいくら？", "1 hour");
        UUID answer = message(mine, 2, "ASSISTANT", "30,000円です。[1]", true);
        cite(answer, 1, old);

        ConversationEvidence evidence =
                service.get(mine, sato).messages().getFirst().evidence().getFirst();

        assertThat(evidence.versionLabel()).isEqualTo("v1.0");
        assertThat(evidence.current()).isFalse();
    }

    private KnaviUserDetails user(String name) {
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        UUID id = jdbc.sql("INSERT INTO users (email, display_name, department_id, role)"
                        + " VALUES (:e, :n, :d, 'EMPLOYEE') RETURNING id")
                .param("e", email)
                .param("n", name)
                .param("d", dept)
                .query(UUID.class)
                .single();
        return new KnaviUserDetails(id, dept, email, null, "EMPLOYEE", true);
    }

    private UUID conversation(KnaviUserDetails owner, String title, String age) {
        return jdbc.sql("INSERT INTO chat_sessions (user_id, title, last_message_at)"
                        + " VALUES (:u, :t, now() - CAST(:age AS interval)) RETURNING id")
                .param("u", owner.id())
                .param("t", title)
                .param("age", age)
                .query(UUID.class)
                .single();
    }

    private UUID message(UUID session, int seq, String role, String content, Boolean answerable) {
        return jdbc.sql("INSERT INTO chat_messages (session_id, seq, role, content, answerable)"
                        + " VALUES (:s, :seq, :role, :c, :a) RETURNING id")
                .param("s", session)
                .param("seq", seq)
                .param("role", role)
                .param("c", content)
                .param("a", answerable)
                .query(UUID.class)
                .single();
    }

    private void cite(UUID message, int ref, UUID section) {
        jdbc.sql("INSERT INTO chat_citations (message_id, ref, section_id) VALUES (:m, :r, :s)")
                .param("m", message)
                .param("r", ref)
                .param("s", section)
                .update();
    }

    private boolean exists(UUID session) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM chat_sessions WHERE id = :id)")
                .param("id", session)
                .query(Boolean.class)
                .single();
    }

    private UUID publishedDocument(String visibility) {
        UUID document = jdbc.sql("""
                        INSERT INTO documents (code, title, document_type, category_id, owner_department_id,
                                               visibility, status)
                        VALUES (:code, 'テスト規程', 'REGULATION', :category, :dept, :vis, 'DRAFT')
                        RETURNING id
                        """)
                .param("code", "CHAT-" + UUID.randomUUID().toString().substring(0, 8))
                .param("category", category)
                .param("dept", dept)
                .param("vis", visibility)
                .query(UUID.class)
                .single();
        UUID version = jdbc.sql("""
                        INSERT INTO document_versions (document_id, version_label, effective_from, body_markdown)
                        VALUES (:doc, 'v1.0', DATE '2026-04-01', '本文') RETURNING id
                        """).param("doc", document).query(UUID.class).single();
        // 公開中の文書は版を指す必要がある（ck_documents_published_has_version）ため、版を作ってから公開にする
        jdbc.sql("UPDATE documents SET current_version_id = :version, status = 'PUBLISHED' WHERE id = :doc")
                .param("version", version)
                .param("doc", document)
                .update();
        return document;
    }

    /** 文書の今の版に条を1つ作る */
    private UUID section(UUID document) {
        UUID version = jdbc.sql("SELECT current_version_id FROM documents WHERE id = :doc")
                .param("doc", document)
                .query(UUID.class)
                .single();
        return jdbc.sql("""
                        INSERT INTO sections (document_version_id, ordinal, heading, content, search_text)
                        VALUES (:version,
                                (SELECT coalesce(max(ordinal), 0) + 1 FROM sections WHERE document_version_id = :version),
                                '第1条', '本文', '本文')
                        RETURNING id
                        """).param("version", version).query(UUID.class).single();
    }
}
