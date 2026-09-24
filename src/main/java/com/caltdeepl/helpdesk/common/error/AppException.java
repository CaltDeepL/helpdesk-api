package com.caltdeepl.helpdesk.common.error;

/** 業務例外。{@link ErrorCode} で分類し、GlobalExceptionHandler が problem+json に変換する。 */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public AppException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AppException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** リソースが存在しない（他人のリソースも含む）ことを表す 404。 */
    public static AppException notFound(String resourceName) {
        return new AppException(ErrorCode.NOT_FOUND, resourceName + "が見つかりません");
    }

    public ErrorCode code() {
        return code;
    }
}
