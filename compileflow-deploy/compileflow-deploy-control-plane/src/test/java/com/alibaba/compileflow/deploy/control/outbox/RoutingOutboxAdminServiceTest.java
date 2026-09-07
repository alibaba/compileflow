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
package com.alibaba.compileflow.deploy.control.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxStore;
import org.junit.jupiter.api.Test;

class RoutingOutboxAdminServiceTest {
    @Test
    void expiredClaimsDegradePipelineHealth() {
        RoutingOutboxStore outboxRepository = mock(RoutingOutboxStore.class);
        when(outboxRepository.countExpiredClaims()).thenReturn(1L);
        RoutingOutboxScheduler scheduler = mock(RoutingOutboxScheduler.class);
        when(scheduler.isRunning()).thenReturn(true);
        RoutingOutboxAdminService control = new RoutingOutboxAdminService(outboxRepository, scheduler);

        RoutingOutboxAdminService.PipelineHealth health = control.getHealth();

        assertThat(health.getProcessingCount()).isZero();
        assertThat(health.getExpiredClaimCount()).isOne();
        assertThat(health.getStatus()).isEqualTo("DEGRADED");
    }

    @Test
    void outboxRepositoryFailureMakesPipelineHealthDown() {
        RoutingOutboxStore outboxRepository = mock(RoutingOutboxStore.class);
        when(outboxRepository.countByStatus(RoutingOutboxRecord.Status.PENDING))
            .thenThrow(new IllegalStateException("repository unavailable"));
        RoutingOutboxScheduler scheduler = mock(RoutingOutboxScheduler.class);
        when(scheduler.isRunning()).thenReturn(true);
        RoutingOutboxAdminService control = new RoutingOutboxAdminService(outboxRepository, scheduler);

        RoutingOutboxAdminService.PipelineHealth health = control.getHealth();

        assertThat(health.isOutboxStateAvailable()).isFalse();
        assertThat(health.getStatus()).isEqualTo("DOWN");
    }

    @Test
    void deadLetterRequeuePreservesRepositoryFailure() {
        RoutingOutboxStore outboxRepository = mock(RoutingOutboxStore.class);
        IllegalStateException failure = new IllegalStateException("repository unavailable");
        when(outboxRepository.requeueFailed()).thenThrow(failure);
        RoutingOutboxAdminService control =
                new RoutingOutboxAdminService(outboxRepository, mock(RoutingOutboxScheduler.class));

        assertThatThrownBy(control::requeueDeadLetters).isSameAs(failure);
    }

    @Test
    void pendingDeliveryWorkDoesNotMakeAHealthyAsyncPipelineDegraded() {
        RoutingOutboxStore outboxRepository = mock(RoutingOutboxStore.class);
        when(outboxRepository.countByStatus(RoutingOutboxRecord.Status.PENDING)).thenReturn(10_000L);
        RoutingOutboxScheduler scheduler = mock(RoutingOutboxScheduler.class);
        when(scheduler.isRunning()).thenReturn(true);
        RoutingOutboxAdminService control = new RoutingOutboxAdminService(outboxRepository, scheduler);

        RoutingOutboxAdminService.PipelineHealth health = control.getHealth();

        assertThat(health.getPendingCount()).isEqualTo(10_000L);
        assertThat(health.getStatus()).isEqualTo("UP");
    }

    @Test
    void stoppedSchedulerMakesPipelineHealthDown() {
        RoutingOutboxStore outboxRepository = mock(RoutingOutboxStore.class);
        RoutingOutboxAdminService control =
                new RoutingOutboxAdminService(outboxRepository, mock(RoutingOutboxScheduler.class));

        assertThat(control.getHealth().getStatus()).isEqualTo("DOWN");
    }
}
