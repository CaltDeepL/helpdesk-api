package com.caltdeepl.helpdesk.common.observability;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/**
 * W3C Trace Context の 32 桁 hex {@code trace-id}。
 *
 * <p>分散トレーシング（Micrometer Tracing）は Task #12 で導入する。それまではリクエスト単位の追跡 ID として自前発行し、MDC と
 * problem+json の {@code traceId} に載せる。
 */
public final class TraceId {

    public static final String MDC_KEY = "traceId";
    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final Pattern TRACEPARENT =
            Pattern.compile("^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final String INVALID_TRACE_ID = "0".repeat(32);
    private static final String INVALID_PARENT_ID = "0".repeat(16);

    private TraceId() {}

    /** 全ゼロではない 32 桁 hex の trace-id を発行する。 */
    public static String create() {
        byte[] bytes = new byte[16];
        do {
            RANDOM.nextBytes(bytes);
        } while (isAllZero(bytes));
        return HEX.formatHex(bytes);
    }

    /**
     * {@code traceparent} が有効ならその trace-id を使い、無効または欠落なら新規発行する。
     *
     * @param traceparent W3C {@code traceparent} ヘッダ。{@code null} 可
     */
    public static String resolve(String traceparent) {
        if (traceparent == null) {
            return create();
        }
        Matcher matcher = TRACEPARENT.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return create();
        }
        String version = matcher.group(1);
        String traceId = matcher.group(2);
        String parentId = matcher.group(3);
        if ("ff".equals(version) || INVALID_TRACE_ID.equals(traceId) || INVALID_PARENT_ID.equals(parentId)) {
            return create();
        }
        return traceId;
    }

    /** MDC の trace-id。未設定なら新規発行する。 */
    public static String current() {
        String id = MDC.get(MDC_KEY);
        if (id == null || id.isBlank()) {
            return create();
        }
        return id;
    }

    private static boolean isAllZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
