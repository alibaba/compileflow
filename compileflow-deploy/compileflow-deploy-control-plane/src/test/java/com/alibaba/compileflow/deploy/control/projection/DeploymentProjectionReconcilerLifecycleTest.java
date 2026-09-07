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
package com.alibaba.compileflow.deploy.control.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DeploymentProjectionReconcilerLifecycleTest {
    @Test
    void interruptedCloseWaitsForForcedTerminationAndRestoresInterruptStatus() throws Exception {
        DeploymentProjectionReconciler task = mock(DeploymentProjectionReconciler.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        when(task.reconcile()).thenAnswer(invocation -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException stop) {
                interrupted.countDown();
                Thread.interrupted();
                Thread.sleep(100L);
            } finally {
                completed.countDown();
            }
            return null;
        });

        DeploymentProjectionReconciler.ScheduledReconciler reconciler =
                new DeploymentProjectionReconciler.ScheduledReconciler(task, Duration.ofDays(1));
        reconciler.start();
        assertThat(started.await(1L, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean closeReturnedInterrupted = new AtomicBoolean();
        Thread closer = new Thread(() -> {
            Thread.currentThread().interrupt();
            reconciler.close();
            closeReturnedInterrupted.set(Thread.currentThread().isInterrupted());
        });
        closer.start();

        assertThat(interrupted.await(1L, TimeUnit.SECONDS)).isTrue();
        closer.join(2_000L);
        assertThat(closer.isAlive()).isFalse();
        assertThat(completed.getCount()).isZero();
        assertThat(closeReturnedInterrupted).isTrue();
    }
}
