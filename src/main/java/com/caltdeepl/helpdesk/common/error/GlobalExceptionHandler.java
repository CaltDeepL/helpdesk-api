package com.caltdeepl.helpdesk.common.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 例外を RFC 9457 (application/problem+json) に一元変換する。
 *
 * <p>方針:
 *
 * <ul>
 *   <li>4xx はクライアントが原因のため、原因が分かる detail を返す
 *   <li>5xx は内部情報を漏らさず、追跡用の traceId のみ返す
 *   <li>スタックトレースは必ずログ側に出す（レスポンスには絶対に含めない）
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE_URI = "https://helpdesk.example.com/problems/";

    /** 業務例外。 */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(AppException ex) {
        ErrorCode code = ex.code();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URI + code.slug()));
        problem.setTitle(code.title());

        if (code.status().is5xxServerError()) {
            LOG.error("業務例外(5xx): code={}", code, ex);
        } else {
            LOG.warn("業務例外: code={} detail={}", code, ex.getMessage());
        }
        return ResponseEntity.status(code.status()).body(problem);
    }

    /** Bean Validation のエラー。フィールド単位の内訳を errors に載せる。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), "入力値の検証に失敗しました");
        problem.setType(URI.create(PROBLEM_BASE_URI + code.slug()));
        problem.setTitle(code.title());

        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        org.springframework.validation.FieldError::getField,
                        fieldError ->
                                fieldError.getDefaultMessage() == null ? "不正な値です" : fieldError.getDefaultMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        problem.setProperty("errors", errors);

        return ResponseEntity.status(code.status()).body(problem);
    }

    /** 想定外の例外。内部情報は返さない。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, code.title());
        problem.setType(URI.create(PROBLEM_BASE_URI + code.slug()));
        problem.setTitle(code.title());

        LOG.error("想定外の例外が発生しました", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
