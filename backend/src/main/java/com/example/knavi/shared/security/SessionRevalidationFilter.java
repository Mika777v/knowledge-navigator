package com.example.knavi.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * セッションの principal を、リクエストごとに users テーブルと照らし合わせる。
 * 無効化・ロールや所属の変更があったセッションは破棄して 401 を返し、もう一度ログインしてもらう
 * （古いロールのまま管理機能を使い続けたり、異動前の部署限定の文書を読み続けたりできないように）。
 *
 * <p>主キーで1行読むだけなので、リクエストごとに行っても負荷は小さい
 */
final class SessionRevalidationFilter extends OncePerRequestFilter {

    static final String DETAIL = "アカウントの権限や所属が変わったため、ログアウトしました。もう一度ログインしてください。";

    private final SessionPrincipalChecker checker;

    SessionRevalidationFilter(SessionPrincipalChecker checker) {
        this.checker = checker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && !checker.isCurrent(user)) {
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter()
                    .write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"" + DETAIL
                            + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
