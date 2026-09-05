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
package com.alibaba.compileflow.durable.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.model.ActiveEffect;
import com.alibaba.compileflow.durable.api.model.ActiveWorkSummary;
import com.alibaba.compileflow.durable.api.model.EffectReadinessCode;
import com.alibaba.compileflow.durable.api.model.EffectExecutionStatus;
import com.alibaba.compileflow.durable.api.model.OutboxEventQuery;
import com.alibaba.compileflow.durable.api.model.OutboxEventStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRunControlState;
import com.alibaba.compileflow.durable.api.model.ProcessRunControl;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunQuery;
import com.alibaba.compileflow.durable.api.model.ProcessRunStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.WaitToken;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRef;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GreenfieldDurableApiTest {
    @Test
    void aliasRoutingOptionsContainOnlyBoundedAdmissionInputs() {
        AliasRoutingOptions options = new AliasRoutingOptions("customer-42", Map.of("tier", "L4"));

        assertThat(options.routingKey()).isEqualTo("customer-42");
        assertThat(options.attributes()).containsExactly(Map.entry("tier", "L4"));
        assertThat(AliasRoutingOptions.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("routingKey", "attributes");
        assertThat(Arrays
            .stream(AliasRoutingOptions.class.getRecordComponents())
            .map(component -> component.getName().toLowerCase())
            .toList())
            .noneMatch(name -> name.contains("idempotency") || name.contains("business") || name.contains("runid"));
    }

    @Test
    void applicationFacadeUsesDirectSemanticArguments() throws NoSuchMethodException {
        Set<List<Class<?>>> signatures = Arrays
            .stream(DurableProcessEngine.class.getMethods())
            .filter(method -> method.getName().equals("start"))
            .map(Method::getParameterTypes)
            .map(Arrays::asList)
            .collect(Collectors.toSet());

        assertThat(signatures)
            .containsExactlyInAnyOrder(List.of(ProcessRunId.class, ProcessDefinition.class, Map.class),
                    List.of(ProcessRunId.class, ProcessRef.Version.class, Map.class),
                    List.of(ProcessRunId.class, ProcessRef.Alias.class, Map.class),
                    List.of(ProcessRunId.class, ProcessRef.Alias.class, Map.class, AliasRoutingOptions.class));
        assertThat(DurableProcessEngine.class
            .getMethod("start", ProcessRunId.class, ProcessRef.Alias.class, Map.class)
            .isDefault())
            .isTrue();
        assertThat(DurableProcessEngine.class.getMethod("completeWait", WaitToken.class, Map.class)).isNotNull();
        assertThat(DurableProcessEngine.class.getMethod("cancel", ProcessRunId.class)).isNotNull();
        assertThat(DurableProcessEngine.class.getMethod("getRun", ProcessRunId.class)).isNotNull();
        assertThat(DurableProcessEngine.class.getMethod("listRuns", ProcessRunQuery.class)).isNotNull();
        assertThat(DurableProcessEngine.class.getMethod("getRunResult", ProcessRunId.class)).isNotNull();
        assertThat(Arrays.stream(DurableProcessEngine.class.getMethods()).map(Method::getName))
            .doesNotContain("execute", "trigger", "requestCancel", "complete", "get", "list", "getResult");
        assertThat(ProcessEngine.class.isAssignableFrom(DurableProcessEngine.class)).isFalse();
    }

    @Test
    void runLifecycleAndControlAreIndependentAxes() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        ProcessRun pausedRunnable = new ProcessRun(new ProcessRunId("3ef19fe9-65b7-443f-818f-8a61e1b70cd7"), "sales",
                "approval", ProcessRef.version("sales", "approval", "3"), ProcessRunStatus.RUNNABLE,
                new ProcessRunControl(ProcessRunControlState.PAUSED, 1), ActiveWorkSummary.NONE, now, null, null, null,
                null, now, now, null);

        assertThat(pausedRunnable.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
        assertThat(pausedRunnable.control().state()).isEqualTo(ProcessRunControlState.PAUSED);
        assertThat(ProcessRunStatus.values())
            .containsExactly(ProcessRunStatus.RUNNABLE, ProcessRunStatus.RUNNING, ProcessRunStatus.WAITING,
                    ProcessRunStatus.SUCCEEDED, ProcessRunStatus.FAILED, ProcessRunStatus.CANCELLED);
    }

    @Test
    void runFailureDetailsArePresentOnlyAsACompletePair() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        assertThatThrownBy(() -> new ProcessRun(new ProcessRunId("4ef19fe9-65b7-443f-818f-8a61e1b70cd7"), "sales",
                "approval", ProcessRef.version("sales", "approval", "3"), ProcessRunStatus.RUNNABLE,
                new ProcessRunControl(ProcessRunControlState.ACTIVE, 0), ActiveWorkSummary.NONE, now, null, "CODE", null,
                null, now, now, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failure details");
        assertThatThrownBy(() -> new ProcessRun(new ProcessRunId("5ef19fe9-65b7-443f-818f-8a61e1b70cd7"), "sales",
                "approval", ProcessRef.version("sales", "approval", "3"), ProcessRunStatus.FAILED,
                new ProcessRunControl(ProcessRunControlState.ACTIVE, 0), ActiveWorkSummary.NONE, now, null, "CODE", null,
                null, now, now, now))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("errorCode and errorMessage");
    }

    @Test
    void unknownEffectCarriesReviewAsAnOrthogonalFlag() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        ActiveEffect effect = new ActiveEffect("3ef19fe9-65b7-443f-818f-8a61e1b70cd7", "root", "charge",
                EffectExecutionStatus.UNKNOWN, 2, 1, 3, null, null, now, "EFFECT_DEADLINE_EXCEEDED", 1);

        assertThat(effect.reviewRequired()).isTrue();
        assertThatThrownBy(() -> new ActiveEffect(effect.effectId(), effect.frontierId(), effect.elementId(),
                EffectExecutionStatus.PENDING, 2, 1, 3, null, null, now, "EFFECT_DEADLINE_EXCEEDED", 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void effectReadinessBackoffIsSanitizedOperationalEvidence() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        ActiveEffect effect = new ActiveEffect("3ef19fe9-65b7-443f-818f-8a61e1b70cd7", "root", "charge",
                EffectExecutionStatus.PENDING, 2, 0, 0, EffectReadinessCode.ACTION_NOT_READY, now, null, null, 0);

        assertThat(effect.readinessCode()).isEqualTo(EffectReadinessCode.ACTION_NOT_READY);
        assertThatThrownBy(() -> new ActiveEffect(effect.effectId(), effect.frontierId(), effect.elementId(),
                EffectExecutionStatus.RUNNING, 2, 0, 0, EffectReadinessCode.ACTION_NOT_READY, null, null, null, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outboxOperatorQueryUsesAbandonedRatherThanASecondFailureMachine() {
        OutboxEventQuery query = OutboxEventQuery.abandonedFirstPage(50);
        assertThat(query.statuses()).isEqualTo(Set.of(OutboxEventStatus.ABANDONED));
        assertThatThrownBy(() -> new OutboxEventQuery("invalid namespace", null, Set.of(), null, 50))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("namespace");
    }

    @Test
    void waitTokenStringRepresentationNeverExposesTheBearerCapability() {
        String rawToken = "opaque-one-shot-capability";
        WaitToken token = new WaitToken(rawToken);

        assertThat(token.toString()).isEqualTo("WaitToken[<redacted>]").doesNotContain(rawToken);
        assertThat(WaitToken.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("value");
    }
}
