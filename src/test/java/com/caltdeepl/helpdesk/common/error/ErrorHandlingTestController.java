package com.caltdeepl.helpdesk.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * エラー変換テスト専用。本番コードには置かない。
 *
 * <p>想定外例外は Checkstyle の IllegalThrows を避けるため {@link IllegalStateException} を投げる。
 */
@RestController
@RequestMapping("/__test/errors")
@Validated
class ErrorHandlingTestController {

    static final String APP_DETAIL = "業務エラーの詳細";
    static final String SECRET = "secret-stack-leaked";

    @GetMapping("/app/{code}")
    void app(@PathVariable ErrorCode code) {
        throw new AppException(code, APP_DETAIL);
    }

    @PostMapping("/validation")
    void validation(@Valid @RequestBody SampleRequest body) {
        // 到達しない。検証エラーを起こすための受け口。
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException(SECRET);
    }

    @GetMapping("/unauthenticated")
    void unauthenticated() {
        throw new InsufficientAuthenticationException(SECRET);
    }

    @GetMapping("/invalid-token")
    void invalidToken() {
        throw new InvalidBearerTokenException(SECRET);
    }

    @GetMapping("/forbidden")
    void forbidden() {
        throw new AccessDeniedException(SECRET);
    }

    record SampleRequest(@NotBlank(message = "名前は必須です") String name) {}
}
