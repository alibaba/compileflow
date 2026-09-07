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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.runtime.machine.DurableModelEligibility;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.kernel.BranchActivation;
import com.alibaba.compileflow.durable.runtime.kernel.ConcurrentFrontierOperations;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierId;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceKey;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceResult;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class TbbpmDurableConcurrentGatewayTest {
    @Test
    void parallelWaitsResolveIndependentlyAndMergeDisjointStateInArrivalOrder() throws Exception {
        CompiledMachineProgram compiled = compile(parallelWaitFlow("parallel.waits"));
        DurableProgram program = compiled.program();
        DurableExecutionContext context = context(compiled);

        MachineTurnResult fork = advance(program,
                ContinuationSnapshot.start(Map.of("left", "", "right", "", "value", "")), List.of(),
                TurnBudget.defaults(), context);
        assertThat(fork.outcome()).isInstanceOf(FrontierStepResult.Advanced.class);
        assertThat(fork.continuation().frontiers())
            .extracting(frontier -> frontier.resumePoint().elementId())
            .containsExactly("leftWait", "rightWait");

        MachineTurnResult firstWait = advance(program, fork.continuation(), List.of(), TurnBudget.defaults(), context);
        MachineTurnResult secondWait =
                advance(program, firstWait.continuation(), List.of(), TurnBudget.defaults(), context);
        assertThat(firstWait.outcome()).isInstanceOf(FrontierStepResult.Waiting.class);
        assertThat(secondWait.outcome()).isInstanceOf(FrontierStepResult.Waiting.class);
        assertThat(secondWait.continuation().hasRunnableFrontier(TurnBudget.defaults().maxActiveIterations())).isFalse();

        FrontierSnapshot right = frontierAt(secondWait.continuation(), "rightWait");
        MachineTurnResult rightArrived = advance(program, secondWait.continuation(),
                List.of(waitResult(right, 2, "rightWait", "right", Map.of("right", "R"))), TurnBudget.defaults(),
                context);
        assertThat(rightArrived.outcome()).isInstanceOf(FrontierStepResult.Advanced.class);
        assertThat(rightArrived.continuation().frontiers())
            .extracting(FrontierSnapshot::resumePoint)
            .contains(ResumePoint.atJoin("join"));

        FrontierSnapshot left = frontierAt(rightArrived.continuation(), "leftWait");
        MachineTurnResult joined = advance(program, rightArrived.continuation(),
                List.of(waitResult(left, 1, "leftWait", "left", Map.of("left", "L"))), TurnBudget.defaults(), context);
        assertThat(joined.outcome()).isInstanceOf(FrontierStepResult.Advanced.class);
        assertThat(joined.continuation().frontiers())
            .singleElement()
            .satisfies(frontier -> {
                assertThat(frontier.frontierId()).isEqualTo(FrontierId.ROOT);
                assertThat(frontier.resumePoint()).isEqualTo(ResumePoint.beforeElement("join"));
                assertThat(frontier.variables()).containsEntry("left", "L").containsEntry("right", "R");
            });

        MachineTurnResult completed = advance(program, joined.continuation(), List.of(), TurnBudget.defaults(), context);
        assertThat(completed.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
        assertThat(((FrontierStepResult.Completed) completed.outcome()).output())
            .containsEntry("left", "L")
            .containsEntry("right", "R");
    }

    @Test
    void inclusiveForksEveryMatchingBranchAndUsesDefaultOnlyWhenNoneMatch() throws Exception {
        CompiledMachineProgram compiled = compile(inclusiveFlow());
        DurableExecutionContext context = context(compiled);

        MachineTurnResult both = advance(compiled.program(),
                ContinuationSnapshot.start(Map.of("leftEnabled", true, "rightEnabled", true)), List.of(),
                TurnBudget.defaults(), context);
        assertThat(both.continuation().frontiers())
            .extracting(frontier -> frontier.resumePoint().elementId())
            .containsExactly("leftWait", "rightWait");

        MachineTurnResult fallback = advance(compiled.program(),
                ContinuationSnapshot.start(Map.of("leftEnabled", false, "rightEnabled", false)), List.of(),
                TurnBudget.defaults(), context);
        assertThat(fallback.continuation().frontiers())
            .extracting(frontier -> frontier.resumePoint().elementId())
            .containsExactly("fallbackWait");
    }

    @Test
    void concurrentWaitResultsWritingTheSameVariableFailClosedAtJoin() throws Exception {
        CompiledMachineProgram compiled = compile(parallelWaitFlow("parallel.conflict"));
        DurableExecutionContext context = context(compiled);
        MachineTurnResult fork = advance(compiled.program(),
                ContinuationSnapshot.start(Map.of("left", "", "right", "", "value", "")), List.of(),
                TurnBudget.defaults(), context);
        MachineTurnResult firstWait =
                advance(compiled.program(), fork.continuation(), List.of(), TurnBudget.defaults(), context);
        MachineTurnResult secondWait =
                advance(compiled.program(), firstWait.continuation(), List.of(), TurnBudget.defaults(), context);

        FrontierSnapshot right = frontierAt(secondWait.continuation(), "rightWait");
        MachineTurnResult rightArrived = advance(compiled.program(), secondWait.continuation(),
                List.of(waitResult(right, 2, "rightWait", "right", Map.of("value", "R"))), TurnBudget.defaults(),
                context);
        FrontierSnapshot left = frontierAt(rightArrived.continuation(), "leftWait");

        assertThatThrownBy(() -> advance(compiled.program(), rightArrived.continuation(),
                List.of(waitResult(left, 1, "leftWait", "left", Map.of("value", "L"))), TurnBudget.defaults(), context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same Process variable 'value'");
    }

    @Test
    void persistedFrontierQueuePreventsALongBranchFromStarvingItsSibling() throws Exception {
        CompiledMachineProgram compiled = compile(fairnessFlow());
        DurableExecutionContext context = context(compiled);
        MachineTurnResult fork =
                advance(compiled.program(), ContinuationSnapshot.start(Map.of()), List.of(), TurnBudget.defaults(),
                        context);

        MachineTurnResult longBranchYield =
                advance(compiled.program(), fork.continuation(), List.of(), new TurnBudget(1, 32), context);
        assertThat(longBranchYield.outcome()).isInstanceOf(FrontierStepResult.Yielded.class);
        assertThat(longBranchYield.continuation().frontiers())
            .extracting(frontier -> frontier.resumePoint().elementId())
            .containsExactly("shortWait", "long2");

        MachineTurnResult sibling =
                advance(compiled.program(), longBranchYield.continuation(), List.of(), new TurnBudget(1, 32), context);
        assertThat(sibling.outcome()).isInstanceOf(FrontierStepResult.Waiting.class);
        assertThat(((FrontierStepResult.Waiting) sibling.outcome()).waitRequest().boundaryId()).isEqualTo("shortWait");
    }

    @Test
    void nestedConcurrentJoinPropagatesWritesToItsOuterBranch() throws Exception {
        CompiledMachineProgram compiled = compile(nestedParallelFlow());
        DurableExecutionContext context = context(compiled);
        MachineTurnResult turn = advance(compiled.program(),
                ContinuationSnapshot.start(Map.of("outer", "", "innerLeft", "", "innerRight", "")), List.of(),
                TurnBudget.defaults(), context);
        // Fork the nested region, then issue the three independent Wait occurrences.
        turn = advance(compiled.program(), turn.continuation(), List.of(), TurnBudget.defaults(), context);
        turn = advance(compiled.program(), turn.continuation(), List.of(), TurnBudget.defaults(), context);
        turn = advance(compiled.program(), turn.continuation(), List.of(), TurnBudget.defaults(), context);
        turn = advance(compiled.program(), turn.continuation(), List.of(), TurnBudget.defaults(), context);
        assertThat(turn.continuation().hasRunnableFrontier(TurnBudget.defaults().maxActiveIterations())).isFalse();

        FrontierSnapshot outer = frontierAt(turn.continuation(), "outerRightWait");
        turn = advance(compiled.program(), turn.continuation(),
                List.of(waitResult(outer, 1, "outerRightWait", "outer", Map.of("outer", "O"))), TurnBudget.defaults(),
                context);
        FrontierSnapshot innerRight = frontierAt(turn.continuation(), "innerRightWait");
        turn = advance(compiled.program(), turn.continuation(),
                List.of(waitResult(innerRight, 2, "innerRightWait", "inner-right", Map.of("innerRight", "R"))),
                TurnBudget.defaults(), context);
        FrontierSnapshot innerLeft = frontierAt(turn.continuation(), "innerLeftWait");
        turn = advance(compiled.program(), turn.continuation(),
                List.of(waitResult(innerLeft, 3, "innerLeftWait", "inner-left", Map.of("innerLeft", "L"))),
                TurnBudget.defaults(), context);
        // The inner merge restores its parent frontier. One more turn reaches and merges the outer join.
        turn = advance(compiled.program(), turn.continuation(), List.of(), TurnBudget.defaults(), context);
        assertThat(turn.continuation().frontiers())
            .singleElement()
            .satisfies(frontier -> {
                assertThat(frontier.frontierId()).isEqualTo(FrontierId.ROOT);
                assertThat(frontier.resumePoint()).isEqualTo(ResumePoint.beforeElement("outerJoin"));
                assertThat(frontier.variables())
                    .containsEntry("outer", "O")
                    .containsEntry("innerLeft", "L")
                    .containsEntry("innerRight", "R");
            });
    }

    @Test
    void nestedConcurrentJoinRecoversByteArrayBaselinesEveryTurn() throws Exception {
        String xml = nestedParallelFlow()
            .replace("<bpm code=\"parallel.nested\">",
                    """
                <bpm code="parallel.nested">
                  <var name="payload" dataType="%s" inOutType="param"/>
                """
                        .formatted(BinaryPayload.class.getName()));
        CompiledMachineProgram compiled = compile(xml);
        DurableValueSerializer serializer = new DurableValueSerializer(compiled.machinePlan());
        DurableProgram interpreted = new DurableInterpretedProgramCompiler(com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig.defaults())
            .compile(compiled.machinePlan(), getClass().getClassLoader());
        for (DurableProgram program : List.of(compiled.program(), interpreted)) {
            ContinuationSnapshot continuation =
                    ContinuationSnapshot.start(Map.of("payload", new BinaryPayload(new byte[] {1, 2, 3})));
            boolean completed = false;
            for (int turnIndex = 0; turnIndex < 24; turnIndex++) {
                continuation = serializer.decode(serializer.encode(continuation));
                List<OccurrenceResult> results = continuation
                    .frontiers()
                    .stream()
                    .filter(frontier -> frontier.resumePoint().isAfterElement())
                    .map(frontier -> waitResult(frontier, 1, frontier.resumePoint().elementId(),
                            switch (frontier.resumePoint().elementId()) {
                                case "outerRightWait" -> "outer";
                                case "innerLeftWait" -> "inner-left";
                                case "innerRightWait" -> "inner-right";
                                default -> throw new AssertionError("Unexpected wait");
                            }, Map.of()))
                    .toList();
                MachineTurnResult turn =
                        advance(program, continuation, results, TurnBudget.defaults(), context(compiled));
                if (turn.outcome() instanceof FrontierStepResult.Completed) {
                    completed = true;
                    break;
                }
                continuation = turn.continuation();
                assertThat(((BinaryPayload) continuation.frontiers().get(0).variables().get("payload")).bytes())
                    .containsExactly(1, 2, 3);
            }
            assertThat(completed).isTrue();
        }
    }

    @Test
    void recoveredNestedAncestryStillRejectsDifferentBaselinesAndWrites() {
        String xml = nestedParallelFlow()
            .replace("<bpm code=\"parallel.nested\">",
                    """
                <bpm code="parallel.nested">
                  <var name="payload" dataType="%s" inOutType="param"/>
                """
                        .formatted(BinaryPayload.class.getName()));
        CompiledMachineProgram compiled = compile(xml);
        DurableValueSerializer serializer = new DurableValueSerializer(compiled.machinePlan());
        FrontierSnapshot root = FrontierSnapshot.root(ResumePoint.beforeElement("outerFork"),
                Map.of("outer", "", "innerLeft", "", "innerRight", "", "payload",
                        new BinaryPayload(new byte[] {1, 2, 3})), List.of());
        FrontierSnapshot parent = ConcurrentFrontierOperations
            .fork(root, "outerFork", "outerJoin",
                    List.of(new BranchActivation(0, "innerFork"), new BranchActivation(1, "outerRightWait")))
            .get(0);
        List<FrontierSnapshot> siblings = ConcurrentFrontierOperations
            .fork(parent, "innerFork", "innerJoin",
                    List.of(new BranchActivation(0, "innerLeftWait"), new BranchActivation(1, "innerRightWait")))
            .stream()
            .map(frontier -> ConcurrentFrontierOperations.parkAtJoin(frontier, "innerJoin", frontier.variables(),
                    frontier.scopeFrames(), Set.of()))
            .toList();
        byte[] encoded = serializer.encode(new ContinuationSnapshot(siblings));
        ContinuationSnapshot recovered = serializer.decode(encoded);
        assertThat(ConcurrentFrontierOperations.join(recovered.frontiers(), "innerJoin", "outerJoin").frontierId())
            .isEqualTo(parent.frontierId());
        assertThat(serializer.decode(encoded).frontiers().get(0).branchFrames().get(0))
            .isNotSameAs(recovered.frontiers().get(0).branchFrames().get(0));
        JsonMapper mapper = JsonMapper.builder().build();
        for (String difference : List.of("baseline", "binaryBaseline", "writes")) {
            var tree = mapper.readTree(encoded);
            var ancestor = tree.get("frontiers").get(1).get("branches").get(0).asObject();
            switch (difference) {
                case "baseline" -> ancestor.get("baseline").asObject().put("innerLeft", "different");
                case "binaryBaseline" -> ancestor.get("baseline").get("payload").asObject().put("bytes", "BAUG");
                case "writes" -> ancestor.get("writes").asArray().add("innerLeft");
                default -> throw new AssertionError(difference);
            }
            ContinuationSnapshot changed = serializer.decode(mapper.writeValueAsBytes(tree));
            assertThat(changed.frontiers().get(0).branchFrames().get(0))
                .isNotSameAs(changed.frontiers().get(1).branchFrames().get(0));
            assertThatThrownBy(() -> ConcurrentFrontierOperations.join(changed.frontiers(), "innerJoin", "outerJoin"))
                .as(difference)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identical parent ancestry");
        }
    }

    public record BinaryPayload(byte[] bytes) {}

    @Test
    void concurrentBranchesMayConvergeDirectlyAtTheProcessEnd() throws Exception {
        CompiledMachineProgram compiled = compile(parallelEndFlow());
        DurableExecutionContext context = context(compiled);
        MachineTurnResult fork =
                advance(compiled.program(), ContinuationSnapshot.start(Map.of()), List.of(), TurnBudget.defaults(),
                        context);

        MachineTurnResult left =
                advance(compiled.program(), fork.continuation(), List.of(), TurnBudget.defaults(), context);
        MachineTurnResult joined =
                advance(compiled.program(), left.continuation(), List.of(), TurnBudget.defaults(), context);
        assertThat(joined.continuation().frontiers())
            .singleElement()
            .satisfies(frontier -> {
                assertThat(frontier.frontierId()).isEqualTo(FrontierId.ROOT);
                assertThat(frontier.resumePoint()).isEqualTo(ResumePoint.beforeElement("end"));
            });

        MachineTurnResult completed =
                advance(compiled.program(), joined.continuation(), List.of(), TurnBudget.defaults(), context);
        assertThat(completed.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void sequentialLoopMayOwnConcurrentWaitsAndRecoverEveryTurn() throws Exception {
        CompiledMachineProgram compiled = compile(loopParallelWaitFlow());
        DurableValueSerializer serializer = new DurableValueSerializer(compiled.machinePlan());
        DurableExecutionContext context = context(compiled);
        ContinuationSnapshot continuation = ContinuationSnapshot.start(Map.of());

        for (int remaining = 16; remaining > 0; remaining--) {
            MachineTurnResult turn = advance(compiled.program(), serializer.decode(serializer.encode(continuation)),
                    List.of(), TurnBudget.defaults(), context);
            continuation = turn.continuation();
            if (!continuation.hasRunnableFrontier(TurnBudget.defaults().maxActiveIterations())) {
                break;
            }
        }
        assertThat(continuation.frontiers())
            .extracting(frontier -> frontier.resumePoint().elementId())
            .containsExactlyInAnyOrder("leftWait", "rightWait");

        FrontierSnapshot left = frontierAt(continuation, "leftWait");
        MachineTurnResult turn = advance(compiled.program(), serializer.decode(serializer.encode(continuation)),
                List.of(waitResult(left, 1, "leftWait", "left", Map.of())), TurnBudget.defaults(), context);
        FrontierSnapshot right = frontierAt(turn.continuation(), "rightWait");
        turn = advance(compiled.program(), serializer.decode(serializer.encode(turn.continuation())),
                List.of(waitResult(right, 2, "rightWait", "right", Map.of())), TurnBudget.defaults(), context);

        for (int remaining = 8; remaining > 0 && !(turn.outcome() instanceof FrontierStepResult.Completed); remaining--) {
            turn = advance(compiled.program(), serializer.decode(serializer.encode(turn.continuation())), List.of(),
                    TurnBudget.defaults(), context);
        }
        assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void concurrentChildIsRejectedBeforeItCanCreateAPoisonRun() {
        DurableModelEligibility eligibility = DurableCompilerTestSupport.check(parse(concurrentChildFlow()));

        assertThat(eligibility.problems())
            .extracting(DurableModelEligibility.Problem::code)
            .contains("DURABLE_CONCURRENT_CHILD_FAILURE_UNSUPPORTED");
    }

    @Test
    void staticFrontierOverflowIsRejectedDuringCompilation() {
        DurableModelEligibility eligibility = DurableCompilerTestSupport.check(parse(wideParallelFlow(257)));

        assertThat(eligibility.problems())
            .extracting(DurableModelEligibility.Problem::code)
            .contains("DURABLE_STATIC_FRONTIER_LIMIT_EXCEEDED");
    }

    private MachineTurnResult advance(DurableProgram program, ContinuationSnapshot continuation,
            List<OccurrenceResult> results, TurnBudget budget, DurableExecutionContext context) throws Exception {
        return program.advance(continuation, results, budget, context);
    }

    private OccurrenceResult waitResult(FrontierSnapshot frontier, long sequence, String boundaryId, String event,
            Map<String, Object> payload) {
        return new OccurrenceResult(new OccurrenceKey(com.alibaba.compileflow.durable.runtime.kernel.BoundaryKind.WAIT,
                        UUID.randomUUID()), frontier.frontierId(),
                new BoundaryCompletion.WaitCompleted(sequence, boundaryId, event, payload));
    }

    private FrontierSnapshot frontierAt(ContinuationSnapshot continuation, String elementId) {
        return continuation
            .frontiers()
            .stream()
            .filter(frontier -> elementId.equals(frontier.resumePoint().elementId()))
            .findFirst()
            .orElseThrow();
    }

    private CompiledMachineProgram compile(String xml) {
        return DurableCompilerTestSupport.compile(parse(xml), getClass().getClassLoader());
    }

    private TbbpmModel parse(String xml) {
        return TbbpmXmlParser.getInstance().parse(FlowSource.of("concurrent", xml.getBytes(StandardCharsets.UTF_8)));
    }

    private DurableExecutionContext context(CompiledMachineProgram compiled) {
        return new DurableExecutionContext(compiled.machinePlan(),
                new DurableActionInvoker(ProcessComponentResolver.disabled(), ScriptExecutorRegistry.from(List.of()),
                        getClass().getClassLoader()), DurableWaitDescriptionProvider.defaults(),
                new DurableValueSerializer(compiled.machinePlan()));
    }

    private String parallelWaitFlow(String code) {
        return """
            <bpm code="%s">
              <var name="left" dataType="java.lang.String" inOutType="return"/>
              <var name="right" dataType="java.lang.String" inOutType="return"/>
              <var name="value" dataType="java.lang.String" inOutType="param"/>
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <parallel id="fork" g="50,0,40,40">
                <transition to="leftWait"/><transition to="rightWait"/>
              </parallel>
              <waitEventTask id="leftWait" event="left" g="120,0,100,40"><transition to="join"/></waitEventTask>
              <waitEventTask id="rightWait" event="right" g="120,80,100,40"><transition to="join"/></waitEventTask>
              <parallel id="join" g="260,0,40,40"><transition to="end"/></parallel>
              <end id="end" g="340,0,32,32"/>
            </bpm>
            """
            .formatted(code);
    }

    private String inclusiveFlow() {
        return """
            <bpm code="inclusive.dynamic">
              <var name="leftEnabled" dataType="java.lang.Boolean" inOutType="param"/>
              <var name="rightEnabled" dataType="java.lang.Boolean" inOutType="param"/>
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <inclusive id="fork" g="50,0,40,40">
                <transition to="leftWait" condition="leftEnabled.booleanValue()"/>
                <transition to="rightWait" condition="rightEnabled.booleanValue()"/>
                <transition to="fallbackWait"/>
              </inclusive>
              <waitTask id="leftWait" g="120,0,100,40"><transition to="join"/></waitTask>
              <waitTask id="rightWait" g="120,80,100,40"><transition to="join"/></waitTask>
              <waitTask id="fallbackWait" g="120,160,100,40"><transition to="join"/></waitTask>
              <inclusive id="join" g="260,0,40,40"><transition to="end"/></inclusive>
              <end id="end" g="340,0,32,32"/>
            </bpm>
            """;
    }

    private String fairnessFlow() {
        return """
            <bpm code="parallel.fairness">
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <parallel id="fork" g="50,0,40,40">
                <transition to="long1"/><transition to="shortWait"/>
              </parallel>
              <autoTask id="long1" g="120,0,100,40">
                <action type="java" execution="replayable" class="java.lang.String" method="isEmpty"/>
                <transition to="long2"/>
              </autoTask>
              <autoTask id="long2" g="240,0,100,40">
                <action type="java" execution="replayable" class="java.lang.String" method="isEmpty"/>
                <transition to="join"/>
              </autoTask>
              <waitTask id="shortWait" g="120,80,100,40"><transition to="join"/></waitTask>
              <parallel id="join" g="380,0,40,40"><transition to="end"/></parallel>
              <end id="end" g="460,0,32,32"/>
            </bpm>
            """;
    }

    private String nestedParallelFlow() {
        return """
            <bpm code="parallel.nested">
              <var name="outer" dataType="java.lang.String" inOutType="return"/>
              <var name="innerLeft" dataType="java.lang.String" inOutType="return"/>
              <var name="innerRight" dataType="java.lang.String" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="outerFork"/></start>
              <parallel id="outerFork" g="50,0,40,40">
                <transition to="innerFork"/><transition to="outerRightWait"/>
              </parallel>
              <parallel id="innerFork" g="120,0,40,40">
                <transition to="innerLeftWait"/><transition to="innerRightWait"/>
              </parallel>
              <waitEventTask id="innerLeftWait" event="inner-left" g="190,0,100,40">
                <transition to="innerJoin"/>
              </waitEventTask>
              <waitEventTask id="innerRightWait" event="inner-right" g="190,60,100,40">
                <transition to="innerJoin"/>
              </waitEventTask>
              <parallel id="innerJoin" g="320,0,40,40"><transition to="outerJoin"/></parallel>
              <waitEventTask id="outerRightWait" event="outer" g="190,140,100,40">
                <transition to="outerJoin"/>
              </waitEventTask>
              <parallel id="outerJoin" g="400,0,40,40"><transition to="end"/></parallel>
              <end id="end" g="480,0,32,32"/>
            </bpm>
            """;
    }

    private String parallelEndFlow() {
        return """
            <bpm code="parallel.end">
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <parallel id="fork" g="50,0,40,40">
                <transition to="left"/><transition to="right"/>
              </parallel>
              <autoTask id="left" g="120,0,100,40">
                <action type="java" execution="replayable" class="java.lang.String" method="isEmpty"/>
                <transition to="end"/>
              </autoTask>
              <autoTask id="right" g="120,80,100,40">
                <action type="java" execution="replayable" class="java.lang.String" method="isEmpty"/>
                <transition to="end"/>
              </autoTask>
              <end id="end" g="300,0,32,32"/>
            </bpm>
            """;
    }

    private String loopParallelWaitFlow() {
        return """
            <bpm code="parallel.loop">
              <start id="start" g="0,0,32,32"><transition to="loop"/></start>
              <while id="loop" condition="true" g="40,0,340,200" maxIterations="100">
                <transition to="end"/>
                <start id="loopStart"><transition to="fork"/></start>
                <parallel id="fork" g="70,20,40,40">
                  <transition to="leftWait"/><transition to="rightWait"/>
                </parallel>
                <waitEventTask id="leftWait" event="left" g="140,0,100,40">
                  <transition to="join"/>
                </waitEventTask>
                <waitEventTask id="rightWait" event="right" g="140,80,100,40">
                  <transition to="join"/>
                </waitEventTask>
                <parallel id="join" g="270,40,40,40"><transition to="stop"/></parallel>
                <break id="stop" g="340,40,32,32"/>
                <end id="loopEnd"/>
              </while>
              <end id="end" g="430,0,32,32"/>
            </bpm>
            """;
    }

    private String concurrentChildFlow() {
        return """
            <bpm code="parallel.child">
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <parallel id="fork" g="50,0,40,40">
                <transition to="child"/><transition to="sibling"/>
              </parallel>
              <bpmCall id="child" code="child.flow" version="v1" g="120,0,100,40">
                <transition to="join"/>
              </bpmCall>
              <waitTask id="sibling" g="120,80,100,40"><transition to="join"/></waitTask>
              <parallel id="join" g="260,0,40,40"><transition to="end"/></parallel>
              <end id="end" g="340,0,32,32"/>
            </bpm>
            """;
    }

    private String wideParallelFlow(int branchCount) {
        StringBuilder xml = new StringBuilder(
                """
            <bpm code="parallel.wide">
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <parallel id="fork" g="50,0,40,40">
            """);
        for (int index = 0; index < branchCount; index++) {
            xml.append("<transition to=\"task").append(index).append("\"/>");
        }
        xml.append("</parallel>");
        for (int index = 0; index < branchCount; index++) {
            xml.append("<waitTask id=\"task").append(index).append(
                    "\" g=\"120,0,80,40\"><transition to=\"join\"/></waitTask>");
        }
        return xml
            .append(
                    """
              <parallel id="join" g="240,0,40,40"><transition to="end"/></parallel>
              <end id="end" g="320,0,32,32"/>
            </bpm>
            """)
            .toString();
    }
}
