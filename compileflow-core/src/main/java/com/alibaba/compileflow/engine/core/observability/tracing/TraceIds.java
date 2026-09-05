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
package com.alibaba.compileflow.engine.core.observability.tracing;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.spi.observability.TraceIdProvider;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Helpers for resolving and generating process trace identifiers.
 *
 * @author yusu
 */
public final class TraceIds {
    private TraceIds() {
    }

    public static String current() {
        return current(null);
    }

    /**
     * Resolves the current trace identifier.
     * <p>
     * Resolution order: cached execution-context value, the configured provider,
     * the {@code traceId} MDC entry, and finally a random identifier. Provider
     * failures are swallowed so tracing never breaks process execution.
     *
     * @param provider configured trace-id provider, or {@code null} when unset
     * @return non-blank trace identifier
     */
    public static String current(TraceIdProvider provider) {
        EngineExecutionContext context = EngineExecutionContextHolder.current();
        if (context != null) {
            String cachedTraceId = validate(context.traceId());
            if (cachedTraceId != null) {
                return cachedTraceId;
            }
        }
        if (provider != null) {
            try {
                String provided = validate(provider.nextTraceId());
                if (provided != null) {
                    return provided;
                }
            } catch (RuntimeException ignored) {
                // Fall through to MDC / random generation.
            }
        }
        String traceId = validate(MDC.get("traceId"));
        if (traceId != null) {
            return traceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String validate(String value) {
        try {
            return ProcessIdentifiers.optionalTraceId(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
