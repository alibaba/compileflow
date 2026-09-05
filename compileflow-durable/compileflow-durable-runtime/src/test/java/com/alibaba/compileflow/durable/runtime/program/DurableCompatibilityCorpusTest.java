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
package com.alibaba.compileflow.durable.runtime.program;

import static com.alibaba.compileflow.durable.runtime.kernel.DurableProgramTestSupport.resolved;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.ForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.codec.DurableKernelJsonCodec;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.runtime.machine.DurableProcessCompiler;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Freezes Durable V1 facts that released upgrades must continue to resume.
 *
 * @author yusu
 */
class DurableCompatibilityCorpusTest {
    private static final String CORPUS = "/durable-compatibility/v1/";

    @Test
    void decodesTheFrozenStartContinuation() throws Exception {
        CompiledMachineProgram compiled = compile("timer-process.bpm");

        ContinuationSnapshot snapshot = serializer(compiled).decode(payload("empty-snapshot.base64"));

        assertThat(snapshot.resumePoint()).isEqualTo(ResumePoint.start());
        assertThat(snapshot.variables()).isEmpty();
        assertThat(snapshot.scopeFrames()).isEmpty();
    }

    @Test
    void resumesTheFrozenWaitSnapshotAndCommittedResult() throws Exception {
        CompiledMachineProgram compiled = compile("wait-process.bpm");
        ResumePoint point = ResumePoint.afterElement("approval");
        DurableValueSerializer serializer = serializer(compiled);
        ContinuationSnapshot snapshot = serializer.decode(payload("wait-snapshot.base64"));
        Map<String, Object> result = serializer.decodeWaitPayload(payload("wait-result.base64"));

        FrontierStepResult completed = compiled
            .program()
            .advance(new ContinuationSnapshot(point, snapshot.variables(), snapshot.scopeFrames()),
                    List.of(resolved(new BoundaryCompletion.WaitCompleted(1, "approval", "approved", result))),
                    TurnBudget.defaults(), context(compiled, serializer))
            .outcome();

        assertThat(completed)
            .isInstanceOfSatisfying(FrontierStepResult.Completed.class, value -> assertThat(value.output())
                .containsEntry("approved", true));
    }

