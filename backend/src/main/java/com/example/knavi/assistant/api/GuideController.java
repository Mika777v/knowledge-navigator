package com.example.knavi.assistant.api;

import com.example.knavi.assistant.application.GuideService;
import com.example.knavi.assistant.application.GuideViews.GuideResult;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 案内（ADR-0014 の決定 1）。きっかけと確認質問の答えから、制度の一覧（対象・対象外・要確認）とチェックリストを返す。
 * LLM は使わず、答えは保存しない。
 */
@Tag(name = "assistant", description = "根拠付きの回答（RAG）")
@RestController
@RequestMapping("/api/v1/assistant/guides")
class GuideController {

    private final GuideService service;

    GuideController(GuideService service) {
        this.service = service;
    }

    /**
     * @param lifeEvent きっかけのコード（MARRIAGE など）
     * @param answers 確認質問のコード → 選んだ value。省いた質問は、利用者の属性から選べるものは選び、それ以外は未回答として扱う
     */
    record GuideRequest(
            @NotBlank @Size(max = 32) String lifeEvent,
            @Size(max = 20) Map<String, String> answers) {}

    @Operation(summary = "きっかけの案内（確認質問・制度の一覧・チェックリスト）")
    @PostMapping
    GuideResult guide(@Valid @RequestBody GuideRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.guide(body.lifeEvent(), body.answers(), user);
    }
}
