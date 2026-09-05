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
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DurableWorkerHealthTest {
    @Test
    void exposesPersistentMachineryFaultsWithoutFailureMessages() {
        DurableWorkerHealth health = new DurableWorkerHealth(List.of(Operation.TURN));

        for (int attempt = 0; attempt < 3; attempt++) {
            health.fault(Operation.TURN, new IllegalStateException("secret-process-state"));
        }

        DurableWorkerHealth.Snapshot snapshot = health.snapshot();
        Map<String, Object> turn = snapshot.operations().get("turn");
        assertThat(snapshot.degraded()).isTrue();
        assertThat(turn)
            .containsEntry("state", "faulting")
            .containsEntry("consecutiveFaults", 3)
            .containsEntry("lastFailureType", IllegalStateException.class.getName())
            .containsKey("lastFault")
            .doesNotContainValue("secret-process-state");
    }

    @Test
    void aSuccessfulCycleClearsDegradedStateAndTracksProgressSeparately() {
        DurableWorkerHealth health = new DurableWorkerHealth(List.of(Operation.TURN));
        health.fault(Operation.TURN, new IllegalStateException());
        health.successfulCycle(Operation.TURN, false);

        Map<String, Object> idle = health.snapshot().operations().get("turn");
        assertThat(health.snapshot().degraded()).isFalse();
        assertThat(idle)
            .containsEntry("state", "healthy")
            .containsEntry("consecutiveFaults", 0)
            .containsKey("lastSuccessfulCycle")
            .doesNotContainKey("lastProgress");

        health.successfulCycle(Operation.TURN, true);

        assertThat(health.snapshot().operations().get("turn")).containsKey("lastProgress");
    }
}
