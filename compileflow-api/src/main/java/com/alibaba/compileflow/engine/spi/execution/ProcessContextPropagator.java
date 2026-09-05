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
 * Propagates application ambient context across ProcessEngine-owned thread boundaries.
 *
 * <p>Implementations must be thread-safe, bounded, and non-blocking. Capture must produce an
 * immutable snapshot that can be opened concurrently on multiple worker threads. Each returned
 * scope is thread-confined and must restore the worker's previous context when closed. Failures
 * fail correctness-bearing action or parallel execution. A best-effort observer host may instead
 * drop its delivery according to that observer's documented failure contract.
 *
 * <p>This SPI does not propagate CompileFlow execution state and is never persisted or restored by
 * Durable execution. The application or dependency-injection container owns the propagator and
 * everything captured by it; ProcessEngine does not close supplied collaborators.
 *
 * @author yusu
 */
@FunctionalInterface
public interface ProcessContextPropagator {
    /**
     * Returns a propagator that carries no application context.
     *
     * @return no-op propagator
     */
    static ProcessContextPropagator none() {
        return () -> () -> () -> {};
    }

    /**
     * Captures application context from the current submitting thread.
     *
     * @return immutable, concurrently reusable snapshot
     */
    Snapshot capture();

    /**
     * Immutable application context captured from one submitting thread.
     */
    @FunctionalInterface
    interface Snapshot {
        /**
         * Opens this context on the current worker thread.
         *
         * @return non-null scope restoring the previous context on close
         */
        Scope open();
    }

    /**
     * Thread-confined application context binding.
     */
    @FunctionalInterface
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
