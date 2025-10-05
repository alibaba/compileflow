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
package com.alibaba.compileflow.engine.core.observability.tracing.impl;

import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import com.alibaba.compileflow.engine.core.observability.tracing.TracingProvider;

/**
 * Micrometer Tracing adapter.
 *
 * @author yusu
 */
@ExtensionRealization(priority = 20)
public class MicrometerTracingProvider implements TracingProvider {

    @Override
    public String getTraceId() {
        try {
            Class<?> traceContextClass = Class.forName("io.micrometer.tracing.TraceContext");
            Object currentContext = traceContextClass.getMethod("current").invoke(null);
            if (currentContext != null) {
                return (String) currentContext.getClass().getMethod("traceId").invoke(currentContext);
            }
        } catch (Exception e) {
            // Silent handling
        }
        return null;
    }

    @Override
    public boolean support(TracingExtensionContext context) {
        try {
            Class.forName("io.micrometer.tracing.TraceContext");
            String traceId = getTraceId();
            return traceId != null && !"micrometer-no-trace".equals(traceId);
        } catch (Exception e) {
            return false;
        }
    }
}
