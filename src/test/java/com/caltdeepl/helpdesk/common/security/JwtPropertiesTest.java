package com.caltdeepl.helpdesk.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtPropertiesTest {

    private static final String SECRET_32_BYTES = "0123456789abcdef0123456789abcdef";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("鍵が未設定なら起動を止める")
    void rejectsMissingSecret(String secret) {
        assertThatThrownBy(() -> new JwtProperties(secret, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("HS256 に足りない 256 bit 未満の鍵は拒否し、鍵の値をメッセージに出さない")
    void rejectsShortSecret() {
        String shortSecret = SECRET_32_BYTES.substring(1);

        assertThatThrownBy(() -> new JwtProperties(shortSecret, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bit")
                .hasMessageNotContaining(shortSecret);
    }

    @Test
    @DisplayName("有効期限は 1 分以上でなければならない")
    void rejectsNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtProperties(SECRET_32_BYTES, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("32 バイトの鍵を受け付け、HmacSHA256 の鍵と有効期限を返す")
    void acceptsValidSettings() {
        JwtProperties properties = new JwtProperties(SECRET_32_BYTES, 60);

        assertThat(properties.secretKey().getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(properties.expiration()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("toString に鍵の値を含めない（ログへの漏えい防止）")
    void toStringHidesSecret() {
        assertThat(new JwtProperties(SECRET_32_BYTES, 60).toString()).doesNotContain(SECRET_32_BYTES);
    }
}
