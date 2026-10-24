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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.caltdeepl.helpdesk.common.observability.TraceId;

/**
 * 例外を RFC 9457 (application/problem+json) に一元変換する。
 *
 * <p>方針:
 *
 * <ul>
 *   <li>4xx はクライアントが原因のため、原因が分かる detail を返す
 *   <li>5xx は内部情報を漏らさず、固定の title / detail のみ返す
 *   <li>traceId はすべてのエラー応答に付け、ログの MDC と突き合わせる
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
        String detail = code.status().is5xxServerError() ? code.title() : ex.getMessage();
        ProblemDetail problem = problem(code, detail);

        if (code.status().is5xxServerError()) {
            LOG.error("業務例外(5xx): code={}", code, ex);
        } else {
            LOG.warn("業務例外: code={} detail={}", code, ex.getMessage());
        }
        return ResponseEntity.status(code.status()).body(problem);
    }

    /**
     * Spring Security の認証失敗（トークンの欠如・不正・期限切れ）。ProblemDetailsSecurityHandler から委ねられる。
     *
     * <p>失敗の内訳は攻撃の手がかりになるため返さない。RFC 6750 3 に従い {@code WWW-Authenticate: Bearer} を付ける。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        ErrorCode code = ErrorCode.UNAUTHORIZED;
        String detail = ex instanceof OAuth2AuthenticationException
                ? "アクセストークンが無効か、有効期限が切れています"
                : "認証が必要です。Authorization ヘッダに Bearer トークンを付けてください";

        LOG.info("認証に失敗しました: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(code.status())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem(code, detail));
    }

    /** 認可の失敗。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ErrorCode code = ErrorCode.FORBIDDEN;

        LOG.info("認可に失敗しました");
        return ResponseEntity.status(code.status()).body(problem(code, "この操作を行う権限がありません"));
    }

    /** Bean Validation のエラー。フィールド単位の内訳を errors に載せる。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        ProblemDetail problem = problem(code, "入力値の検証に失敗しました");

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
        ProblemDetail problem = problem(code, code.title());

        LOG.error("想定外の例外が発生しました", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    /** Spring MVC が扱う例外にも traceId を付ける。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response.getBody() instanceof ProblemDetail problem) {
            applyTraceId(problem);
        }
        return response;
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(PROBLEM_BASE_URI + code.slug()));
        problem.setTitle(code.title());
        applyTraceId(problem);
        return problem;
    }

    private static void applyTraceId(ProblemDetail problem) {
        problem.setProperty("traceId", TraceId.current());
    }
}
