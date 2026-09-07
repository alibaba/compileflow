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
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.runtime.machine.DurableModelEligibility;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryKind;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceKey;
import com.alibaba.compileflow.durable.runtime.kernel.ParallelForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceResult;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TbbpmDurableParallelForEachTest {
    @Test
    void parallelWaitsDescribeTheirRecoveredItemAndIndex() throws Exception {
        CompiledMachineProgram compiled = compile(
                """
                <bpm code="parallel.foreach.waits">
                  <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
                  <start id="start"><transition to="loop"/></start>
                  <foreach id="loop" execution="parallel" collection="items"
                       item="item" itemType="java.lang.String" index="itemIndex">
                    <transition to="end"/>
                    <start id="loopStart"><transition to="approval"/></start>
                    <waitTask id="approval"><transition to="loopEnd"/></waitTask>
                    <end id="loopEnd"/>
                  </foreach>
                  <end id="end"/>
                </bpm>
                """);
        DurableValueSerializer serializer = serializer(compiled);
        DurableProgram interpreted = new DurableInterpretedProgramCompiler(com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig.defaults())
            .compile(compiled.machinePlan(), getClass().getClassLoader());
        for (DurableProgram program : List.of(compiled.program(), interpreted)) {
            List<Map<String, Object>> described = new ArrayList<>();
            DurableExecutionContext context = new DurableExecutionContext(compiled.machinePlan(),
                    DurableActionInvoker.unavailable(), new DurableWaitDescriptionProvider() {
                        @Override
                        public Map<String, Object> describeWait(
                                com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionContext wait) {
                            described.add(wait.lexicalBindings());
                            return wait.lexicalBindings();
                        }
                    }, serializer);
            MachineTurnResult turn = program.advance(ContinuationSnapshot.start(Map.of("items", List.of("A", "B"))),
                    List.of(), new TurnBudget(100, 2), context);
            for (int index = 0; index < 2; index++) {
                turn = program.advance(serializer.decode(serializer.encode(turn.continuation())), List.of(),
                        new TurnBudget(100, 2), context);
                assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.Waiting.class);
            }
            assertThat(described).containsExactly(Map.of("item", "A", "itemIndex", 0),
                    Map.of("item", "B", "itemIndex", 1));
            assertThat(turn.continuation().hasRunnableFrontier(2)).isFalse();
        }
    }

    @Test
    void sequentialParentMayOwnParallelForEachAndRecoverBetweenEveryTurn() throws Exception {
        CompiledMachineProgram compiled = compile(sequentialParentParallelFlow());
        DurableMachinePlan.Iteration.ForEach inner =
                (DurableMachinePlan.Iteration.ForEach) compiled.machinePlan().requireIteration("inner");
        assertThat(inner.collectionOwnerIterationId()).isEqualTo("outer");
        DurableValueSerializer serializer = serializer(compiled);
        ContinuationSnapshot continuation =
                ContinuationSnapshot.start(Map.of("batches", List.of(List.of("A", "B"), List.of("C", "D", "E"))));
        MachineTurnResult turn = null;
        for (int remainingTurns = 64; remainingTurns > 0; remainingTurns--) {
            turn = compiled
                .program()
                .advance(serializer.decode(serializer.encode(continuation)), List.of(), new TurnBudget(10_000, 2),
                        context(compiled));
            if (turn.outcome() instanceof FrontierStepResult.Completed) {
                break;
            }
            continuation = turn.continuation();
        }

        assertThat(turn).isNotNull();
        assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
        assertThat(((FrontierStepResult.Completed) turn.outcome()).output())
            .containsEntry("outerResults", List.of(List.of("A", "B"), List.of("C", "D", "E")));
    }

    @Test
    void whileParentMayOwnParallelForEach() throws Exception {
        CompiledMachineProgram compiled = compile(whileParentParallelFlow());
        MachineTurnResult turn =
                runToTerminal(compiled, ContinuationSnapshot.start(Map.of("items", List.of("A", "B", "C"))));

        assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
        assertThat(((FrontierStepResult.Completed) turn.outcome()).output()).containsEntry("results",
                List.of("A", "B", "C"));
    }

    @Test
    void parallelParentCannotOwnAnotherParallelForEach() {
        DurableModelEligibility eligibility = DurableCompilerTestSupport.check(parse(parallelParentParallelFlow()));

        assertThat(eligibility.problems())
            .extracting(DurableModelEligibility.Problem::code)
            .contains("DURABLE_NESTED_PARALLELISM_UNSUPPORTED");
    }

    @Test
    void gatewayConcurrentBranchCannotEnterParallelForEach() {
        DurableModelEligibility eligibility = DurableCompilerTestSupport.check(parse(gatewayConcurrentParallelFlow()));

        assertThat(eligibility.problems())
            .extracting(DurableModelEligibility.Problem::code)
            .contains("DURABLE_NESTED_PARALLELISM_UNSUPPORTED");
    }

    @Test
    void inclusiveGatewayBranchCannotEnterParallelForEach() {
        DurableModelEligibility eligibility =
                DurableCompilerTestSupport.check(parse(inclusiveGatewayConcurrentParallelFlow()));

        assertThat(eligibility.problems())
            .extracting(DurableModelEligibility.Problem::code)
            .contains("DURABLE_NESTED_PARALLELISM_UNSUPPORTED");
    }

    @Test
    void childProcessIsRejectedUntilGroupFailureAndCancellationHaveOneLaw() {
        DurableModelEligibility eligibility = DurableCompilerTestSupport.check(parse(parallelChildFlow()));

        assertThat(eligibility.problems())
            .extracting(DurableModelEligibility.Problem::code)
            .contains("DURABLE_CONCURRENT_CHILD_FAILURE_UNSUPPORTED");
    }

    @Test
    void outputTargetItemTypeMustMatchTheOutputSource() {
        String invalid = parallelEffectFlow()
            .replace("name=\"results\" dataType=\"java.util.List&lt;java.lang.String&gt;\"",
                    "name=\"results\" dataType=\"java.util.List&lt;java.lang.Integer&gt;\"");

        assertThatThrownBy(() -> compile(invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must contain output source type");
    }

    @Test
    void largeInputIsStoredOnceAndActiveIterationsPersistOnlyTheirDelta() throws Exception {
        CompiledMachineProgram compiled = compile(parallelEffectFlow());
        DurableValueSerializer serializer = serializer(compiled);
        List<String> items = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            items.add("item-%04d".formatted(index));
        }

        MachineTurnResult turn = compiled
            .program()
            .advance(ContinuationSnapshot.start(Map.of("items", items, "slot", "ignored", "results", List.of())),
                    List.of(), new TurnBudget(10_000, 8), context(compiled));
        byte[] encoded = serializer.encode(turn.continuation());

        assertThat(turn.continuation().frontiers()).hasSize(9);
        assertThat(encoded.length).isLessThan(64 * 1024);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).doesNotContain("\"snapshot\"");
        assertThat(serializer.decode(encoded)).isEqualTo(turn.continuation());
    }

    @Test
    void boundedParallelEffectsRecoverAndCollectInInputOrder() throws Exception {
        CompiledMachineProgram compiled = compile(parallelEffectFlow());
        DurableProgram program = compiled.program();
        DurableExecutionContext context = context(compiled);
        DurableValueSerializer serializer = serializer(compiled);
        TurnBudget budget = new TurnBudget(10_000, 2);

        MachineTurnResult turn = program.advance(ContinuationSnapshot.start(Map.of("items", List.of("A", "B", "C"),
                        "slot", "ignored", "results", List.of())), List.of(), budget, context);
        assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.Advanced.class);
        assertThat(turn.continuation().frontiers()).hasSize(3);
        assertThat(turn
            .continuation()
            .frontiers()
            .stream()
            .filter(frontier -> frontier.multiInstanceController() != null)
            .findFirst()
            .orElseThrow()
            .multiInstanceController()
            .activeIndices())
            .containsExactly(0, 1);
        // A process crash may reconstruct the exact controller and iteration identities.
        ContinuationSnapshot recovered = serializer.decode(serializer.encode(turn.continuation()));
        assertThat(recovered).isEqualTo(turn.continuation());

        turn = program.advance(recovered, List.of(), budget, context);
        assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.EffectWaiting.class);
        FrontierSnapshot zero = waitingIteration(turn.continuation(), 0);
        turn = program.advance(turn.continuation(), List.of(), budget, context);
        FrontierSnapshot one = waitingIteration(turn.continuation(), 1);
        assertThat(turn.continuation().hasRunnableFrontier(2)).isFalse();
        // Complete index 1 first. The rolling window immediately issues index 2.
        turn = program.advance(turn.continuation(), List.of(effectResult(one, 2, "B!")), budget, context);
        assertThat(turn.continuation().frontiers()).hasSize(3);
        assertThat(activeIndices(turn.continuation())).containsExactly(0, 2);
        turn = program.advance(turn.continuation(), List.of(), budget, context);
        FrontierSnapshot two = waitingIteration(turn.continuation(), 2);

        turn = program.advance(turn.continuation(), List.of(effectResult(two, 3, "C!")), budget, context);
        assertThat(activeIndices(turn.continuation())).containsExactly(0);
        zero = waitingIteration(turn.continuation(), 0);
        turn = program.advance(turn.continuation(), List.of(effectResult(zero, 1, "A!")), budget, context);

        assertThat(turn.continuation().frontiers())
            .singleElement()
            .satisfies(frontier -> {
                assertThat(frontier.resumePoint().elementId()).isEqualTo("end");
                assertThat(frontier.variables().get("results")).isEqualTo(List.of("A!", "B!", "C!"));
                assertThat(frontier.variables().get("slot")).isEqualTo("ignored");
            });

        MachineTurnResult completed = program.advance(turn.continuation(), List.of(), budget, context);
        assertThat(completed.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
        assertThat(((FrontierStepResult.Completed) completed.outcome()).output())
            .containsEntry("results", List.of("A!", "B!", "C!"));
    }

    @Test
    void replayableActionsCompleteWithoutCreatingBoundaryOccurrences() throws Exception {
        CompiledMachineProgram compiled = compile(parallelReplayableFlow());
        MachineTurnResult turn = runToTerminal(compiled,
                ContinuationSnapshot.start(Map.of("items", List.of("A", "B", "C"), "slot", "ignored", "results",
                        List.of())));

        assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
        assertThat(turn.consumedOccurrences()).isEmpty();
        assertThat(((FrontierStepResult.Completed) turn.outcome()).output()).containsEntry("results",
                List.of("A", "B", "C"));
    }

    @Test
    void emptyCollectionCompletesWithAnEmptyOrderedOutput() throws Exception {
        CompiledMachineProgram compiled = compile(parallelReplayableFlow());
        MachineTurnResult turn = runToTerminal(compiled,
                ContinuationSnapshot.start(Map.of("items", List.of(), "slot", "ignored", "results", List.of("stale"))));

        assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
        assertThat(((FrontierStepResult.Completed) turn.outcome()).output()).containsEntry("results", List.of());
    }

    @Test
    void parallelContinueWithoutAggregationRecoversBetweenTurns() throws Exception {
        CompiledMachineProgram compiled = compile(parallelContinueWithoutOutputFlow());
        DurableValueSerializer serializer = serializer(compiled);
        ContinuationSnapshot continuation = ContinuationSnapshot.start(Map.of("items", List.of("A", "B", "C")));
        MachineTurnResult turn = null;
        for (int remainingTurns = 16; remainingTurns > 0; remainingTurns--) {
            continuation = serializer.decode(serializer.encode(continuation));
            turn = compiled.program().advance(continuation, List.of(), new TurnBudget(10_000, 2), context(compiled));
            if (turn.outcome() instanceof FrontierStepResult.Completed) {
                break;
            }
            continuation = turn.continuation();
        }

        assertThat(turn).isNotNull();
        assertThat(turn.outcome())
            .isInstanceOfSatisfying(FrontierStepResult.Completed.class, value -> assertThat(value.output()).isEmpty());
    }

    @Test
    void parallelContinueCollectsTheFreshOutputDefault() throws Exception {
        CompiledMachineProgram compiled = compile(parallelContinueWithOutputFlow());
        MachineTurnResult turn =
                runToTerminal(compiled, ContinuationSnapshot.start(Map.of("items", List.of("A", "B", "C"))));

        assertThat(turn.outcome())
            .isInstanceOfSatisfying(FrontierStepResult.Completed.class, value -> assertThat(value.output())
                .containsEntry("results", List.of("A", "unassigned", "C")));
    }

    private MachineTurnResult runToTerminal(CompiledMachineProgram compiled, ContinuationSnapshot initial)
            throws Exception {
        TurnBudget budget = new TurnBudget(10_000, 2);
        MachineTurnResult turn = compiled.program().advance(initial, List.of(), budget, context(compiled));
        for (
                int remainingTurns = 32;
                !(turn.outcome() instanceof FrontierStepResult.Completed) && remainingTurns > 0;
                remainingTurns--) {
            turn = compiled.program().advance(turn.continuation(), List.of(), budget, context(compiled));
        }
        return turn;
    }

    private static List<Integer> activeIndices(ContinuationSnapshot continuation) {
        return continuation
            .frontiers()
            .stream()
            .filter(frontier -> frontier.multiInstanceController() != null)
            .findFirst()
            .orElseThrow()
            .multiInstanceController()
            .activeIndices()
            .stream()
            .toList();
    }

    private static FrontierSnapshot waitingIteration(ContinuationSnapshot continuation, int index) {
        return continuation
            .frontiers()
            .stream()
            .filter(frontier -> frontier.multiInstanceController() == null)
            .filter(frontier -> !frontier.scopeFrames().isEmpty())
            .filter(frontier -> frontier.scopeFrames().get(frontier.scopeFrames().size() - 1) instanceof ParallelForEachFrame frame
                    && frame.position() == index)
            .findFirst()
            .orElseThrow();
    }

    private static OccurrenceResult effectResult(FrontierSnapshot frontier, long sequence, String value) {
        return new OccurrenceResult(new OccurrenceKey(BoundaryKind.EFFECT, UUID.randomUUID()), frontier.frontierId(),
                new BoundaryCompletion.EffectSucceeded(sequence, "work", Map.of("slot", value)));
    }

    private CompiledMachineProgram compile(String xml) {
        return DurableCompilerTestSupport.compile(parse(xml), getClass().getClassLoader());
    }

    private TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("parallel-foreach", xml.getBytes(StandardCharsets.UTF_8)));
    }

    private DurableExecutionContext context(CompiledMachineProgram compiled) {
        return new DurableExecutionContext(compiled.machinePlan(),
                new DurableActionInvoker(ProcessComponentResolver.disabled(), ScriptExecutorRegistry.from(List.of()),
                        getClass().getClassLoader()), DurableWaitDescriptionProvider.defaults(), serializer(compiled));
    }

    private DurableValueSerializer serializer(CompiledMachineProgram compiled) {
        return new DurableValueSerializer(compiled.machinePlan());
    }

    private String parallelEffectFlow() {
        return """
            <bpm code="parallel.foreach.effects">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="loop"/></start>
              <foreach id="loop" execution="parallel"
                   collection="items" item="item" itemType="java.lang.String"
                   index="itemIndex" g="40,0,220,120">
                <output target="results" source="slot"/>
                <transition to="end"/>
                <start id="loopStart"><transition to="work"/></start>
                <autoTask id="work" g="70,20,100,40">
                  <action type="java" execution="effect" class="java.lang.String" method="valueOf">
                      <input target="item" dataType="java.lang.String" source="item"/>
                      <output dataType="java.lang.String" target="slot"/>

                  </action>
                  <transition to="loopEnd"/>
                </autoTask>
                <end id="loopEnd"/>
              </foreach>
              <end id="end" g="300,0,32,32"/>
            </bpm>
            """;
    }

    private String parallelReplayableFlow() {
        return """
            <bpm code="parallel.foreach.replayable">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="loop"/></start>
              <foreach id="loop" execution="parallel"
                   collection="items" item="item" itemType="java.lang.String"
                   index="itemIndex" g="40,0,220,120">
                <output target="results" source="slot"/>
                <transition to="end"/>
                <start id="loopStart"><transition to="work"/></start>
                <autoTask id="work" g="70,20,100,40">
                  <action type="java" execution="replayable" class="%s" method="identity">
                      <input target="item" dataType="java.lang.String" source="item"/>
                      <output dataType="java.lang.String" target="slot"/>

                  </action>
                  <transition to="loopEnd"/>
                </autoTask>
                <end id="loopEnd"/>
              </foreach>
              <end id="end" g="300,0,32,32"/>
            </bpm>
            """
            .formatted(TbbpmDifferentialActions.class.getName());
    }

    private String parallelContinueWithoutOutputFlow() {
        return """
            <bpm code="parallel.foreach.continue">
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

    private String parallelContinueWithOutputFlow() {
        return """
            <bpm code="parallel.foreach.continue.output">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" defaultValue="unassigned" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start"><transition to="loop"/></start>
              <foreach id="loop" execution="parallel" collection="items"
                   item="item" itemType="java.lang.String" index="index">
                <output target="results" source="slot"/>
                <transition to="end"/>
                <start id="loopStart"><transition to="skip"/></start>
                <continue id="skip" condition="index == 1"><transition to="work"/></continue>
                <autoTask id="work">
                  <action type="java" execution="replayable" class="%s" method="identity">
                      <input target="item" dataType="java.lang.String" source="item"/>
                      <output dataType="java.lang.String" target="slot"/>

                  </action>
                  <transition to="loopEnd"/>
                </autoTask>
                <end id="loopEnd"/>
              </foreach>
              <end id="end"/>
            </bpm>
            """
            .formatted(TbbpmDifferentialActions.class.getName());
    }

    private String sequentialParentParallelFlow() {
        return """
            <bpm code="parallel.foreach.sequential-parent">
              <var name="batches" dataType="java.util.List&lt;java.util.List&lt;java.lang.String&gt;&gt;" inOutType="param"/>
              <var name="innerSlot" dataType="java.lang.String" inOutType="inner"/>
              <var name="innerResults" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="inner"/>
              <var name="outerResults" dataType="java.util.List&lt;java.util.List&lt;java.lang.String&gt;&gt;" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="outer"/></start>
              <foreach id="outer"
                   collection="batches" item="batch" itemType="java.util.List" g="40,0,300,180">
                <output target="outerResults" source="innerResults"/>
                <transition to="end"/>
                <start id="outerStart"><transition to="inner"/></start>
                <foreach id="inner" execution="parallel"
                     collection="batch" item="innerItem" itemType="java.lang.String" g="70,20,220,120">
                  <output target="innerResults" source="innerSlot"/>
                  <start id="innerStart"><transition to="work"/></start>
                  <autoTask id="work" g="100,40,100,40">
                    <action type="java" execution="replayable" class="%s" method="identity">
                        <input target="item" dataType="java.lang.String" source="innerItem"/>
                        <output dataType="java.lang.String" target="innerSlot"/>

                    </action>
                    <transition to="innerEnd"/>
                  </autoTask>
                  <end id="innerEnd"/>
                  <transition to="outerEnd"/>
                </foreach>
                <end id="outerEnd"/>
              </foreach>
              <end id="end" g="380,0,32,32"/>
            </bpm>
            """
            .formatted(TbbpmDifferentialActions.class.getName());
    }

    private String parallelParentParallelFlow() {
        return """
            <bpm code="parallel.foreach.parallel-parent">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="outerSlot" dataType="java.lang.String" inOutType="inner"/>
              <var name="outerResults" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <var name="innerSlot" dataType="java.lang.String" inOutType="inner"/>
              <var name="innerResults" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="inner"/>
              <start id="start" g="0,0,32,32"><transition to="outer"/></start>
              <foreach id="outer" execution="parallel"
                   collection="items" item="outerItem" itemType="java.lang.String" g="40,0,300,180">
                <output target="outerResults" source="outerSlot"/>
                <transition to="end"/>
                <start id="outerStart"><transition to="inner"/></start>
                <foreach id="inner" execution="parallel"
                     collection="items" item="innerItem" itemType="java.lang.String" g="70,20,220,120">
                  <output target="innerResults" source="innerSlot"/>
                  <start id="innerStart"><transition to="work"/></start>
                  <autoTask id="work" g="100,40,100,40">
                    <action type="java" execution="replayable" class="java.lang.String" method="valueOf"/>
                    <transition to="innerEnd"/>
                  </autoTask>
                  <end id="innerEnd"/>
                  <transition to="outerEnd"/>
                </foreach>
                <end id="outerEnd"/>
              </foreach>
              <end id="end" g="380,0,32,32"/>
            </bpm>
            """;
    }

    private String whileParentParallelFlow() {
        return """
            <bpm code="parallel.foreach.while-parent">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="outer"/></start>
              <while id="outer" condition="true" g="40,0,300,180" maxIterations="100">
                <transition to="end"/>
                <start id="outerStart"><transition to="inner"/></start>
                <foreach id="inner" execution="parallel"
                     collection="items" item="item" itemType="java.lang.String" g="70,20,220,120">
                  <output target="results" source="slot"/>
                  <transition to="stop"/>
                  <start id="innerStart"><transition to="work"/></start>
                  <autoTask id="work" g="100,40,100,40">
                    <action type="java" execution="replayable" class="%s" method="identity">
                        <input target="item" dataType="java.lang.String" source="item"/>
                        <output dataType="java.lang.String" target="slot"/>

                    </action>
                    <transition to="innerEnd"/>
                  </autoTask>
                  <end id="innerEnd"/>
                </foreach>
                <break id="stop" g="310,20,32,32"/>
                <end id="outerEnd"/>
              </while>
              <end id="end" g="380,0,32,32"/>
            </bpm>
            """
            .formatted(TbbpmDifferentialActions.class.getName());
    }

    private String gatewayConcurrentParallelFlow() {
        return """
            <bpm code="parallel.foreach.gateway-parent">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <parallel id="fork" g="40,0,40,40">
                <transition to="loop"/><transition to="sibling"/>
              </parallel>
              <foreach id="loop" execution="parallel"
                   collection="items" item="item" itemType="java.lang.String" g="100,0,220,120">
                <output target="results" source="slot"/>
                <transition to="join"/>
                <start id="loopStart"><transition to="work"/></start>
                <autoTask id="work" g="130,20,100,40">
                  <action type="java" execution="replayable" class="java.lang.String" method="valueOf"/>
                  <transition to="loopEnd"/>
                </autoTask>
                <end id="loopEnd"/>
              </foreach>
              <autoTask id="sibling" g="100,160,100,40">
                <action type="java" execution="replayable" class="java.lang.String" method="isEmpty"/>
                <transition to="join"/>
              </autoTask>
              <parallel id="join" g="360,0,40,40"><transition to="end"/></parallel>
              <end id="end" g="440,0,32,32"/>
            </bpm>
            """;
    }

    private String inclusiveGatewayConcurrentParallelFlow() {
        return gatewayConcurrentParallelFlow()
            .replace("parallel.foreach.gateway-parent", "parallel.foreach.inclusive-parent")
            .replace("<parallel id=", "<inclusive id=")
            .replace("<transition to=\"loop\"/>", "<transition to=\"loop\" condition=\"true\"/>")
            .replace("</parallel>", "</inclusive>");
    }

    private String parallelChildFlow() {
        return """
            <bpm code="parallel.foreach.child">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="loop"/></start>
              <foreach id="loop" execution="parallel"
                   collection="items" item="item" itemType="java.lang.String" g="40,0,220,120">
                <output target="results" source="slot"/>
                <transition to="end"/>
                <start id="loopStart"><transition to="child"/></start>
                <bpmCall id="child" code="child.flow" version="v1" g="70,20,100,40"><transition to="loopEnd"/></bpmCall>
                <end id="loopEnd"/>
              </foreach>
              <end id="end" g="300,0,32,32"/>
            </bpm>
            """;
    }
}
