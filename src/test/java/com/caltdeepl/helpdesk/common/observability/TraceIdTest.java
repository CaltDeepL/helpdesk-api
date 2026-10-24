package com.caltdeepl.helpdesk.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceIdTest {

    private static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String VALID_TRACEPARENT = "00-" + VALID_TRACE_ID + "-00f067aa0ba902b7-01";

    @Test
    @DisplayName("create は 32 桁の小文字 hex で、全ゼロではない")
    void createReturnsW3cTraceId() {
        String id = TraceId.create();

        assertThat(id).matches("[0-9a-f]{32}");
        assertThat(id).isNotEqualTo("0".repeat(32));
    }

    @Test
    @DisplayName("有効な traceparent から 32 桁の trace-id を取り出す")
    void resolveExtractsTraceIdFromTraceparent() {
        assertThat(TraceId.resolve(VALID_TRACEPARENT)).isEqualTo(VALID_TRACE_ID);
    }

    @Test
    @DisplayName("欠落・不正・全ゼロの traceparent では新規発行する")
    void resolveCreatesWhenTraceparentIsUnusable() {
        assertThat(TraceId.resolve(null)).matches("[0-9a-f]{32}");
        assertThat(TraceId.resolve("not-a-traceparent")).matches("[0-9a-f]{32}");
        assertThat(TraceId.resolve("00-" + "0".repeat(32) + "-00f067aa0ba902b7-01"))
                .isNotEqualTo("0".repeat(32))
                .matches("[0-9a-f]{32}");
        assertThat(TraceId.resolve("ff-" + VALID_TRACE_ID + "-00f067aa0ba902b7-01"))
                .isNotEqualTo(VALID_TRACE_ID);
    }

    @Test
    @DisplayName("current は MDC の値を返し、無ければ新規発行する")
    void currentReadsMdcOrCreates() {
        MDC.clear();
        try {
            assertThat(TraceId.current()).matches("[0-9a-f]{32}");

            MDC.put(TraceId.MDC_KEY, VALID_TRACE_ID);
            assertThat(TraceId.current()).isEqualTo(VALID_TRACE_ID);
        } finally {
            MDC.clear();
        }
    }
}
