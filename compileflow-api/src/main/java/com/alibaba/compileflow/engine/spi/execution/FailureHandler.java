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
package com.alibaba.compileflow.engine.spi.execution;

/**
 * Selects the terminal outcome after an action exhausts its configured retry attempts.
 * <p>
 * Implementations must be thread-safe, deterministic, bounded, and non-blocking. A handler is an
 * execution decision, not a reliable external-integration callback: use a best-effort {@code
 * ProcessEventListener} or the Durable Outbox for external delivery. A thrown exception or {@code
 * null} resolution fails the Process invocation. The application or dependency-injection
 * container owns the handler lifecycle.
 *
 * @author yusu
 */
@FunctionalInterface
public interface FailureHandler {
    /**
     * Handles one terminal action failure.
     *
     * @param context immutable failure details
     * @return non-null terminal resolution
     */
    FailureResolution handle(FailureContext context);
}
