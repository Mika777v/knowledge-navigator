package com.example.knavi.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 認証・認可の設定。
 *
 * <ul>
 *   <li>セッション Cookie による認証（ブラウザは Next.js の BFF 経由で同一オリジンとして呼ぶ。ADR-0005）
 *   <li>CSRF は SPA 向けの方式（XSRF-TOKEN Cookie を読んで X-XSRF-TOKEN ヘッダーで送る）
 *   <li>未認証の API 呼び出しにはリダイレクトではなく 401 を返す
 *   <li>無効化・ロールや所属の変更があったユーザーのセッションは、次のリクエストで破棄する（SessionRevalidationFilter）
 * </ul>
 *
 * <p>データ単位の認可（公開範囲・所管部署）は AccessPolicy（#34）で行う。ADR-0004
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** Web サーバーを起動しないとき（make seed など）は HttpSecurity が無いため作らない */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionPrincipalChecker principalChecker)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.spa())
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                // 認可（ロールの確認）より前に、セッションのロールが今も正しいかを確かめる
                .addFilterBefore(new SessionRevalidationFilter(principalChecker), AuthorizationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // 既定は BCrypt。ハッシュ文字列の接頭辞（{bcrypt} など）で方式を判別する
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
