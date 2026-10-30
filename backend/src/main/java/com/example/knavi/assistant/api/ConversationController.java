package com.example.knavi.assistant.api;

import com.example.knavi.assistant.application.ConversationService;
import com.example.knavi.assistant.application.ConversationViews.ConversationDetail;
import com.example.knavi.assistant.application.ConversationViews.ConversationSummary;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 会話の履歴（ADR-0014 の決定 3）。本人の会話だけ。他の人の会話は 404 にする */
@Tag(name = "assistant", description = "根拠付きの回答（RAG）")
@RestController
@RequestMapping("/api/v1/assistant/conversations")
class ConversationController {

    private final ConversationService service;

    ConversationController(ConversationService service) {
        this.service = service;
    }

    @Operation(summary = "自分の会話の一覧（新しい順・50件まで。保持期間を過ぎたものは出さない）")
    @GetMapping
    List<ConversationSummary> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user);
    }

    @Operation(summary = "自分の会話（根拠は開いた時点の権限で読み直す）")
    @GetMapping("/{id}")
    ConversationDetail get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(id, user);
    }

    @Operation(summary = "自分の会話を削除する")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(id, user);
    }
}
