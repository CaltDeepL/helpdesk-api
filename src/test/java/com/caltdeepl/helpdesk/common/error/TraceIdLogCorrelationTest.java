package com.caltdeepl.helpdesk.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.UnsupportedEncodingException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.caltdeepl.helpdesk.common.observability.TraceId;
import com.caltdeepl.helpdesk.common.observability.TraceIdFilter;
import com.jayway.jsonpath.JsonPath;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** エラー応答の traceId で、そのリクエストのログを引けることを確かめる。 */
class TraceIdLogCorrelationTest {

    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        appender.start();
        handlerLogger.addAppender(appender);
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorHandlingTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("想定外の例外で出したエラーログの traceId は、応答の traceId と一致する")
    void errorLogCarriesSameTraceIdAsResponse() {
        String responseTraceId = traceIdOf(perform("/__test/errors/unexpected"));

        assertThat(loggedTraceIds()).containsExactly(responseTraceId);
    }

    @Test
    @DisplayName("5xx の AppException で出したエラーログの traceId は、応答の traceId と一致する")
    void appExceptionLogCarriesSameTraceIdAsResponse() {
        String responseTraceId = traceIdOf(perform("/__test/errors/app/" + ErrorCode.INTERNAL_ERROR.name()));

        assertThat(loggedTraceIds()).containsExactly(responseTraceId);
    }

    private MvcResult perform(String path) {
        try {
            return mockMvc.perform(get(path)).andReturn();
        } catch (Exception ex) {
            throw new IllegalStateException("MockMvc の実行に失敗しました", ex);
        }
    }

    private static String traceIdOf(MvcResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("応答本文を読めませんでした", ex);
        }
    }

    private List<String> loggedTraceIds() {
        return appender.list.stream()
                .map(event -> event.getMDCPropertyMap().get(TraceId.MDC_KEY))
                .toList();
    }
}
