package com.caltdeepl.helpdesk.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 認証の方針: ステートレスな Bearer トークン（JWT）。登録・ログイン・ヘルスチェック・API ドキュメント以外は認証必須（FR-1.3）。
 *
 * <p>CSRF 保護は無効にする（ADR-0003）。CSRF はブラウザが自動で送る認証情報（Cookie など）を悪用する攻撃で、この API の認証は
 * {@code Authorization: Bearer} ヘッダだけ、セッションも Cookie も発行しない。この前提は AuthIntegrationTest の
 * noCookieIsIssued で検査する。Cookie で認証する機能を入れるときは、CSRF 保護を有効にし直す。
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATHS = {
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemDetailsSecurityHandler problemHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter) {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                                .permitAll()
                                // コントローラーの外で起きたエラーの転送先。ここを塞ぐと本来の 4xx が 401 に化ける
                                .requestMatchers("/error")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                                .authenticationEntryPoint(problemHandler)
                                .accessDeniedHandler(problemHandler))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler));
        return http.build();
    }

    /** 既定は bcrypt。保存値に {@code {bcrypt}} の接頭辞が付き、将来の方式変更に追従できる（FR-1.4）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
