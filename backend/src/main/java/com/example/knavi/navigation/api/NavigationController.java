package com.example.knavi.navigation.api;

import com.example.knavi.navigation.application.NavigationResult;
import com.example.knavi.navigation.application.NavigationService;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ナビゲーション検索。ログインユーザーが検索で見てよいもの（CONFIDENTIAL を除く）だけを返す */
@Tag(name = "navigation", description = "質問から制度・規程・FAQ・問い合わせ先を案内する")
@RestController
@RequestMapping("/api/v1")
class NavigationController {

    private final NavigationService service;

    NavigationController(NavigationService service) {
        this.service = service;
    }

    @Operation(summary = "ナビゲーション検索（制度・文書・FAQ・窓口の4ブロック）")
    @GetMapping("/navigate")
    NavigationResult navigate(
            @Parameter(description = "知りたいこと（例: 結婚したら何か制度ある？）。200文字まで") @RequestParam String q,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.navigate(q, user);
    }
}
