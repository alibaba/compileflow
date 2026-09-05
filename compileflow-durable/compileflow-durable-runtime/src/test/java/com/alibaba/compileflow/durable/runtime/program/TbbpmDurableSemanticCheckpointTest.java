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

import static com.alibaba.compileflow.durable.runtime.kernel.DurableProgramTestSupport.advanceAfter;
import static com.alibaba.compileflow.durable.runtime.kernel.DurableProgramTestSupport.advanceStart;
import static com.alibaba.compileflow.durable.runtime.kernel.DurableProgramTestSupport.resolved;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.ForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.ResumeDescriptor;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.TimerRequest;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TbbpmDurableSemanticCheckpointTest {
    @Test
    void loopWaitRecoversOnlyFromProcessSemanticCoordinates() throws Exception {
        CompiledMachineProgram compiled = compile(loopWaitFlow());
        DurableExecutionContext context = context(compiled);
        FrontierStepResult.Waiting first =
                (FrontierStepResult.Waiting) advanceStart(compiled.program(), Map.of("items", List.of("a", "b")),
                        context);

        assertThat(first.checkpoint().resumePoint()).isEqualTo(ResumePoint.afterElement("approval"));
        assertThat(first.checkpoint().scopeFrames()).containsExactly(
                new ForEachFrame("itemsLoop", 0, List.of("a", "b")));

        FrontierStepResult.Waiting second = (FrontierStepResult.Waiting) advanceAfter(compiled.program(),
                first.checkpoint(), first.state(),
                new BoundaryCompletion.WaitCompleted(1, "approval", "approved", Map.of()), context);
        assertThat(second.checkpoint().scopeFrames()).containsExactly(
                new ForEachFrame("itemsLoop", 1, List.of("a", "b")));

        FrontierStepResult completed = advanceAfter(compiled.program(), second.checkpoint(), second.state(),
                new BoundaryCompletion.WaitCompleted(2, "approval", "approved", Map.of()), context);
        assertThat(completed).isInstanceOf(FrontierStepResult.Completed.class);
        assertThat(source(loopWaitFlow()))
            .contains("SemanticCheckpoint.afterElement(\"approval\"")
            .contains("checkpoint.resumePoint().key()")
            .doesNotContain("ProgramDigest")
            .doesNotContain("ProcessCursor")
            .doesNotContain("continuationId");
    }

    @Test
    void sequentialAggregationSurvivesRecoveryWithoutPublishingPartialOutput() throws Exception {
        CompiledMachineProgram compiled = compile(aggregatingLoopWaitFlow());
        DurableExecutionContext context = context(compiled);
        FrontierStepResult.Waiting first =
                (FrontierStepResult.Waiting) advanceStart(compiled.program(), Map.of("items", List.of("a", "b")),
                        context);

        FrontierStepResult.Waiting second = (FrontierStepResult.Waiting) advanceAfter(compiled.program(),
                first.checkpoint(), first.state(),
                new BoundaryCompletion.WaitCompleted(1, "approval", "approved", Map.of("slot", "a")), context);
        assertThat(second.state().get("results")).isNull();
        assertThat(second.checkpoint().scopeFrames())
            .containsExactly(new ForEachFrame("itemsLoop", 1, List.of("a", "b"), List.of("a")));

        DurableValueSerializer serializer = new DurableValueSerializer(compiled.machinePlan());
        ContinuationSnapshot recovered = serializer.decode(serializer.encode(
                new ContinuationSnapshot(second.checkpoint().resumePoint(), second.state(),
                        second.checkpoint().scopeFrames())));
        FrontierStepResult completed = compiled
            .program()
            .advance(recovered,
                    List.of(
                            resolved(
                                    new BoundaryCompletion.WaitCompleted(2, "approval", "approved", Map.of("slot", "b")))),
                    TurnBudget.defaults(), context)
            .outcome();

        assertThat(completed)
            .isInstanceOfSatisfying(FrontierStepResult.Completed.class, result -> assertThat(result.output())
                .containsEntry("results", List.of("a", "b")));
    }

    @Test
    void timerUsesAfterTimerAndRejectsWrongFactKind() throws Exception {
        CompiledMachineProgram compiled = compile(timerFlow());
        DurableExecutionContext context = context(compiled);
        FrontierStepResult.TimerWaiting waiting =
                (FrontierStepResult.TimerWaiting) advanceStart(compiled.program(), Map.of(), context);
        assertThat(waiting.timerRequest()).isEqualTo(TimerRequest.after("cooling", Duration.ofMinutes(5)));
        assertThat(waiting.checkpoint().resumePoint()).isEqualTo(ResumePoint.afterElement("cooling"));
        assertThatThrownBy(() -> advanceAfter(compiled.program(), waiting.checkpoint(), waiting.state(),
                new BoundaryCompletion.WaitCompleted(1, "cooling", null, Map.of()), context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TimerFired");

        Instant scheduled = Instant.parse("2026-08-12T00:00:00Z");
        assertThat(
                advanceAfter(compiled.program(), waiting.checkpoint(), waiting.state(),
                        new BoundaryCompletion.TimerFired(1, "cooling", scheduled, scheduled.plusSeconds(300),
                                scheduled.plusSeconds(300)), context))
            .isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void eventDeadlineIsPartOfTheSameTypedWaitOccurrence() throws Exception {
        CompiledMachineProgram compiled = compile(waitDeadlineFlow());
        DurableExecutionContext context = context(compiled);
        FrontierStepResult.Waiting waiting =
                (FrontierStepResult.Waiting) advanceStart(compiled.program(), Map.of(), context);

        assertThat(waiting.waitRequest().deadlineAfter()).isEqualTo(Duration.ofMinutes(30));
        Instant deadline = Instant.parse("2026-08-16T00:30:00Z");
        assertThat(
                advanceAfter(compiled.program(), waiting.checkpoint(), waiting.state(),
                        new BoundaryCompletion.WaitExpired(1, "approval", deadline, deadline.plusMillis(1)), context))
            .isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void whileBoundIsFrozenIntoTheRecoveryFrame() {
        CompiledMachineProgram compiled = compile(boundedWhileWaitFlow());

        assertThat(((DurableMachinePlan.Iteration.While) compiled.machinePlan().requireIteration("poll"))
            .maxIterations())
            .isEqualTo(100);
        assertThat(compiled
            .machinePlan()
            .resumes()
            .get(ResumePoint.afterElement("approval").key())
            .expectedFramePath())
            .singleElement()
            .extracting(ResumeDescriptor.FrameDescriptor::maxIterations)
            .isEqualTo(100);
    }

    private CompiledMachineProgram compile(String xml) {
        return DurableCompilerTestSupport.compile(TbbpmXmlParser
                    .getInstance()
                    .parse(FlowSource.of("checkpoint", xml.getBytes(StandardCharsets.UTF_8))),
                getClass().getClassLoader());
    }

    private String source(String xml) {
        var model =
                TbbpmXmlParser.getInstance().parse(FlowSource.of("checkpoint", xml.getBytes(StandardCharsets.UTF_8)));
        var machinePlan = DurableCompilerTestSupport.lower(model);
        return new DurableJavaProgramCompiler().generateSource(machinePlan);
    }

    private DurableExecutionContext context(CompiledMachineProgram compiled) {
        return new DurableExecutionContext(compiled.machinePlan(), DurableActionInvoker.unavailable(),
                DurableWaitDescriptionProvider.defaults(), new DurableValueSerializer(compiled.machinePlan()));
    }

    private String loopWaitFlow() {
        return """
            <bpm code="durable.loop.wait">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <start id="start" g="0,0,32,32"><transition to="itemsLoop"/></start>
              <foreach id="itemsLoop" g="50,0,200,100"
                   collection="items" item="item" itemType="java.lang.String">
                <transition to="end"/>
                <start id="itemsStart"><transition to="approval"/></start>
                <waitEventTask id="approval" event="approved" g="80,20,100,40"><transition to="itemsEnd"/></waitEventTask>
                <end id="itemsEnd"/>
              </foreach>
              <end id="end" g="280,0,32,32"/>
            </bpm>
            """;
    }

    private String boundedWhileWaitFlow() {
        return """
            <bpm code="durable.loop.unbounded">
              <start id="start" g="0,0,32,32"><transition to="poll"/></start>
              <while id="poll" g="50,0,200,100" condition="true"
                   index="attempt" maxIterations="100">
                <transition to="end"/>
                <start id="pollStart"><transition to="approval"/></start>
                <waitEventTask id="approval" event="approved" g="80,20,100,40"><transition to="pollEnd"/></waitEventTask>
                <end id="pollEnd"/>
              </while>
              <end id="end" g="300,0,32,32"/>
            </bpm>
            """;
    }

    private String aggregatingLoopWaitFlow() {
        return """
            <bpm code="durable.loop.wait.output">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start"><transition to="itemsLoop"/></start>
              <foreach id="itemsLoop" collection="items"
                   item="item" itemType="java.lang.String">
                <output target="results" source="slot"/>
                <transition to="end"/>
                <start id="itemsStart"><transition to="approval"/></start>
                <waitEventTask id="approval" event="approved"><transition to="itemsEnd"/></waitEventTask>
                <end id="itemsEnd"/>
              </foreach>
              <end id="end"/>
            </bpm>
            """;
    }

    private String timerFlow() {
        return """
            <bpm code="durable.timer">
              <start id="start" g="0,0,32,32"><transition to="cooling"/></start>
              <timerTask id="cooling" duration="PT5M" g="60,0,100,40"><transition to="end"/></timerTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """;
    }

    private String waitDeadlineFlow() {
        return """
            <bpm code="durable.wait.timeout">
              <start id="start" g="0,0,32,32"><transition to="approval"/></start>
              <waitEventTask id="approval" event="approved" timeout="PT30M" g="60,0,100,40">
                <transition to="end"/>
              </waitEventTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """;
    }
}
