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
package com.alibaba.compileflow.engine.core.concurrent;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;

/**
 * Live observations for one engine-owned bounded executor.
 *
 * @author yusu
 */
public final class ProcessExecutorMetrics {
    private final ThreadPoolExecutor executor;
    private final LongAdder rejected = new LongAdder();

    ProcessExecutorMetrics(ThreadPoolExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        RejectedExecutionHandler delegate = executor.getRejectedExecutionHandler();
        executor.setRejectedExecutionHandler((task, pool) -> {
            rejected.increment();
            delegate.rejectedExecution(task, pool);
        });
    }

    /**
     * Returns the approximate number of tasks currently executing.
     *
     * @return active task count
     */
    public long activeCount() {
        return executor.getActiveCount();
    }

    /**
     * Returns the number of tasks currently waiting in the executor queue.
     *
     * @return pending task count
     */
    public long pendingCount() {
        return executor.getQueue().size();
    }

    /**
     * Returns the cumulative number of rejected submissions for this executor.
     *
     * @return rejected submission count
     */
    public long rejectedCount() {
        return rejected.sum();
    }
}
