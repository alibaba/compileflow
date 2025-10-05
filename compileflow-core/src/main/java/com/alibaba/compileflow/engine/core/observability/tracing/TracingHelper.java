/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.observability.tracing;

import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;

/**
 * Trace ID retrieval with ThreadLocal caching and safe fallback to extensions.
 *
 * @author yusu
 */
public final class TracingHelper {

    private static final String NO_TRACE = "no-trace";

    private TracingHelper() {
        // Utility class
    }

    /**
     * Returns current trace ID (cached if available), never null.
     */
    public static String getCurrentTraceId() {
        // 1. Fast path: Check ThreadLocal cache first
        EngineExecutionContext context = EngineExecutionContextHolder.current();
        if (context != null) {
            String cachedTraceId = context.traceId();
            if (cachedTraceId != null) {
                return cachedTraceId;
            }
        }

        // 2. Slow path: Get from extension system
        return getTraceIdFromExtension();
    }

    /**
     * Gets a fresh trace ID from extensions (bypasses cache).
     */
    public static String getFreshTraceId() {
        return getTraceIdFromExtension();
    }

    /**
     * Internal: fetch from TracingProvider extensions.
     */
    private static String getTraceIdFromExtension() {
        try {
            TracingProvider.TracingExtensionContext tracingContext = TracingProvider.TracingExtensionContext.getInstance();
            String traceId = ExtensionInvoker.getInstance().invokeFirst(
                    tracingContext,
                    TracingProvider.EXT_GET_TRACE_ID,
                    TracingProvider::getTraceId
            );
            return traceId != null ? traceId : NO_TRACE;
        } catch (Exception e) {
            // Silent handling - tracing failure should never break main business flow
            return NO_TRACE;
        }
    }
}
