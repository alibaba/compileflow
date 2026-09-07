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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryKind;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierId;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceKey;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceResult;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.TimerRequest;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.runtime.script.JavaSourceScriptExecutor;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurableCompiledInterpretedDifferentialTest {
    @Test
    void nullableBooleanConditionUsesTheSameFalseSemantics() throws Exception {
        Programs programs = programs(
                """
                <bpm code="nullable.condition">
                  <var name="approved" dataType="java.lang.Boolean" inOutType="param"/>
                  <start id="start"><transition to="loop"/></start>
                  <while id="loop" condition="approved" maxIterations="1">
                    <transition to="end"/>
                    <start id="bodyStart"><transition to="bodyEnd"/></start>
                    <end id="bodyEnd"/>
                  </while>
                  <end id="end"/>
                </bpm>
                """);
        assertThat(programs
            .advance(ContinuationSnapshot.start(java.util.Collections.singletonMap("approved", null)), List.of())
            .outcome())
            .isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void pastAbsoluteTimerPreservesDueAtAndConsumesAfterRecovery() throws Exception {
        Instant dueAt = Instant.parse("2000-01-01T00:00:00Z");
        String source = resource("timer-process.bpm")
            .replace("duration=\"PT5M\"", "wakeAtExpression=\"dueAt\"")
            .replace("<start", """
                    <var name="dueAt" dataType="java.time.Instant" inOutType="param"/>
                    <start""");
        Programs programs = programs(source);
        MachineTurnResult waiting = programs.advance(ContinuationSnapshot.start(Map.of("dueAt", dueAt)), List.of());
        TimerRequest request = ((FrontierStepResult.TimerWaiting) waiting.outcome()).timerRequest();
        assertThat(request.wakeAt()).isEqualTo(dueAt);
        DurableValueSerializer serializer = new DurableValueSerializer(DurableCompilerTestSupport.lower(TbbpmXmlParser
                    .getInstance()
                    .parse(FlowSource.of("past-timer", source.getBytes(StandardCharsets.UTF_8))),
                ScriptExecutorRegistry.from(List.of())));
        Instant scheduledAt = Instant.parse("2026-09-07T00:00:00Z");
        MachineTurnResult completed = programs.advance(serializer.decode(serializer.encode(waiting.continuation())),
                List.of(
                        resolved(BoundaryKind.TIMER, FrontierId.ROOT, 10,
                                new BoundaryCompletion.TimerFired(1, "cooldown", scheduledAt, dueAt, scheduledAt))));
        assertThat(completed.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
        assertThat(completed.consumedOccurrences()).hasSize(1);
    }

    @Test
    void waitTimerAndEffectBoundariesProduceIdenticalMachineTurns() throws Exception {
        Programs wait = programs(resource("wait-process.bpm"));
        MachineTurnResult waiting = wait.advance(ContinuationSnapshot.start(Map.of()), List.of());
        assertThat(waiting.outcome()).isInstanceOf(FrontierStepResult.Waiting.class);
        wait.advance(waiting.continuation(),
                List.of(
                        resolved(BoundaryKind.WAIT, FrontierId.ROOT, 1,
                                new BoundaryCompletion.WaitCompleted(1, "approval", "approved", Map.of("approved", true)))));

        Programs timer = programs(resource("timer-process.bpm"));
        MachineTurnResult timerWaiting = timer.advance(ContinuationSnapshot.start(Map.of()), List.of());
        TimerRequest request = ((FrontierStepResult.TimerWaiting) timerWaiting.outcome()).timerRequest();
        assertThat(request.scheduleKind()).isEqualTo(TimerRequest.ScheduleKind.AFTER);
        Instant scheduledAt = Instant.parse("2026-08-19T00:00:00Z");
        Instant wakeAt = scheduledAt.plus(request.duration());
        timer.advance(timerWaiting.continuation(),
                List.of(
                        resolved(BoundaryKind.TIMER, FrontierId.ROOT, 2,
                                new BoundaryCompletion.TimerFired(1, "cooldown", scheduledAt, wakeAt, wakeAt))));

        Programs effect = programs(resource("effect-process.bpm"));
        MachineTurnResult effectWaiting = effect.advance(ContinuationSnapshot.start(Map.of()), List.of());
        assertThat(effectWaiting.outcome()).isInstanceOf(FrontierStepResult.EffectWaiting.class);
        effect.advance(effectWaiting.continuation(),
                List.of(
                        resolved(BoundaryKind.EFFECT, FrontierId.ROOT, 3,
                                new BoundaryCompletion.EffectSucceeded(1, "notify", Map.of()))));
    }

    @Test
    void concurrentFrontiersAndJoinMergeProduceIdenticalMachineTurns() throws Exception {
        Programs programs = programs(parallelWaitProcess());
        MachineTurnResult fork = programs.advance(ContinuationSnapshot.start(Map.of()), List.of());
        MachineTurnResult firstWait = programs.advance(fork.continuation(), List.of());
        MachineTurnResult secondWait = programs.advance(firstWait.continuation(), List.of());

        FrontierSnapshot right = frontierAt(secondWait.continuation(), "rightWait");
        MachineTurnResult rightArrived = programs.advance(secondWait.continuation(),
                List.of(
                        resolved(BoundaryKind.WAIT, right.frontierId(), 5,
                                new BoundaryCompletion.WaitCompleted(2, "rightWait", "right", Map.of("right", "R")))));
        FrontierSnapshot left = frontierAt(rightArrived.continuation(), "leftWait");
        MachineTurnResult joined = programs.advance(rightArrived.continuation(),
                List.of(
                        resolved(BoundaryKind.WAIT, left.frontierId(), 6,
                                new BoundaryCompletion.WaitCompleted(1, "leftWait", "left", Map.of("left", "L")))));
        MachineTurnResult completed = programs.advance(joined.continuation(), List.of());

        assertThat(completed.outcome())
            .isInstanceOfSatisfying(FrontierStepResult.Completed.class, value -> assertThat(value.output())
                .containsEntry("left", "L")
                .containsEntry("right", "R"));
    }

    @Test
    void compiledJavaCodeProducesIdenticalMachineTurns() throws Exception {
        try (ScriptExecutorRegistry scripts = ScriptExecutorRegistry.from(List.of(new JavaSourceScriptExecutor()))) {
            Programs programs = programs(javaScriptProcess(), scripts);

            MachineTurnResult completed =
                    programs.advance(ContinuationSnapshot.start(Map.of("value", 3, "result", 0)), List.of());

            assertThat(completed.outcome())
                .isInstanceOfSatisfying(FrontierStepResult.Completed.class, value -> assertThat(value.output())
                    .containsEntry("result", 5));
        }
    }

    @Test
    void exhaustedBudgetsYieldAtTheSameCheckpoint() throws Exception {
        MachineTurnResult yielded =
                programs(completedProcess()).advance(ContinuationSnapshot.start(Map.of()), List.of(), 1);

        assertThat(yielded.outcome()).isInstanceOf(FrontierStepResult.Yielded.class);
        assertThat(yielded.continuation().resumePoint()).isEqualTo(ResumePoint.beforeElement("end"));
    }

    @Test
    void whileIterationLimitIsAStableTerminalFailureForBothRealizations() throws Exception {
        Programs programs = programs(boundedWhileProcess());
        MachineTurnResult waiting = programs.advance(ContinuationSnapshot.start(Map.of()), List.of());

        assertThat(waiting.outcome()).isInstanceOf(FrontierStepResult.Waiting.class);
        FrontierStepResult outcome = programs
            .advance(waiting.continuation(),
                    List.of(
                            resolved(BoundaryKind.WAIT, FrontierId.ROOT, 7,
                                    new BoundaryCompletion.WaitCompleted(1, "approval", "approved", Map.of()))))
            .outcome();

        assertThat(outcome)
            .isInstanceOfSatisfying(FrontierStepResult.Failed.class, failure -> assertThat(failure.code())
                .isEqualTo(FrontierStepResult.WHILE_MAX_ITERATIONS_EXCEEDED));
    }

    @Test
    void parallelForEachWithoutAggregationProducesIdenticalMachineTurns() throws Exception {
        Programs programs = programs(parallelContinueProcess());
        MachineTurnResult turn =
                programs.advance(ContinuationSnapshot.start(Map.of("items", List.of("A", "B", "C"))), List.of());
        for (
                int remainingTurns = 16;
                !(turn.outcome() instanceof FrontierStepResult.Completed) && remainingTurns > 0;
                remainingTurns--) {
            turn = programs.advance(turn.continuation(), List.of());
        }

        assertThat(turn.outcome())
            .isInstanceOfSatisfying(FrontierStepResult.Completed.class, value -> assertThat(value.output()).isEmpty());
    }

    private Programs programs(String source) {
        return programs(source, ScriptExecutorRegistry.from(List.of()));
    }

    private Programs programs(String source, ScriptExecutorRegistry scripts) {
        DurableMachinePlan machine = DurableCompilerTestSupport.lower(TbbpmXmlParser
                    .getInstance()
                    .parse(FlowSource.of("durable-differential", source.getBytes(StandardCharsets.UTF_8))), scripts);
        DurableProgram compiled = new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults())
            .compile(machine, getClass().getClassLoader());
        DurableProgram interpreted = new DurableInterpretedProgramCompiler(JavaDiagnosticsConfig.defaults())
            .compile(machine, getClass().getClassLoader());
        DurableExecutionContext context = new DurableExecutionContext(machine,
                new DurableActionInvoker(ProcessComponentResolver.disabled(), scripts, getClass().getClassLoader())
                    .withScriptPrograms(com.alibaba.compileflow.engine.core.runtime.script.ScriptProgramCatalog.compile(machine.semanticPlan(),
                            scripts)), DurableWaitDescriptionProvider.defaults(), new DurableValueSerializer(machine));
        return new Programs(compiled, interpreted, context);
    }

    private static OccurrenceResult resolved(BoundaryKind kind, FrontierId frontierId, long identity,
            BoundaryCompletion completion) {
        return new OccurrenceResult(new OccurrenceKey(kind, new UUID(0L, identity)), frontierId, completion);
    }

    private static FrontierSnapshot frontierAt(ContinuationSnapshot continuation, String elementId) {
        return continuation
            .frontiers()
            .stream()
            .filter(frontier -> elementId.equals(frontier.resumePoint().elementId()))
            .findFirst()
            .orElseThrow();
    }

    private String resource(String name) throws IOException {
        String path = "/durable-compatibility/v1/" + name;
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing Durable differential resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String parallelWaitProcess() {
        return """
            <bpm code="parallel.differential">
              <var name="left" dataType="java.lang.String" defaultValue="" inOutType="return"/>
              <var name="right" dataType="java.lang.String" defaultValue="" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <parallel id="fork" g="50,0,40,40">
                <transition to="leftWait"/><transition to="rightWait"/>
              </parallel>
              <waitEventTask id="leftWait" event="left" g="120,0,100,40"><transition to="join"/></waitEventTask>
              <waitEventTask id="rightWait" event="right" g="120,80,100,40"><transition to="join"/></waitEventTask>
              <parallel id="join" g="260,40,40,40"><transition to="end"/></parallel>
              <end id="end" g="340,40,32,32"/>
            </bpm>
            """;
    }

    private static String javaScriptProcess() {
        return """
            <bpm code="java-script.differential">
              <var name="value" dataType="java.lang.Integer" inOutType="param"/>
              <var name="result" dataType="java.lang.Integer" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="calculate"/></start>
              <scriptTask id="calculate" g="60,0,100,40">
                <action type="script" execution="replayable" language="java">
                    <input target="value" dataType="java.lang.Integer" source="value"/>
                    <output dataType="java.lang.Integer" target="result"/>
                    <code>return value + 2;</code>

                </action>
                <transition to="end"/>
              </scriptTask>
              <end id="end" g="220,0,32,32"/>
            </bpm>
            """;
    }

    private static String completedProcess() {
        return """
            <bpm code="completed.differential">
              <start id="start" g="0,0,32,32"><transition to="end"/></start>
              <end id="end" g="100,0,32,32"/>
            </bpm>
            """;
    }

    private static String boundedWhileProcess() {
        return """
            <bpm code="bounded-while.differential">
              <start id="start" g="0,0,32,32"><transition to="poll"/></start>
              <while id="poll" condition="true" maxIterations="1" g="50,0,180,100">
                <transition to="end"/>
                <start id="pollStart"><transition to="approval"/></start>
                <waitEventTask id="approval" event="approved" g="90,20,100,40"><transition to="pollEnd"/></waitEventTask>
                <end id="pollEnd"/>
              </while>
              <end id="end" g="280,0,32,32"/>
            </bpm>
            """;
    }

    private static String parallelContinueProcess() {
        return """
            <bpm code="parallel.continue.differential">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <start id="start"><transition to="loop"/></start>
              <foreach id="loop" execution="parallel"
                   collection="items" item="item" itemType="java.lang.String">
                <transition to="end"/>
                <start id="loopStart"><transition to="skip"/></start>
                <continue id="skip"/>
                <end id="loopEnd"/>
              </foreach>
              <end id="end"/>
            </bpm>
            """;
    }

    private record Programs(DurableProgram compiled, DurableProgram interpreted, DurableExecutionContext context) {
        MachineTurnResult advance(ContinuationSnapshot continuation, List<OccurrenceResult> occurrences)
                throws Exception {
            return advance(continuation, occurrences, TurnBudget.defaults().maxSteps());
        }

        MachineTurnResult advance(ContinuationSnapshot continuation, List<OccurrenceResult> occurrences, int maxSteps)
                throws Exception {
            MachineTurnResult compiledResult =
                    compiled.advance(continuation, occurrences, new TurnBudget(maxSteps, 32), context);
            MachineTurnResult interpretedResult =
                    interpreted.advance(continuation, occurrences, new TurnBudget(maxSteps, 32), context);
            assertThat(interpretedResult).isEqualTo(compiledResult);
            return compiledResult;
        }
    }
}
