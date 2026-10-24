package com.caltdeepl.helpdesk.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT の設定（{@code app.jwt.*}）。値が不正なら起動時に失敗させる（ADR-0003）。
 *
 * @param secret HS256 の共有鍵。環境変数 {@code JWT_SECRET} から読む。256 bit（32 バイト）以上
 * @param expirationMinutes アクセストークンの有効期間（分）
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, long expirationMinutes) {

    /** HS256 は鍵長 256 bit 以上を要求する（RFC 7518 3.2）。 */
    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.jwt.secret（環境変数 JWT_SECRET）が未設定です");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret（環境変数 JWT_SECRET）は 256 bit（" + MIN_SECRET_BYTES + " バイト）以上にしてください");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalArgumentException("app.jwt.expiration-minutes は 1 以上にしてください");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public Duration expiration() {
        return Duration.ofMinutes(expirationMinutes);
    }

    /** 鍵の値をログや例外メッセージに出さない。 */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, expirationMinutes=" + expirationMinutes + "]";
    }
}
