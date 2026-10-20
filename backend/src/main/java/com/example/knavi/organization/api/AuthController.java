package com.example.knavi.organization.api;

import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.example.knavi.organization.infrastructure.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "auth", description = "ログイン・ログアウト・ログイン中のユーザー")
@RestController
@RequestMapping("/api/v1")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository users;

    AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            UserRepository users) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.users = users;
    }

    @Operation(summary = "CSRF トークンを発行する（XSRF-TOKEN Cookie を設定する）。ログイン前に1回呼ぶ")
    @GetMapping("/auth/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }

    @Operation(summary = "メールアドレスとパスワードでログインする")
    @PostMapping("/auth/login")
    @Transactional(readOnly = true)
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));

        // セッション固定攻撃を防ぐため、ログイン時にセッション ID を変える
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        var principal = (KnaviUserDetails) authentication.getPrincipal();
        return users.findWithDepartmentById(principal.id())
                .map(MeResponse::from)
                .orElseThrow();
    }

    @Operation(summary = "ログイン中のユーザーを返す")
    @GetMapping("/me")
    @Transactional(readOnly = true)
    MeResponse me(@AuthenticationPrincipal KnaviUserDetails principal) {
        return users.findWithDepartmentById(principal.id())
                .map(MeResponse::from)
                .orElseThrow();
    }

    /** 失敗理由（ユーザーが存在しない・パスワード違い・無効）は区別せずに返す */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail onAuthenticationFailure() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません。");
        problem.setTitle("Unauthorized");
        return problem;
    }
}
