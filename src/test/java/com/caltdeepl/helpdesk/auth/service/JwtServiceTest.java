package com.caltdeepl.helpdesk.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.caltdeepl.helpdesk.common.security.JwtConfig;
import com.caltdeepl.helpdesk.common.security.JwtProperties;
import com.caltdeepl.helpdesk.user.domain.Role;

class JwtServiceTest {

    private static final String SECRET = "unit-test-only-secret-0123456789abcdef";
    private static final String OTHER_SECRET = "another-unit-test-secret-0123456789abcdef";
    private static final UUID USER_ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000001");

    private final JwtProperties properties = new JwtProperties(SECRET, 60);
    private final JwtConfig jwtConfig = new JwtConfig();
    private final JwtDecoder decoder = jwtConfig.jwtDecoder(properties);

    @Test
    @DisplayName("発行したトークンは同じ鍵で検証でき、sub・role・有効期限を持つ")
    void issuedTokenCanBeDecoded() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        IssuedToken token =
                service(properties, Clock.fixed(now, ZoneOffset.UTC)).issue(USER_ID, Role.AGENT);
        Jwt jwt = decoder.decode(token.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString(JwtConfig.ROLE_CLAIM)).isEqualTo("AGENT");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(60)));
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("ペイロードを書き換えたトークン（ロールの昇格）は拒否する")
    void tamperedTokenIsRejected() {
        String[] parts = service(properties, Clock.systemUTC())
                .issue(USER_ID, Role.AGENT)
                .accessToken()
                .split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.replace("\"AGENT\"", "\"MANAGER\"").getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forged + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("別の鍵で署名したトークンは拒否する")
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = service(new JwtProperties(OTHER_SECRET, 60), Clock.systemUTC())
                .issue(USER_ID, Role.AGENT)
                .accessToken();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("有効期限が切れたトークンは拒否する")
    void expiredTokenIsRejected() {
        Clock twoHoursAgo = Clock.offset(Clock.systemUTC(), Duration.ofHours(-2));
        String token =
                service(properties, twoHoursAgo).issue(USER_ID, Role.AGENT).accessToken();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("署名なし（alg=none）のトークンは拒否する")
    void unsignedTokenIsRejected() {
        String[] parts = service(properties, Clock.systemUTC())
                .issue(USER_ID, Role.AGENT)
                .accessToken()
                .split("\\.");
        String noneHeader = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String unsigned = noneHeader + "." + parts[1] + ".";

        assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(JwtException.class);
    }

    private JwtService service(JwtProperties props, Clock clock) {
        return new JwtService(jwtConfig.jwtEncoder(props), props, clock);
    }
}
