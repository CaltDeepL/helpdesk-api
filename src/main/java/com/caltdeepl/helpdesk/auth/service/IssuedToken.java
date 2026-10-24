package com.caltdeepl.helpdesk.auth.service;

/**
 * 発行したアクセストークン。
 *
 * @param expiresIn 有効期間（秒）。OAuth 2.0 のトークン応答（RFC 6749 5.1）に合わせる
 */
public record IssuedToken(String accessToken, long expiresIn) {

    public String tokenType() {
        return "Bearer";
    }
}
