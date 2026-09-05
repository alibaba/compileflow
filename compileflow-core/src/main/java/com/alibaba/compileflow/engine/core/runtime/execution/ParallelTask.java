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
package com.alibaba.compileflow.engine.core.runtime.execution;

import com.alibaba.compileflow.engine.core.semantic.SemanticText;
import java.util.Objects;

/**
 * Named value-returning task executed by {@link ParallelExecutor}.
 *
 * @param <T> task result type
 * @author yusu
 */
record ParallelTask<T>(String name, ThrowingSupplier<T> task) {
    ParallelTask {
        name = SemanticText.requireIdentity(name, "Parallel task name");
        task = Objects.requireNonNull(task, "Parallel task body must not be null");
    }

    static <T> ParallelTask<T> of(String name, ThrowingSupplier<T> task) {
        return new ParallelTask<>(name, task);
    }

    static ParallelTask<Void> run(String name, ThrowingRunnable task) {
        Objects.requireNonNull(task, "Parallel task body must not be null");
        return of(name, () -> {
            task.run();
            return null;
        });
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
