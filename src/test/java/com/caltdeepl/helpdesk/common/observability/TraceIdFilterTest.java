package com.caltdeepl.helpdesk.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String VALID_TRACEPARENT = "00-" + VALID_TRACE_ID + "-00f067aa0ba902b7-01";

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("処理中は MDC に trace-id があり、終了後は取り除かれる")
    void putsTraceIdDuringRequestAndRemovesAfter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceId.TRACEPARENT_HEADER, VALID_TRACEPARENT);
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(TraceId.MDC_KEY));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen.get()).isEqualTo(VALID_TRACE_ID);
        assertThat(MDC.get(TraceId.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("後続で例外が起きても MDC の trace-id は取り除かれる")
    void removesTraceIdEvenWhenChainThrows() {
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(TraceId.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("認証などより先に trace-id を載せるため、最優先の順序で動く")
    void runsWithHighestPrecedence() {
        assertThat(OrderUtils.getOrder(TraceIdFilter.class)).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