    @Test
    void resumesTheFrozenTimerSnapshot() throws Exception {
        CompiledMachineProgram compiled = compile("timer-process.bpm");
        ResumePoint point = ResumePoint.afterElement("cooldown");
        DurableValueSerializer serializer = serializer(compiled);
        ContinuationSnapshot snapshot = serializer.decode(payload("timer-snapshot.base64"));
        Instant scheduled = Instant.parse("2026-08-14T00:00:00Z");

        FrontierStepResult completed = compiled
            .program()
            .advance(new ContinuationSnapshot(point, snapshot.variables(), snapshot.scopeFrames()),
                    List.of(
                            resolved(
                                    new BoundaryCompletion.TimerFired(1, "cooldown", scheduled,
                                            scheduled.plusSeconds(300), scheduled.plusSeconds(300)))),
                    TurnBudget.defaults(), context(compiled, serializer))
            .outcome();

        assertThat(completed).isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void resumesTheFrozenEffectSnapshotAndCommittedResult() throws Exception {
        CompiledMachineProgram compiled = compile("effect-process.bpm");
        ResumePoint point = ResumePoint.afterElement("notify");
        DurableValueSerializer serializer = serializer(compiled);
        ContinuationSnapshot snapshot = serializer.decode(payload("effect-snapshot.base64"));
        Map<String, Object> result = serializer.decodeEffectOutput("notify", payload("empty-result.base64"));

        FrontierStepResult completed = compiled
            .program()
            .advance(new ContinuationSnapshot(point, snapshot.variables(), snapshot.scopeFrames()),
                    List.of(resolved(new BoundaryCompletion.EffectSucceeded(1, "notify", result))),
                    TurnBudget.defaults(), context(compiled, serializer))
            .outcome();

        assertThat(completed).isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void resumesTheFrozenLoopSnapshotWithoutImplementationVersionRouting() throws Exception {
        CompiledMachineProgram compiled = compile("foreach-loop.bpm");
        ResumePoint point = ResumePoint.afterElement("approval");
        DurableValueSerializer serializer = serializer(compiled);
        ContinuationSnapshot snapshot = serializer.decode(payload("loop-snapshot.base64"));

        assertThat(snapshot.scopeFrames()).containsExactly(new ForEachFrame("itemsLoop", 0, List.of("a", "b")));

        FrontierStepResult waiting = compiled
            .program()
            .advance(new ContinuationSnapshot(point, snapshot.variables(), snapshot.scopeFrames()),
                    List.of(resolved(new BoundaryCompletion.WaitCompleted(1, "approval", "approved", Map.of()))),
                    TurnBudget.defaults(), context(compiled, serializer))
            .outcome();

        assertThat(waiting)
            .isInstanceOfSatisfying(FrontierStepResult.Waiting.class, value -> assertThat(value
                .checkpoint()
                .scopeFrames())
                .containsExactly(new ForEachFrame("itemsLoop", 1, List.of("a", "b"))));
    }

    @Test
    void decodesTheFrozenKernelFactWithoutTreatingFormatAsIdentity() throws Exception {
        DurableStore.Envelope envelope = stored("kernel-fact.base64");

        assertThat(envelope.formatVersion()).isEqualTo(1);
        assertThat(new DurableKernelJsonCodec().decode(envelope.payload()))
            .containsEntry("event", "WAIT_COMPLETED")
            .containsEntry("occurrenceSequence", 1);
    }

    @Test
    void compilesTheFrozenStructuredAndChildDefinitionSurface() throws Exception {
        byte[] source = resourceAt("/tbbpm-durable-v1/all-constructs.bpm");
        CompiledMachineProgram compiled = DurableCompilerTestSupport.compile(TbbpmXmlParser
                    .getInstance()
                    .parse(FlowSource.of("all-constructs.bpm", source)),
                ScriptExecutorRegistry.from(List.of(new CompatibilityScriptExecutor())), getClass().getClassLoader());

        assertThat(compiled.machinePlan().semanticPlan().getProcessCode()).isEqualTo("durable.protocol.all-constructs");
        assertThat(compiled.machinePlan().semanticPlan().getNodes().keySet())
            .contains("parallelSplit", "parallelJoin", "inclusiveSplit", "inclusiveJoin", "child");
    }

    @Test
    void compilesTheFrozenBpmnProfileThroughProductionDiscovery() throws Exception {
        assertThat(compile("bpmn-wait-process.bpmn").machinePlan().requireStep("approval"))
            .isInstanceOf(DurableMachinePlan.Step.Await.class);
        assertThat(compile("bpmn-timer-process.bpmn").machinePlan().requireStep("cooldown"))
            .isInstanceOf(DurableMachinePlan.Step.Timer.class);
        assertThat(compile("bpmn-effect-process.bpmn").machinePlan().requireStep("notify"))
            .isInstanceOf(DurableMachinePlan.Step.Effect.class);
        assertThat(compile("bpmn-structured-process.bpmn").machinePlan().requireStep("scope"))
            .isInstanceOf(DurableMachinePlan.Step.Advance.class);
    }

    private CompiledMachineProgram compile(String definition) throws IOException {
        byte[] source = resource(definition);
        ProcessModelType modelType = definition.endsWith(".bpmn") ? ProcessModelType.BPMN : ProcessModelType.TBBPM;
        ProcessSemanticCompiler.ProcessSemanticCompilation compilation = ProcessSemanticCompiler
            .discover(modelType, getClass().getClassLoader())
            .compile(ProcessDefinitionSnapshot.of("compatibility", declaredCode(definition), "v1", source,
                    "Frozen Durable compatibility corpus"));
        try (ScriptExecutorRegistry scripts = ScriptExecutorRegistry.from(List.of(new CompatibilityScriptExecutor()))) {
            DurableProcessCompiler compiler = new DurableProcessCompiler(scripts);
            DurableMachinePlan machinePlan = compiler.lower(compilation.semanticPlan(), compilation.structuredPlan());
            return new CompiledMachineProgram(new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults())
                        .compile(machinePlan, getClass().getClassLoader()), machinePlan,
                    compiler.compileScripts(machinePlan));
        }
    }

    private static String declaredCode(String definition) {
        return switch (definition) {
            case "wait-process.bpm" -> "durable.compatibility.wait";
            case "timer-process.bpm" -> "durable.compatibility.timer";
            case "effect-process.bpm" -> "durable.compatibility.effect";
            case "foreach-loop.bpm" -> "durable.compatibility.foreach-loop";
            case "bpmn-wait-process.bpmn" -> "durable.compatibility.bpmn.wait";
            case "bpmn-timer-process.bpmn" -> "durable.compatibility.bpmn.timer";
            case "bpmn-effect-process.bpmn" -> "durable.compatibility.bpmn.effect";
            case "bpmn-structured-process.bpmn" -> "durable.compatibility.bpmn.structured";
            default -> throw new IllegalArgumentException("Unknown compatibility definition: " + definition);
        };
    }

    private static DurableValueSerializer serializer(CompiledMachineProgram compiled) {
        return new DurableValueSerializer(compiled.machinePlan());
    }

    private static DurableExecutionContext context(CompiledMachineProgram compiled, DurableValueSerializer serializer) {
        return new DurableExecutionContext(compiled.machinePlan(), DurableActionInvoker.unavailable(),
                DurableWaitDescriptionProvider.defaults(), serializer);
    }

    private byte[] payload(String name) throws IOException {
        return stored(name).payload();
    }

    private DurableStore.Envelope stored(String name) throws IOException {
        String encoded = new String(resource(name), java.nio.charset.StandardCharsets.US_ASCII).trim();
        return DurableStore.Envelope.fromStoredBytes(Base64.getDecoder().decode(encoded));
    }

    private byte[] resource(String name) throws IOException {
        return resourceAt(CORPUS + name);
    }

    private byte[] resourceAt(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing Durable compatibility corpus resource: " + path);
            }
            return input.readAllBytes();
        }
    }

    private static final class CompatibilityScriptExecutor implements ScriptExecutor {
        @Override
        public String name() {
            return "qlexpress";
        }

        @Override
        public void validate(ScriptProgramSpec spec) {}

        @Override
        public ScriptProgram compile(ScriptProgramSpec spec) {
            return () -> "qlexpress";
        }

        @Override
        public Object evaluate(ScriptProgram script, Map<String, Object> context) {
            throw new UnsupportedOperationException("Compatibility compilation does not execute scripts");
        }
    }
}
