package com.alibaba.compileflow.engine.core.infrastructure;

import org.slf4j.MDC;

import java.util.Map;

/**
 * MDC helpers: only traceId and processCode.
 *
 * @author yusu
 */
public class LogContext {

    private static final String TRACE_ID = "traceId";
    private static final String PROCESS_CODE = "processCode";

    public static String getTraceId() {
        return get(TRACE_ID);
    }

    public static void setTraceId(String value) {
        putSafe(TRACE_ID, value);
    }

    public static String getProcessCode() {
        return get(PROCESS_CODE);
    }

    public static void setProcessCode(String value) {
        putSafe(PROCESS_CODE, value);
    }

    public static Map<String, String> getContext() {
        try {
            return MDC.getCopyOfContextMap();
        } catch (Exception e) {
            return null;
        }
    }

    public static void setContext(Map<String, String> contextMap) {
        try {
            if (contextMap == null || contextMap.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(contextMap);
            }
        } catch (Exception ignored) {
        }
    }

    public static void clear() {
        try {
            MDC.clear();
        } catch (Exception ignored) {
        }
    }

    private static String get(String key) {
        try {
            return MDC.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static void putSafe(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        try {
            MDC.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
