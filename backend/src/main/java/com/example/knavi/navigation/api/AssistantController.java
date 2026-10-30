package com.example.knavi.navigation.api;

import com.example.knavi.assistant.ConversationRecorder;
import com.example.knavi.assistant.ConversationRecorder.Citation;
import com.example.knavi.assistant.ConversationRecorder.Reply;
import com.example.knavi.navigation.application.AnswerService;
import com.example.knavi.navigation.application.AnswerService.AnswerSink;
import com.example.knavi.navigation.application.AssistantAnswer;
import com.example.knavi.navigation.application.AssistantUnanswerable;
import com.example.knavi.navigation.application.NavigationResult;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 根拠付き回答（E14-T4・ADR-0013）。Server-Sent Events で、段階ごとに次のイベントを送る。
 *
 * <ol>
 *   <li>{@code results}: ナビゲーション検索の4ブロックの結果（{@link NavigationResult}）。検索が終わればすぐ送る
 *   <li>{@code answer}（{@link AssistantAnswer}）または {@code unanswerable}（{@link AssistantUnanswerable}）
 *   <li>{@code conversation}: 会話の履歴に記録した会話の ID（{@link AssistantConversation}。ADR-0014）。記録できなかったときは送らない
 *   <li>{@code done}: 終わり（データは空のオブジェクト）
 * </ol>
 *
 * <p>回答文は1文字ずつではなく、引用を確かめてから1回で送る（確かめる前の文章を画面に出さないため）。
 */
@Tag(name = "assistant", description = "根拠付きの回答（RAG）")
@RestController
@RequestMapping("/api/v1/assistant")
class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    /** 生成の待ち時間（120 秒）に、検索と送信の余裕を足す */
    static final Duration STREAM_TIMEOUT = Duration.ofSeconds(150);

    private final AnswerService service;
    private final ConversationRecorder conversations;
    /** 生成を待つ間、リクエストのスレッドをふさがない。待つだけなので仮想スレッドで足りる */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    AssistantController(AnswerService service, ConversationRecorder conversations) {
        this.service = service;
        this.conversations = conversations;
    }

    /**
     * @param question 知りたいこと（200文字まで）
     * @param conversationId 続きを話す会話（自分の会話だけ）。省くと新しい会話にする
     */
    record AssistantQuestion(String question, UUID conversationId) {}

    /** @param conversationId 質問と回答を記録した会話 */
    record AssistantConversation(UUID conversationId) {}

    @Operation(summary = "根拠付きの回答（SSE。results → answer か unanswerable → conversation → done の順に送る）")
    @ApiResponse(
            responseCode = "200",
            description = "イベントのデータは、results が NavigationResult、answer が AssistantAnswer、"
                    + "unanswerable が AssistantUnanswerable、conversation が AssistantConversation",
            content =
                    @Content(
                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema =
                                    @Schema(
                                            oneOf = {
                                                NavigationResult.class,
                                                AssistantAnswer.class,
                                                AssistantUnanswerable.class,
                                                AssistantConversation.class
                                            })))
    @PostMapping(value = "/answers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter answer(@RequestBody AssistantQuestion body, @AuthenticationPrincipal AuthenticatedUser user) {
        String question = body.question() == null ? null : body.question().strip();
        service.validate(question);
        UUID conversationId = body.conversationId();
        // SSE を始めてからでは 404 を返せないため、先に確かめる
        if (conversationId != null && !conversations.isOwnedBy(conversationId, user.id())) {
            throw ApiErrors.notFound("会話が見つかりません。");
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        executor.submit(() -> {
            try {
                EmitterSink sink = new EmitterSink(emitter);
                service.answer(question, user, sink);
                if (sink.reply != null) {
                    var recorded = conversations.record(user.id(), conversationId, question, sink.reply);
                    if (recorded.isPresent()) {
                        send(emitter, "conversation", new AssistantConversation(recorded.get()));
                    }
                }
                send(emitter, "done", Map.of());
                emitter.complete();
            } catch (RuntimeException | IOException e) {
                // 質問の中身はログに出さない
                log.warn("回答の送信に失敗しました: {}", e.toString());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static void send(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
    }

    /** 回答を送り、会話の履歴に残す内容を覚えておく */
    private static final class EmitterSink implements AnswerSink {

        private final SseEmitter emitter;
        private Reply reply;

        EmitterSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void results(NavigationResult result) {
            sendUnchecked("results", result);
        }

        @Override
        public void answer(AssistantAnswer answer) {
            sendUnchecked("answer", answer);
            reply = Reply.answered(
                    answer.answer(),
                    answer.evidence().stream()
                            .map(e -> new Citation(e.ref(), e.sectionId()))
                            .toList(),
                    answer.model());
        }

        @Override
        public void unanswerable(AssistantUnanswerable unanswerable) {
            sendUnchecked("unanswerable", unanswerable);
            reply = Reply.unanswerable(unanswerable.reason());
        }

        private void sendUnchecked(String name, Object data) {
            try {
                send(emitter, name, data);
            } catch (IOException e) {
                // 利用者が画面を閉じたなど。続けても届かないので止める
                throw new IllegalStateException("SSE の送信に失敗しました", e);
            }
        }
    }
}
