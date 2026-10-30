package com.example.knavi.navigation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knavi.TestcontainersConfiguration;
import com.example.knavi.assistant.ConversationRecorder.Citation;
import com.example.knavi.assistant.ConversationRecorder.Reply;
import com.example.knavi.assistant.application.ConversationService;
import com.example.knavi.navigation.application.AnswerService;
import com.example.knavi.navigation.application.AnswerService.AnswerSink;
import com.example.knavi.navigation.application.AssistantAnswer;
import com.example.knavi.navigation.application.AssistantAnswer.AssistantEvidence;
import com.example.knavi.navigation.application.AssistantUnanswerable;
import com.example.knavi.navigation.application.NavigationResult;
import com.example.knavi.navigation.application.NavigationResult.QueryInfo;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** 根拠付き回答の SSE（E14-T4）。回答の中身は AnswerService を差し替え、イベントの順と形だけを確かめる */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AssistantApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    AnswerService service;

    /** 会話の記録（ConversationRecorder の実装）。一覧などの API も同じ Bean を使うため、実装の型で差し替える */
    @MockitoBean
    ConversationService conversations;

    KnaviUserDetails employee;

    @BeforeEach
    void setUp() {
        UUID dept = jdbc.sql("INSERT INTO departments (code, name) VALUES ('GA', '総務部') RETURNING id")
                .query(UUID.class)
                .single();
        UUID id = jdbc.sql("INSERT INTO users (email, display_name, department_id, role)"
                        + " VALUES ('sato@example.com', '佐藤', :d, 'EMPLOYEE') RETURNING id")
                .param("d", dept)
                .query(UUID.class)
                .single();
        employee = new KnaviUserDetails(id, dept, "sato@example.com", null, "EMPLOYEE", true);
        // 質問の形の確認は本物を使う
        doCallRealMethod().when(service).validate(any());
    }

    @Test
    void streamsResultsThenTheAnswerThenDone() throws Exception {
        UUID section = UUID.randomUUID();
        doAnswer(invocation -> {
                    AnswerSink sink = invocation.getArgument(2);
                    sink.results(new NavigationResult(
                            null,
                            new QueryInfo("結婚祝金", "結婚祝金", List.of("結婚祝金"), List.of()),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            false));
                    sink.answer(new AssistantAnswer(
                            "30,000円です。[1]",
                            List.of(new AssistantEvidence(
                                    1, UUID.randomUUID(), "慶弔見舞金規程", "v2.1", section, "第5条", "結婚祝金", null, null)),
                            "qwen3.5:4b"));
                    return null;
                })
                .when(service)
                .answer(eq("結婚祝金はいくら？"), any(), any());

        String body = stream("{\"question\":\" 結婚祝金はいくら？ \"}");

        assertThat(body.indexOf("event:results")).isGreaterThanOrEqualTo(0);
        assertThat(body.indexOf("event:answer")).isGreaterThan(body.indexOf("event:results"));
        assertThat(body.indexOf("event:done")).isGreaterThan(body.indexOf("event:answer"));
        assertThat(body).contains("\"answer\":\"30,000円です。[1]\"").contains(section.toString());
    }

    @Test
    void recordsTheAnswerInANewConversationAndSendsItsIdBeforeDone() throws Exception {
        UUID section = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        doAnswer(invocation -> {
                    AnswerSink sink = invocation.getArgument(2);
                    sink.answer(new AssistantAnswer(
                            "30,000円です。[1]",
                            List.of(new AssistantEvidence(
                                    1, UUID.randomUUID(), "慶弔見舞金規程", "v2.1", section, "第5条", "結婚祝金", null, null)),
                            "qwen3.5:4b"));
                    return null;
                })
                .when(service)
                .answer(eq("結婚祝金はいくら？"), any(), any());
        Reply reply = Reply.answered("30,000円です。[1]", List.of(new Citation(1, section)), "qwen3.5:4b");
        when(conversations.record(employee.id(), null, "結婚祝金はいくら？", reply)).thenReturn(Optional.of(conversation));

        String body = stream("{\"question\":\"結婚祝金はいくら？\"}");

        assertThat(body.indexOf("event:conversation")).isGreaterThan(body.indexOf("event:answer"));
        assertThat(body.indexOf("event:done")).isGreaterThan(body.indexOf("event:conversation"));
        assertThat(body).contains("\"conversationId\":\"" + conversation + "\"");
    }

    @Test
    void continuesTheUsersOwnConversationAndRecordsUnanswerableReplies() throws Exception {
        UUID conversation = UUID.randomUUID();
        when(conversations.isOwnedBy(conversation, employee.id())).thenReturn(true);
        doAnswer(invocation -> {
                    AnswerSink sink = invocation.getArgument(2);
                    sink.unanswerable(new AssistantUnanswerable("NO_EVIDENCE"));
                    return null;
                })
                .when(service)
                .answer(eq("申請先は？"), any(), any());
        when(conversations.record(employee.id(), conversation, "申請先は？", Reply.unanswerable("NO_EVIDENCE")))
                .thenReturn(Optional.of(conversation));

        String body = stream("{\"question\":\"申請先は？\",\"conversationId\":\"" + conversation + "\"}");

        assertThat(body).contains("event:conversation").contains(conversation.toString());
    }

    @Test
    void someoneElsesConversationIsNotFoundBeforeStreaming() throws Exception {
        UUID theirs = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/assistant/answers")
                        .with(user(employee))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"申請先は？\",\"conversationId\":\"" + theirs + "\"}"))
                .andExpect(status().isNotFound());
        verify(service, never()).answer(any(), any(), any());
    }

    @Test
    void blankAndTooLongQuestionsAreBadRequestsBeforeStreaming() throws Exception {
        for (String question : List.of(" ", "あ".repeat(201))) {
            mockMvc.perform(post("/api/v1/assistant/answers")
                            .with(user(employee))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"" + question + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void needsLoginAndCsrf() throws Exception {
        String json = "{\"question\":\"結婚祝金\"}";
        mockMvc.perform(post("/api/v1/assistant/answers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/assistant/answers")
                        .with(user(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    private String stream(String json) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/assistant/answers")
                        .with(user(employee))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(json))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
