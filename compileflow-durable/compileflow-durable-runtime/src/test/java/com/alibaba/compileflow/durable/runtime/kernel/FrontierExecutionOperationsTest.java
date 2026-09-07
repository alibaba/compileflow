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
package com.alibaba.compileflow.durable.runtime.kernel;

import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FrontierExecutionOperationsTest {
    @SuppressWarnings("unchecked")
    private static List<Object> items(Map<String, Object> state) {
        return (List<Object>) state.get("items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(Map<String, Object> state) {
        return (Map<String, Object>) state.get("details");
    }

    @SuppressWarnings("unchecked")
    private static Set<Object> tags(Map<String, Object> state) {
        return (Set<Object>) details(state).get("tags");
    }

    @Test
    void stateSnapshotsDetachAndFreezeNestedValues() {
        List<Object> sourceItems = new ArrayList<>(List.of("first"));
        Set<Object> sourceTags = new LinkedHashSet<>(Set.of("durable"));
        byte[] sourcePayload = {1, 2};
        Map<String, Object> sourceDetails = new LinkedHashMap<>();
        sourceDetails.put("tags", sourceTags);
        sourceDetails.put("payload", sourcePayload);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("items", sourceItems);
        source.put("details", sourceDetails);

        Map<String, Object> state = FrontierExecutionOperations.mutableState(source);
        sourceItems.add("external-change");
        sourceTags.add("external-change");
        sourcePayload[0] = 9;

        assertThat(state.get("items")).isEqualTo(List.of("first"));
        assertThat(details(state).get("tags")).isEqualTo(Set.of("durable"));
        assertThat((byte[]) details(state).get("payload")).containsExactly(1, 2);
        Map<String, Object> readOnly = FrontierExecutionOperations.readOnlyState(state);
        assertThatThrownBy(() -> readOnly.put("other", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> items(readOnly).add("mutation")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> details(readOnly).put("other", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> tags(readOnly).add("mutation")).isInstanceOf(UnsupportedOperationException.class);

        state.put("other", "detached");
        assertThat(readOnly).doesNotContainKey("other");
    }

    @Test
    void acceptedUpdatesCannotRetainMutableStateAliases() {
        List<Object> updateItems = new ArrayList<>(List.of("accepted"));
        Map<String, Object> state = new LinkedHashMap<>();

        FrontierExecutionOperations.applyUpdates(state, Map.of("items", updateItems), Set.of("items"));
        updateItems.add("late-mutation");

        assertThat(state.get("items")).isEqualTo(List.of("accepted"));
        assertThatThrownBy(() -> items(state).add("mutation")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void effectRequestOwnsTheDurableInputSnapshot() {
        List<Object> values = new ArrayList<>(List.of("original"));
        Map<String, Object> state = FrontierExecutionOperations.mutableState(Map.of("values", values));
        Map<String, Object> transientInput = FrontierExecutionOperations.effectInput(state, Map.of("items", "values"));

        EffectRequest request = new EffectRequest("effect", EffectRecoveryPlan.manual(), transientInput);
        transientInput.put("late", true);

        assertThat(request.input()).containsOnlyKeys("items");
        assertThat(request.input().get("items")).isEqualTo(List.of("original"));
        assertThatThrownBy(() -> request.input().put("other", true)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cyclicStateFailsBeforeFrontierExecution() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> FrontierExecutionOperations.mutableState(cyclic))
            .withMessageContaining("Cyclic Durable value graph");
    }

    @Test
    void runtimeSnapshotsBoundEntireGraphsBeforeApplyingUpdates() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("items", List.of("original"));
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("items", List.of("replacement"));
        invalid.put("undeclared", true);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> FrontierExecutionOperations.applyUpdates(state, invalid, Set.of("items")))
            .withMessageContaining("undeclared state field");
        assertThat(state.get("items")).isEqualTo(List.of("original"));

        List<Object> sharedLeaf = new ArrayList<>();
        for (int index = 0; index < 1_001; index++) {
            sharedLeaf.add("value");
        }
        List<Object> fanOut = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            fanOut.add(sharedLeaf);
        }
        assertThatIllegalArgumentException()
            .isThrownBy(() -> FrontierExecutionOperations.applyUpdates(state, Map.of("items", fanOut), Set.of("items")))
            .withMessageContaining("total nested collection entries");

        Object nested = "value";
        for (int depth = 0; depth < 65; depth++) {
            nested = List.of(nested);
        }
        Object tooDeep = nested;
        assertThatIllegalArgumentException()
            .isThrownBy(() -> FrontierExecutionOperations.applyUpdates(state, Map.of("items", tooDeep), Set.of("items")))
            .withMessageContaining("maximum nested depth");
    }

    @Test
    void waitFrontierResultRejectsMismatchedResumeElement() {
        WaitRequest wait = new WaitRequest("approval", "approved", Map.of());
        SemanticCheckpoint otherCheckpoint = SemanticCheckpoint.afterElement("other", List.of());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new FrontierStepResult.Waiting(wait, otherCheckpoint, Map.of()))
            .withMessageContaining("SemanticCheckpoint");
    }

    @Test
    void boundaryRequestsCannotLeaveTheirFrontierRunnable() {
        SemanticCheckpoint before = SemanticCheckpoint.beforeElement("boundary", List.of());

        assertAll(() -> assertThatIllegalArgumentException()
            .isThrownBy(() -> new FrontierStepResult.Waiting(new WaitRequest("boundary", null, Map.of()), before,
                    Map.of())), () -> assertThatIllegalArgumentException()
            .isThrownBy(() -> new FrontierStepResult.TimerWaiting(TimerRequest.at("boundary", Instant.EPOCH), before,
                    Map.of())), () -> assertThatIllegalArgumentException()
            .isThrownBy(() -> new FrontierStepResult.EffectWaiting(new EffectRequest("boundary",
                            EffectRecoveryPlan.manual(), Map.of()), before, Map.of())), () -> assertThatIllegalArgumentException()
            .isThrownBy(() -> new FrontierStepResult.ProcessCallRequested(new ProcessCallRequest("boundary", Map.of()),
                    before, Map.of())));
    }

    @Test
    void parallelIterationFrameOwnsItsNestedCollectionSnapshot() {
        List<String> values = new ArrayList<>(List.of("original"));
        ParallelForEachFrame frame = new ParallelForEachFrame("loop", 0, Map.of("items", values));

        values.add("late-mutation");

        assertThat(frame.currentValue()).isEqualTo(Map.of("items", List.of("original")));
    }

    @Test
    void parallelIterationFrameExposesReadOnlyCollections() {
        ParallelForEachFrame frame = new ParallelForEachFrame("loop", 0, new ArrayList<>(List.of("original")));

        assertThatThrownBy(() -> ((List<?>) frame.currentValue()).clear()).isInstanceOf(
                UnsupportedOperationException.class);
    }

    @Test
    void recoveredParallelResultsOwnTheirNestedCollectionSnapshot() {
        List<String> result = new ArrayList<>(List.of("original"));
        MultiInstanceState recovered = new MultiInstanceState("loop", 1, List.of(result), Set.of(), Set.of(0), 1);

        result.add("late-mutation");

        assertThat(recovered.orderedResults()).isEqualTo(List.of(List.of("original")));
    }

    @Test
    void recordedParallelResultsOwnTheirNestedCollectionSnapshot() {
        List<String> result = new ArrayList<>(List.of("original"));
        MultiInstanceState completed = new MultiInstanceState("loop", 1).issueNext(1).recordResult(0, result);

        result.add("late-mutation");

        assertThat(completed.orderedResults()).isEqualTo(List.of(List.of("original")));
    }

    @Test
    void runtimeDiagnosticStringsRedactDataAndCapabilities() {
        String dataSecret = "customer-secret-value";
        String eventSecret = "secret-event-name";
        String tokenSecret = "one-time-trigger-secret";
        Map<String, Object> sensitive = Map.of("credential", dataSecret);
        WaitRequest waitRequest = new WaitRequest("approval", eventSecret, sensitive);
        EffectRequest effectRequest = new EffectRequest("charge", EffectRecoveryPlan.manual(), sensitive);
        SemanticCheckpoint waitCheckpoint = SemanticCheckpoint.afterElement("approval", List.of());
        SemanticCheckpoint timerCheckpoint = SemanticCheckpoint.afterElement("deadline", List.of());
        SemanticCheckpoint effectCheckpoint = SemanticCheckpoint.afterElement("charge", List.of());
        DurableProgramTestRunner.TriggerToken token =
                new DurableProgramTestRunner.TriggerToken("run-1", 1L, "approval", tokenSecret);

        List<Object> diagnosticValues = List.of(new BoundaryCompletion.WaitCompleted(1L, "approval", eventSecret,
                        sensitive), new BoundaryCompletion.EffectSucceeded(2L, "charge", sensitive), waitRequest,
                effectRequest, new FrontierStepResult.Completed(sensitive, sensitive),
                new FrontierStepResult.Waiting(waitRequest, waitCheckpoint, sensitive),
                new FrontierStepResult.TimerWaiting(TimerRequest.at("deadline", Instant.EPOCH), timerCheckpoint,
                        sensitive), new FrontierStepResult.EffectWaiting(effectRequest, effectCheckpoint, sensitive),
                token, new DurableProgramTestRunner.ActiveWait(token, waitRequest, waitCheckpoint, sensitive));

        String diagnostics = diagnosticValues.toString();
        assertThat(diagnostics).contains("<redacted>").doesNotContain(dataSecret, eventSecret, tokenSecret);
    }

    @Test
    void forEachCurrentValueReusesTheImmutableFrameSnapshot() {
        ForEachFrame frame = new ForEachFrame("items", 0, List.of(Map.of("id", 1)));

        assertThat(frame.currentValue()).isSameAs(frame.currentValue());
    }
}
