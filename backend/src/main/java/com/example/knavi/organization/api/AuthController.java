package com.example.knavi.organization.api;

import com.example.knavi.organization.application.LoginThrottle;
import com.example.knavi.organization.infrastructure.KnaviUserDetails;
import com.example.knavi.organization.infrastructure.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    private final LoginThrottle loginThrottle;

    AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            UserRepository users,
            LoginThrottle loginThrottle) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.users = users;
        this.loginThrottle = loginThrottle;
    }

    @Operation(summary = "CSRF トークンを発行する（XSRF-TOKEN Cookie を設定する）。ログイン前に1回呼ぶ")
    @GetMapping("/auth/csrf")
    Map<String, String> csrf(CsrfToken token) {
        // トークンは遅延生成されるため、値を読んで生成させる（このとき XSRF-TOKEN Cookie が設定される）
        token.getToken();
        return Map.of("headerName", token.getHeaderName());
    }

    @Operation(summary = "メールアドレスとパスワードでログインする")
    @PostMapping("/auth/login")
    @Transactional(readOnly = true)
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        // 続けて失敗しているアカウントは、パスワードが正しくてもしばらく受け付けない
        loginThrottle.lockedFor(body.email()).ifPresent(remaining -> {
            throw new LoginLockedException(remaining);
        });
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
        } catch (AuthenticationException e) {
            loginThrottle.recordFailure(body.email());
            throw e;
        }
        loginThrottle.recordSuccess(body.email());

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

    static final class LoginLockedException extends RuntimeException {
        final Duration remaining;

        LoginLockedException(Duration remaining) {
            super("login locked");
            this.remaining = remaining;
        }
    }

    /** ロック中は 429 と、再度試せるまでの秒数（Retry-After）を返す */
    @ExceptionHandler(LoginLockedException.class)
    ResponseEntity<ProblemDetail> onLocked(LoginLockedException e) {
        long seconds = Math.max(1, e.remaining.toSeconds());
        long minutes = (seconds + 59) / 60;
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "ログインに続けて失敗したため、一時的にログインできません。約" + minutes + "分後に再度お試しください。");
        problem.setTitle("Too Many Requests");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(problem);
    }

    /** 失敗理由（ユーザーが存在しない・パスワード違い・無効）は区別せずに返す */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail onAuthenticationFailure() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません。");
        problem.setTitle("Unauthorized");
        return problem;
    }
}
