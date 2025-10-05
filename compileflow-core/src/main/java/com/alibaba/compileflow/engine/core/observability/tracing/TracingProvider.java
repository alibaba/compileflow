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

import com.alibaba.compileflow.engine.core.extension.Extension;
import com.alibaba.compileflow.engine.core.extension.ExtensionContext;
import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

/**
 * Distributed tracing provider extension point.
 * <p>
 * Supports any company's tracing middleware: Sleuth, Micrometer,
 * Eagle Eye, CAT, SkyWalking, etc.
 *
 * @author yusu
 */
public interface TracingProvider extends Extension<TracingProvider.TracingExtensionContext> {

    String EXT_GET_TRACE_ID = "compileflow.tracing.getTraceId";

    /**
     * Gets the current distributed trace ID.
     * <p>
     * Retrieves from thread context: MDC, ThreadLocal, TraceContext, etc.
     * No parameters needed as tracing systems get from current thread.
     */
    @ExtensionPoint(code = EXT_GET_TRACE_ID, desc = "Get distributed trace ID")
    String getTraceId();

    /**
     * Checks if current tracing environment is supported.
     * Complies with CompileFlow Extension mechanism.
     */
    @Override
    default boolean support(TracingExtensionContext context) {
        try {
            String traceId = getTraceId();
            return traceId != null && !"no-trace".equals(traceId);
        } catch (Exception e) {
            return false;
        }
    }

    class TracingExtensionContext implements ExtensionContext {

        public static final TracingExtensionContext INSTANCE = new TracingExtensionContext();

        private TracingExtensionContext() {
        }

        public static TracingExtensionContext getInstance() {
            return INSTANCE;
        }
    }

}
