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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.durable.api.DurableProcessEngine;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DurableWorkerLifecycleTest {
    @Test
    void contextDelegatesStartAndDrainedShutdownToRuntime() {
        DurableProcessEngine coordinator = mock(DurableProcessEngine.class);
        AtomicBoolean running = new AtomicBoolean();
        when(coordinator.isRunning()).thenAnswer(ignored -> running.get());
        doAnswer(ignored -> {
            running.set(true);
            return null;
        }).when(coordinator).start();
        doAnswer(invocation -> {
            running.set(false);
            return null;
        }).when(coordinator).stop();

        new ApplicationContextRunner()
            .withBean(DurableProcessEngine.class, () -> coordinator)
            .withBean(DurableWorkerLifecycle.class, () -> new DurableWorkerLifecycle(coordinator))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(DurableWorkerLifecycle.class).isRunning()).isTrue();
                verify(coordinator).start();
            });

        assertThat(running).isFalse();
        verify(coordinator).stop();
    }
}
