package com.caltdeepl.helpdesk.common.error;

import org.springframework.http.HttpStatus;

/**
 * 業務エラーの分類。HTTP ステータスと RFC 9457 の type / title を一元管理する。
 *
 * <p>slug は problem type の URI 末尾に使う（例: {@code .../problems/invalid-status-transition}）。
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "validation-failed", "入力値が不正です"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "unauthorized", "認証が必要です"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden", "権限がありません"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "not-found", "リソースが見つかりません"),
    CONFLICT(HttpStatus.CONFLICT, "conflict", "リソースが競合しています"),
    INVALID_STATUS_TRANSITION(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-status-transition", "許可されていないステータス遷移です"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "サーバー内部でエラーが発生しました");

    private final HttpStatus status;
    private final String slug;
    private final String title;

    ErrorCode(HttpStatus status, String slug, String title) {
        this.status = status;
        this.slug = slug;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }
}
