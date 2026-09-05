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
package com.alibaba.compileflow.engine.spi.event;

/**
 * Observes best-effort process engine lifecycle telemetry.
 * <p>
 * These events are observational only. Asynchronous delivery can be dropped when
 * the bounded event executor is saturated or shutting down, and delivery may be
 * concurrent or out of order. A listener must never be the authority for business
 * correctness, audit, billing, or reliable integration. Use an application-owned
 * transaction/outbox, or the Durable Journal/Outbox where applicable, for those
 * responsibilities.
 * <p>
 * Listener failures are isolated by the engine publisher: one failing listener does not
 * abort process execution or prevent other listeners from receiving the same event.
 * Implementations must be thread-safe because different events may be dispatched concurrently.
 * Work must remain bounded; synchronous event mode directly delays the Process invocation.
 * The application or dependency-injection container owns the listener lifecycle.
 *
 * @author yusu
 */
@FunctionalInterface
public interface ProcessEventListener {
    /**
     * Returns whether this listener accepts the event.
     * <p>
     * Implementations may override this method to avoid receiving unrelated event types or
     * process codes. The publisher evaluates the predicate immediately before delivery and
     * isolates predicate failures in the same way as {@link #onEvent(ProcessEvent)} failures.
     *
     * @param event immutable lifecycle event
     * @return {@code true} when {@link #onEvent(ProcessEvent)} should be invoked
     */
    default boolean supports(ProcessEvent event) {
        return true;
    }

    /**
     * Handles one engine lifecycle event.
     *
     * @param event immutable lifecycle event
     */
    void onEvent(ProcessEvent event);
}
