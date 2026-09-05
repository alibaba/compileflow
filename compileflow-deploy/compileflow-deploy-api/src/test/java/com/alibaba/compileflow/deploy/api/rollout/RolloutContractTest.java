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
package com.alibaba.compileflow.deploy.api.rollout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RolloutContractTest {
    private static ProcessRollout completedAllAtOnce() {
        Instant now = Instant.ofEpochMilli(1_000L);
        return ProcessRollout
            .builder()
            .id("rollout-1")
            .alias(ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, "order.flow", "prod"))
            .targetVersion(ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, "order.flow", "v1"))
            .baseAliasRevision(0L)
            .aliasRevision(1L)
            .strategy(RolloutStrategy.ALL_AT_ONCE)
            .phase(RolloutPhase.COMPLETED)
            .targetWeightBps(10_000)
            .rolloutRevision(1L)
            .idempotencyKey("deploy-v1")
            .operationKind(RolloutOperationKind.DEPLOY)
            .requestFingerprint("a".repeat(64))
            .createdBy("operator")
            .createdAt(now)
            .updatedAt(now)
            .completedAt(now)
            .build();
    }

    @Test
    void queryRejectsAmbiguousBlankNamespace() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RolloutQuery(" ", null, null, null, null, null, 20));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RolloutQuery(null, null, "x".repeat(129), null, null, null, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> new RolloutCursor(" next "));
    }

    @Test
    void pageRejectsCursorWithoutItems() {
        ProcessRollout record = completedAllAtOnce();
        RolloutCursor cursor = new RolloutCursor("next");

        new RolloutPage(List.of(record), cursor);
        assertThatIllegalArgumentException().isThrownBy(() -> new RolloutPage(List.of(), cursor));
    }

    @Test
    void auditNotesNormalizeUnicodeBoundaryWhitespace() {
        ProcessRef.Alias alias = ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, "order.flow", "prod");
        ProcessRef.Version version = ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, "order.flow", "v1");

        CreateRolloutCommand command =
                CreateRolloutCommand.allAtOnce("deploy-v1", alias, version, 0L, "operator", "\u00a0ready\u00a0");

        assertThat(command.getNotes()).isEqualTo("ready");
    }

    @Test
    void canaryRequiresADistinctCapturedBaseline() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessRollout
                .builder()
                .id("rollout-1")
                .alias(ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, "order.flow", "prod"))
                .targetVersion(ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, "order.flow", "v2"))
                .baseAliasRevision(1L)
                .aliasRevision(2L)
                .strategy(RolloutStrategy.CANARY)
                .phase(RolloutPhase.IN_PROGRESS)
                .targetWeightBps(1_000)
                .rolloutRevision(1L)
                .idempotencyKey("canary-v2")
                .operationKind(RolloutOperationKind.DEPLOY)
                .requestFingerprint("a".repeat(64))
                .createdBy("operator")
                .createdAt(Instant.ofEpochMilli(1_000L))
                .updatedAt(Instant.ofEpochMilli(1_000L))
                .build());
    }

    @Test
    void eventTransitionsFollowTheRolloutStateMachine() {
        Instant now = Instant.ofEpochMilli(1_000L);

        new RolloutEvent(1L, "rollout-1", 1L, "CANARY_STARTED", null, RolloutPhase.IN_PROGRESS, "operator", null, now);
        new RolloutEvent(2L, "rollout-1", 2L, "CANARY_WEIGHT_UPDATED", RolloutPhase.IN_PROGRESS,
                RolloutPhase.IN_PROGRESS, "operator", null, now);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RolloutEvent(1L, "rollout-1", 1L, "ABORTED", null, RolloutPhase.ABORTED, "operator",
                    null, now));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RolloutEvent(2L, "rollout-1", 2L, "COMPLETED", null, RolloutPhase.COMPLETED,
                    "operator", null, now));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RolloutEvent(3L, "rollout-1", 2L, "ABORTED", RolloutPhase.COMPLETED,
                    RolloutPhase.ABORTED, "operator", null, now));
    }
}
