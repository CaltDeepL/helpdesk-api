package com.caltdeepl.helpdesk.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.caltdeepl.helpdesk.common.observability.TraceId;
import com.caltdeepl.helpdesk.common.observability.TraceIdFilter;

class GlobalExceptionHandlerTest {

    private static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";
    private static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String VALID_TRACEPARENT = "00-" + VALID_TRACE_ID + "-00f067aa0ba902b7-01";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorHandlingTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new TraceIdFilter())
                .build();
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("AppException は ErrorCode の status / type / title を problem+json で返す")
    void appExceptionMapsToProblemJson(ErrorCode code) {
        perform(
                get("/__test/errors/app/{code}", code),
                status().is(code.status().value()),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.type").value("https://helpdesk.example.com/problems/" + code.slug()),
                jsonPath("$.title").value(code.title()),
                jsonPath("$.status").value(code.status().value()),
                jsonPath("$.instance").value("/__test/errors/app/" + code.name()),
                jsonPath("$.traceId", matchesPattern(TRACE_ID_PATTERN)));
    }

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"INTERNAL_ERROR"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("4xx の AppException は detail に業務メッセージを載せる")
    void clientAppExceptionIncludesDetail(ErrorCode code) {
        perform(
                get("/__test/errors/app/{code}", code),
                jsonPath("$.detail").value(ErrorHandlingTestController.APP_DETAIL));
    }

    @Test
    @DisplayName("5xx の AppException は内部メッセージを detail に載せない")
    void serverAppExceptionHidesInternalDetail() {
        perform(
                get("/__test/errors/app/{code}", ErrorCode.INTERNAL_ERROR),
                status().isInternalServerError(),
                jsonPath("$.detail").value(ErrorCode.INTERNAL_ERROR.title()),
                jsonPath("$.detail", not(ErrorHandlingTestController.APP_DETAIL)),
                jsonPath("$.traceId", matchesPattern(TRACE_ID_PATTERN)));
    }

    @Test
    @DisplayName("Bean Validation 失敗は 400 とフィールド単位の errors を返す")
    void validationFailedReturnsFieldErrors() {
        perform(
                post("/__test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                status().isBadRequest(),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.type").value("https://helpdesk.example.com/problems/validation-failed"),
                jsonPath("$.title").value(ErrorCode.VALIDATION_FAILED.title()),
                jsonPath("$.errors.name").value("名前は必須です"),
                jsonPath("$.instance").value("/__test/errors/validation"),
                jsonPath("$.traceId", matchesPattern(TRACE_ID_PATTERN)));
    }

    @Test
    @DisplayName("想定外の例外は 500 とし、メッセージもスタックトレースも返さない")
    void unexpectedExceptionHidesInternals() {
        perform(
                get("/__test/errors/unexpected"),
                status().isInternalServerError(),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.type").value("https://helpdesk.example.com/problems/internal-error"),
                jsonPath("$.title").value(ErrorCode.INTERNAL_ERROR.title()),
                jsonPath("$.detail").value(ErrorCode.INTERNAL_ERROR.title()),
                jsonPath("$.trace").doesNotExist(),
                jsonPath("$.instance").value("/__test/errors/unexpected"),
                jsonPath("$.traceId", matchesPattern(TRACE_ID_PATTERN)),
                content().string(not(containsString(IllegalStateException.class.getName()))),
                content().string(not(containsString(ErrorHandlingTestController.SECRET))));
    }

    @Test
    @DisplayName("有効な traceparent があれば 4xx / 5xx ともその trace-id を返す")
    void traceparentIsReusedOnClientAndServerErrors() {
        perform(
                get("/__test/errors/app/{code}", ErrorCode.NOT_FOUND)
                        .header(TraceId.TRACEPARENT_HEADER, VALID_TRACEPARENT),
                status().isNotFound(),
                jsonPath("$.traceId").value(VALID_TRACE_ID));
        perform(
                get("/__test/errors/unexpected").header(TraceId.TRACEPARENT_HEADER, VALID_TRACEPARENT),
                status().isInternalServerError(),
                jsonPath("$.traceId").value(VALID_TRACE_ID));
    }

    @Test
    @DisplayName("不正な JSON など Spring が扱うエラーにも traceId が付く")
    void springHandledErrorIncludesTraceId() {
        perform(
                post("/__test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"),
                status().isBadRequest(),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.traceId", matchesPattern(TRACE_ID_PATTERN)));
    }

    @Test
    @DisplayName("許可されていないメソッドなど、Spring が扱う別のエラーにも traceId が付く")
    void methodNotAllowedIncludesTraceId() {
        perform(
                post("/__test/errors/unexpected"),
                status().isMethodNotAllowed(),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.instance").value("/__test/errors/unexpected"),
                jsonPath("$.traceId", matchesPattern(TRACE_ID_PATTERN)));
    }

    private void perform(MockHttpServletRequestBuilder request, ResultMatcher... matchers) {
        try {
            var actions = mockMvc.perform(request);
            for (ResultMatcher matcher : matchers) {
                actions.andExpect(matcher);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("MockMvc の実行に失敗しました", ex);
        }
    }
}
