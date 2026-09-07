/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.observability;

import java.util.Map;
import org.slf4j.MDC;

/**
 * MDC helpers for process execution logging context.
 *
 * @author yusu
 */
public final class LogContext {
    private static final String TRACE_ID = "traceId";
    private static final String PROCESS_CODE = "processCode";

    private LogContext() {
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    public static void setTraceId(String value) {
        putOrRemove(TRACE_ID, value);
    }

    public static String getProcessCode() {
        return MDC.get(PROCESS_CODE);
    }

    public static void setProcessCode(String value) {
        putOrRemove(PROCESS_CODE, value);
    }

    public static Map<String, String> getContext() {
        return MDC.getCopyOfContextMap();
    }

    public static void setContext(Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(contextMap);
        }
    }

    private static void putOrRemove(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
