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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ActionBackedLifecycleTest {
    @Test
    void startsAndStopsExactlyOnce() {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        ActionBackedLifecycle lifecycle =
                new ActionBackedLifecycle(starts::incrementAndGet, stops::incrementAndGet, 123);

        lifecycle.start();
        lifecycle.start();
        lifecycle.stop(callbacks::incrementAndGet);
        lifecycle.stop(callbacks::incrementAndGet);

        assertThat(starts).hasValue(1);
        assertThat(stops).hasValue(1);
        assertThat(callbacks).hasValue(2);
        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(lifecycle.getPhase()).isEqualTo(123);
    }

    @Test
    void reportsStoppedAndInvokesCallbackWhenStopActionFails() {
        ActionBackedLifecycle lifecycle = new ActionBackedLifecycle(() -> {}, () -> {
            throw new IllegalStateException("stop failed");
        }, 0);
        AtomicInteger callbacks = new AtomicInteger();
        lifecycle.start();

        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> lifecycle.stop(callbacks::incrementAndGet))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stop failed");

        assertThat(callbacks).hasValue(1);
        assertThat(lifecycle.isRunning()).isFalse();
    }
}
