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
package com.alibaba.compileflow.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.program.DurableProgram;
import com.alibaba.compileflow.durable.postgres.PostgresDurableStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Enumeration;
import java.net.URL;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class BenchmarkValidationTest {
    @Test
    void eachMachineInvocationReceivesAnUnspentTurnBudget() throws Exception {
        DurableMachineAdvanceBenchmark benchmark = new DurableMachineAdvanceBenchmark();
        DurableProgram consumesBudget =
                (continuation, available, budget, context) -> {
            assertThat(budget.hasRemainingSteps()).isTrue();
            while (budget.tryConsumeStep()) {
                // Exhaust this turn to expose accidental sharing with the next invocation.
            }
            return new MachineTurnResult(new FrontierStepResult.Completed(Map.of(), Map.of()), List.of());
        };
        set(benchmark, "program", consumesBudget);
        set(benchmark, "input", Map.of());
        benchmark.advanceToCompletion();
        benchmark.advanceToCompletion();
    }

    @Test
    void resumedRunConsumesItsCompletedOccurrenceBeforeIssuingAnotherWait() throws Exception {
        var database = new DurablePostgresBoundaryBenchmark.DatabaseState();
        database.snapshotBytes = 16384;
        var claim = mock(DurableStore.RunClaim.class);
        var completed = mock(DurableStore.WaitResult.class);
        var key = new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT, java.util.UUID.randomUUID());
        when(completed.occurrence()).thenReturn(key);
        when(claim.occurrenceResults()).thenReturn(List.of(completed));
        var method = database.getClass().getDeclaredMethod("waitCommit", DurableStore.RunClaim.class, String.class);
        method.setAccessible(true);
        var commit = (DurableStore.TurnCommit) method.invoke(database, claim, "next-wait");
        assertThat(commit.consumedOccurrences()).containsExactly(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CompileFlowExecuteBenchmark", "BpmnExecuteBenchmark",
            "CompileFlowConcurrentExecuteBenchmark", "CompileFlowAliasExecuteBenchmark",
            "ProcessCallExecuteBenchmark", "StructuredGatewayCodeGenerationBenchmark"})
    void failedSetupClosesItsEngine(String name) throws Exception {
        Object benchmark = Class.forName("com.alibaba.compileflow.benchmarks." + name).getConstructor().newInstance();
        if (benchmark instanceof ProcessCallExecuteBenchmark calls) {
            calls.callDepth = -1;
        }
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader missingFlows = new ClassLoader(original) {
            @Override
            public URL getResource(String path) {
                return path.startsWith("flows/") ? null : super.getResource(path);
            }

            @Override
            public Enumeration<URL> getResources(String path) throws IOException {
                return path.startsWith("flows/") ? Collections.emptyEnumeration() : super.getResources(path);
            }
        };
        try {
            Thread.currentThread().setContextClassLoader(missingFlows);
            assertThatThrownBy(() -> benchmark.getClass().getMethod("setup").invoke(benchmark))
                .isInstanceOf(InvocationTargetException.class);
            Field field = benchmark.getClass().getDeclaredField("engine");
            field.setAccessible(true);
            ProcessEngine engine = (ProcessEngine) field.get(benchmark);
            assertThat(engine).as("Failure must occur after engine construction").isNotNull();
            ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "probe",
                    """
                <bpm code="probe"><start id="s"><transition to="e"/></start><end id="e"/></bpm>
                """);
            assertThatThrownBy(() -> engine.tooling().generateJavaCode(definition)).isInstanceOf(
                    IllegalStateException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            benchmark.getClass().getMethod("tearDown").invoke(benchmark);
        }
    }

    @Test
    void terminalMachineFailureMustNotBecomeACompletionSample() throws Exception {
        DurableMachineAdvanceBenchmark benchmark = new DurableMachineAdvanceBenchmark();
        DurableProgram failed =
                (continuation, available, budget, context) -> new MachineTurnResult(new FrontierStepResult.Failed("TE"
                                + "ST_FAILURE", "planned failure"), List.of());
        set(benchmark, "program", failed);
        set(benchmark, "input", Map.of());
        assertThatThrownBy(benchmark::advanceToCompletion).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lostRunFenceMustNotBecomeACompletionSample() throws Exception {
        var database = new DurablePostgresBoundaryBenchmark.DatabaseState();
        set(database, "store", mock(PostgresDurableStore.class));
        var state = new DurablePostgresBoundaryBenchmark.CompleteRunState();
        set(state, "database", database);
        set(state, "claim", mock(DurableStore.RunClaim.class));
        assertThatThrownBy(() -> new DurablePostgresBoundaryBenchmark().completeRun(state))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lostOutboxFenceMustFailInvocationCleanup() throws Exception {
        var database = new DurablePostgresBoundaryBenchmark.DatabaseState();
        set(database, "store", mock(PostgresDurableStore.class));
        var state = new DurablePostgresBoundaryBenchmark.OutboxReadyState();
        set(state, "database", database);
        set(state, "claim", mock(DurableStore.OutboxClaim.class));
        assertThatThrownBy(state::tearDown).isInstanceOf(IllegalStateException.class);
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
