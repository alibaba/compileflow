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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProcessExecutorMetricsTest {
    @Test
    void reportsThisExecutorOccupancyAndRejections() throws Exception {
        ProcessEngineExecutors group = ProcessEngineExecutors.create(config(0));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            group.action().execute(() -> {
                started.countDown();
                await(release);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(group.actionMetrics().activeCount()).isEqualTo(1);
            assertThat(group.actionMetrics().pendingCount()).isZero();
            assertThatThrownBy(() -> group.action().execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
            assertThat(group.actionMetrics().rejectedCount()).isEqualTo(1);
        } finally {
            release.countDown();
            group.close();
        }
    }

    @Test
    void pendingCountIsScopedToEachEngine() throws Exception {
        ProcessEngineExecutors first = ProcessEngineExecutors.create(config(1));
        ProcessEngineExecutors second = ProcessEngineExecutors.create(config(1));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            first.action().execute(() -> {
                started.countDown();
                await(release);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            first.action().execute(() -> {});
            assertThat(first.actionMetrics().pendingCount()).isEqualTo(1);
            assertThat(second.actionMetrics().activeCount()).isZero();
            assertThat(second.actionMetrics().pendingCount()).isZero();
        } finally {
            release.countDown();
            first.close();
            second.close();
        }
    }

    private static ProcessExecutorConfig config(int pending) {
        return ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxConcurrency(1)
            .runtimeLoadMaxPending(pending)
            .actionTimeoutMaxConcurrency(1)
            .actionTimeoutMaxPending(pending)
            .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
