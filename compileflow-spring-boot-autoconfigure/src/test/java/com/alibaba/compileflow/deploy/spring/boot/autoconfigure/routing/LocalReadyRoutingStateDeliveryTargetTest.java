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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStatePayloads;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.state.DesiredRoutingState;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LocalReadyRoutingStateDeliveryTargetTest {
    private static final Duration CONVERGENCE_TIMEOUT = Duration.ofSeconds(12);

    private static String routingKey() {
        return RoutingStateKeys.aliasState("compileflow.deployment.", "default", "payment.flow", "production");
    }

    private static String payload() {
        return RoutingStatePayloads.aliasStateJson("default", "payment.flow", "production", "v1", null, null, 1L, "test",
                1_800_000_000_000L);
    }

    @Test
    void waitsForLocalReadinessBeforeDeliverySucceeds() {
        LocalRoutingReconciler applier = mock(LocalRoutingReconciler.class);
        LocalReadyRoutingStateDeliveryTarget target =
                new LocalReadyRoutingStateDeliveryTarget(applier, CONVERGENCE_TIMEOUT);

        target.deliver(routingKey(), payload());

        verify(applier).applyAndAwait(any(DesiredRoutingState.class), eq(CONVERGENCE_TIMEOUT));
    }

    @Test
    void propagatesConvergenceFailureToTheOutboxDispatcher() {
        LocalRoutingReconciler applier = mock(LocalRoutingReconciler.class);
        IllegalStateException convergenceFailure = new IllegalStateException("runtime installation failed");
        doThrow(convergenceFailure).when(applier).applyAndAwait(any(DesiredRoutingState.class), any(Duration.class));
        LocalReadyRoutingStateDeliveryTarget target =
                new LocalReadyRoutingStateDeliveryTarget(applier, CONVERGENCE_TIMEOUT);

        assertThatThrownBy(() -> target.deliver(routingKey(), payload())).isSameAs(convergenceFailure);
    }

    @Test
    void rejectsPayloadsWhoseIdentityDoesNotMatchTheOutboxKey() {
        LocalRoutingReconciler applier = mock(LocalRoutingReconciler.class);
        LocalReadyRoutingStateDeliveryTarget target =
                new LocalReadyRoutingStateDeliveryTarget(applier, CONVERGENCE_TIMEOUT);

        assertThatThrownBy(() -> target.deliver(RoutingStateKeys.aliasState("compileflow.deployment.", "default",
                        "other.flow", "production"), payload()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("identity do not match");
    }
}
