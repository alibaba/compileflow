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
package com.alibaba.compileflow.engine.spi.observability;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.UUID;

/**
 * Supplies lightweight correlation identifiers for process executions.
 * <p>
 * This is a log/event correlation hook, not a tracing, span, baggage, or context-
 * propagation abstraction. Integrations that need distributed tracing should use
 * their tracing system's current context and may adapt its trace identifier here.
 * CompileFlow Core does not propagate provider state into application-created tasks.
 * <p>
 * Implementations must be thread-safe, bounded, and non-blocking. A thrown exception is treated as
 * an unavailable value and falls through to MDC/random correlation; observability input never
 * changes the Process result. The application or dependency-injection container owns the provider
 * lifecycle.
 *
 * @author yusu
 */
@FunctionalInterface
public interface TraceIdProvider {
    /**
     * Returns a provider that generates a random 32-character hex identifier.
     *
     * @return random trace-id provider
     */
    static TraceIdProvider random() {
        return () -> UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Returns the next trace identifier.
     *
     * <p>The engine accepts exact values from 1 through
     * {@value ProcessIdentifiers#MAX_TRACE_ID_LENGTH} characters.
     * Blank, control-containing, whitespace-padded, or oversized values are treated as unavailable
     * and fall through to the next correlation source; values are never normalized or truncated.
     *
     * @return trace identifier, or blank/null when unavailable
     */
    String nextTraceId();
}
