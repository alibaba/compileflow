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
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryKind;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceKey;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceResult;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.runtime.machine.DurableProcessCompiler;
import com.alibaba.compileflow.durable.runtime.kernel.ResumeDescriptor;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.kernel.WhileFrame;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.engine.bpmn.semantic.BpmnSemanticFrontend;
import com.alibaba.compileflow.engine.bpmn.parser.BpmnXmlParser;
import com.alibaba.compileflow.engine.bpmn.validation.BpmnModelValidator;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModel;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptProgramCatalog;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.validation.ValidationFailure;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ReconcilePlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * End-to-end contract for the BPMN subset promoted to Durable execution.
 *
 * @author yusu
 */
class DurableBpmnProfileCoverageTest {
    private final ScriptExecutorRegistry scripts = ScriptExecutorRegistry.from(List.of(new TestScriptExecutor()));

    @Test
    void lowersServiceAndNativeScriptTasksThroughTheSameActionSemantics() {
        DurableMachinePlan service = compilePlan(singleActivity(javaServiceTask("replayable")));
        DurableMachinePlan script = compilePlan(
                singleActivity(
                        """
            <bpmn:scriptTask id="activity" scriptFormat="test-script">
              <bpmn:script>meaning + 1</bpmn:script>
            </bpmn:scriptTask>
            """));

        assertThat(service.requireStep("activity")).isInstanceOf(DurableMachinePlan.Step.Replayable.class);
        assertThat(script.requireStep("activity")).isInstanceOf(DurableMachinePlan.Step.Replayable.class);
        assertThat(((ActionPlan) script.semanticPlan().requireNode("activity").operation()).invocation())
            .isEqualTo(
                    new com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation.Script("test-script",
                            "meaning + 1"));
    }

    @Test
    void lowersExclusiveParallelAndInclusiveGateways() {
        DurableMachinePlan exclusive = compilePlan(exclusiveProcess());
        DurableMachinePlan parallel = compilePlan(concurrentProcess("parallelGateway"));
        DurableMachinePlan inclusive = compilePlan(concurrentProcess("inclusiveGateway"));

        DurableMachinePlan.Step.ChooseOne choice = (DurableMachinePlan.Step.ChooseOne) exclusive.requireStep("fork");
        assertThat(choice.branches()).singleElement();
        assertThat(choice.defaultTargetNodeId()).isEqualTo("right");
        assertThat(parallel.requireStep("fork")).isInstanceOf(DurableMachinePlan.Step.ForkAll.class);
        assertThat(inclusive.requireStep("fork")).isInstanceOf(DurableMachinePlan.Step.ForkSelected.class);
        assertThat(parallel.isConcurrentJoin("join")).isTrue();
        assertThat(inclusive.isConcurrentJoin("join")).isTrue();
    }

    @Test
    void lowersEmbeddedSubprocessExactChildAndReceiveBoundaries() {
        DurableMachinePlan subprocess = compilePlan(embeddedSubprocess());
        DurableMachinePlan child = compilePlan(
                singleActivity(
                        """
            <bpmn:callActivity id="activity" calledElement="child.process" cf:version="v1"/>
            """));
        DurableMachinePlan receive = compilePlan(receiveTask());

        assertThat(subprocess.requireStep("activity")).isInstanceOf(DurableMachinePlan.Step.Advance.class);
        assertThat(subprocess.semanticPlan().requireNode("activity").scopeBoundary()).isNotNull();
        assertThat(child.requireStep("activity")).isInstanceOf(DurableMachinePlan.Step.ProcessCall.class);
        assertThat(receive.requireStep("activity")).isInstanceOf(DurableMachinePlan.Step.Await.class);
    }

    @Test
    void lowersStandardLoopsBeforeAndAfterAndBothMultiInstanceModes() {
        DurableMachinePlan before = compilePlan(
                singleActivity(
                        loopedService(
                                """
            <bpmn:standardLoopCharacteristics testBefore="true">
              <bpmn:loopCondition xsi:type="bpmn:tFormalExpression" language="java">true</bpmn:loopCondition>
            </bpmn:standardLoopCharacteristics>
            """)));
        DurableMachinePlan after = compilePlan(
                singleActivity(
                        loopedService(
                                """
            <bpmn:standardLoopCharacteristics testBefore="false" loopMaximum="3">
              <bpmn:loopCondition xsi:type="bpmn:tFormalExpression" language="java">true</bpmn:loopCondition>
            </bpmn:standardLoopCharacteristics>
            """)));
        DurableMachinePlan sequential = compilePlan(multiInstanceProcess(true));
        DurableMachinePlan parallel = compilePlan(multiInstanceProcess(false));
        DurableMachinePlan parallelWithoutOutput =
                compilePlan(multiInstanceProcess(false).replace(" cf:target=\"results\" cf:source=\"result\"", ""));

        assertThat(((DurableMachinePlan.Iteration.While) before.requireIteration("activity")).timing())
            .isEqualTo(IterationPlan.ConditionTiming.BEFORE);
        assertThat(((DurableMachinePlan.Iteration.While) before.requireIteration("activity")).maxIterations()).isNull();
        assertThat(((DurableMachinePlan.Iteration.While) after.requireIteration("activity")).timing())
            .isEqualTo(IterationPlan.ConditionTiming.AFTER);
        assertThat(((DurableMachinePlan.Iteration.ForEach) sequential.requireIteration("activity")).execution())
            .isEqualTo(IterationPlan.Execution.SEQUENTIAL);
        assertThat(((DurableMachinePlan.Iteration.ForEach) parallel.requireIteration("activity")).execution())
            .isEqualTo(IterationPlan.Execution.PARALLEL);
        assertThat(((DurableMachinePlan.Iteration.ForEach) parallelWithoutOutput.requireIteration("activity")))
            .satisfies(loop -> {
                assertThat(loop.execution()).isEqualTo(IterationPlan.Execution.PARALLEL);
                assertThat(loop.outputTargetVariable()).isNull();
                assertThat(loop.outputSourceVariable()).isNull();
            });
        assertThat(parallel.requireResume(ResumePoint.beforeIterationBody("activity").key()).expectedFramePath())
            .singleElement()
            .extracting(ResumeDescriptor.FrameDescriptor::loopId)
            .isEqualTo("activity");
    }

    @Test
    void parallelMultiInstanceWithoutOutputRunsToCompletion() throws Exception {
        CompiledMachineProgram compiled =
                compileBpmn(multiInstanceProcess(false).replace(" cf:target=\"results\" cf:source=\"result\"", ""));
        ContinuationSnapshot continuation = ContinuationSnapshot.start(Map.of("items", List.of("A", "B", "C")));
        MachineTurnResult turn = null;
        for (int remainingTurns = 16; remainingTurns > 0; remainingTurns--) {
            turn = compiled.program().advance(continuation, List.of(), TurnBudget.defaults(), context(compiled));
            if (turn.outcome() instanceof FrontierStepResult.Completed) {
                break;
            }
            continuation = turn.continuation();
        }

        assertThat(turn).isNotNull();
        assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void lowersManualRetryAndReconcileEffects() {
        DurableMachinePlan manual = compilePlan(singleActivity(javaServiceTask("effect")));
        DurableMachinePlan retry = compilePlan(
                singleActivity(
                        effectTask(
                                """
            <cf:effectPolicy recovery="retry" maxAttempts="3" recoveryDelay="PT1S"/>
            """)));
        DurableMachinePlan reconcile = compilePlan(
                singleActivity(
                        effectTask(
                                """
            <cf:effectPolicy recovery="reconcile" maxAttempts="3" maxReconcileAttempts="2"
                             recoveryDelay="PT1S" maxRecoveryDuration="PT10M">
              <cf:reconcileAction type="java" class="java.lang.System" method="currentTimeMillis"/>
            </cf:effectPolicy>
            """)));

        assertEffect(manual, EffectRecovery.MANUAL);
        assertEffect(retry, EffectRecovery.RETRY);
        assertEffect(reconcile, EffectRecovery.RECONCILE);
        ReconcilePlan reconcileAction = ((ActionPlan) reconcile.semanticPlan().requireNode("activity").operation())
            .effectPolicy()
            .reconcileAction();
        assertThat(reconcileAction).isNotNull();
    }

    @Test
    void lowersIntermediateMessageAndTimerCatchEvents() {
        DurableMachinePlan message = compilePlan(
                catchEvent("""
            <bpmn:messageEventDefinition messageRef="message_order"/>
            """,
                        "<bpmn:message id=\"message_order\" name=\"order-received\"/>"));
        DurableMachinePlan timer = compilePlan(
                catchEvent("""
            <bpmn:timerEventDefinition>
              <bpmn:timeDuration>PT5M</bpmn:timeDuration>
            </bpmn:timerEventDefinition>
            """,
                        ""));

        assertThat(message.requireStep("activity")).isInstanceOf(DurableMachinePlan.Step.Await.class);
        assertThat(timer.requireStep("activity")).isInstanceOf(DurableMachinePlan.Step.Timer.class);
    }

    @Test
    void nestedSubprocessReceiveSuspendsAndRecoversFromItsSemanticCoordinate() throws Exception {
        CompiledMachineProgram compiled = compileBpmn(nestedReceiveProcess());
        DurableExecutionContext context = context(compiled);

        FrontierStepResult.Waiting waiting =
                (FrontierStepResult.Waiting) advanceStart(compiled.program(), Map.of(), context);

        assertThat(waiting.checkpoint().resumePoint()).isEqualTo(ResumePoint.afterElement("nested_wait"));
        assertThat(waiting.checkpoint().scopeFrames()).isEmpty();
        assertThat(
                advanceAfter(compiled.program(), waiting.checkpoint(), waiting.state(),
                        new BoundaryCompletion.WaitCompleted(1, "nested_wait", "order-received", Map.of()), context))
            .isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void nestedParallelReceiveBranchesRecoverIndependentlyAndJoin() throws Exception {
        CompiledMachineProgram compiled = compileBpmn(nestedParallelReceiveProcess());
        DurableExecutionContext context = context(compiled);
        MachineTurnResult turn =
                compiled
            .program()
            .advance(ContinuationSnapshot.start(Map.of()), List.of(), TurnBudget.defaults(), context);

        assertThat(turn.continuation().frontiers())
            .extracting(frontier -> frontier.resumePoint().elementId())
            .containsExactly("left_wait", "right_wait");

        turn = compiled.program().advance(turn.continuation(), List.of(), TurnBudget.defaults(), context);
        turn = compiled.program().advance(turn.continuation(), List.of(), TurnBudget.defaults(), context);
        assertThat(turn.continuation().hasRunnableFrontier(TurnBudget.defaults().maxActiveIterations())).isFalse();

        FrontierSnapshot right = frontierAt(turn.continuation(), "right_wait");
        turn = compiled
            .program()
            .advance(turn.continuation(), List.of(waitResult(right, 1, "right_wait", "right")), TurnBudget.defaults(),
                    context);
        FrontierSnapshot left = frontierAt(turn.continuation(), "left_wait");
        turn = compiled
            .program()
            .advance(turn.continuation(), List.of(waitResult(left, 2, "left_wait", "left")), TurnBudget.defaults(),
                    context);
        turn = compiled.program().advance(turn.continuation(), List.of(), TurnBudget.defaults(), context);

        assertThat(turn.outcome()).isInstanceOf(FrontierStepResult.Completed.class);
    }

    @Test
    void standardLoopDirectlyOnReceiveTaskRecoversEveryIteration() throws Exception {
        CompiledMachineProgram compiled = compileBpmn(loopedReceiveProcess());
        DurableExecutionContext context = context(compiled);
        FrontierStepResult.Waiting first =
                (FrontierStepResult.Waiting) advanceStart(compiled.program(), Map.of(), context);

        assertThat(first.checkpoint().resumePoint()).isEqualTo(ResumePoint.afterElement("activity"));
        assertThat(first.checkpoint().scopeFrames()).containsExactly(new WhileFrame("activity", 0));

        FrontierStepResult.Waiting second = (FrontierStepResult.Waiting) advanceAfter(compiled.program(),
                first.checkpoint(), first.state(),
                new BoundaryCompletion.WaitCompleted(1, "activity", "order-received", Map.of()), context);
        assertThat(second.checkpoint().scopeFrames()).containsExactly(new WhileFrame("activity", 1));

        assertThat(
                advanceAfter(compiled.program(), second.checkpoint(), second.state(),
                        new BoundaryCompletion.WaitCompleted(2, "activity", "order-received", Map.of()), context))
            .isInstanceOf(FrontierStepResult.Completed.class);
    }

    private DurableMachinePlan compilePlan(String xml) {
        BpmnModel model = BpmnXmlParser
            .getInstance()
            .parse(FlowSource.of("durable-profile.bpmn", xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(new BpmnModelValidator().validate(model)).extracting(ValidationFailure::message).isEmpty();
        DurableMachinePlan machine =
                new DurableProcessCompiler(scripts).lower(new BpmnSemanticFrontend().compile(model));
        assertThat(new DurableJavaProgramCompiler().compile(machine, getClass().getClassLoader())).isNotNull();
        assertThat(new DurableInterpretedProgramCompiler().compile(machine, getClass().getClassLoader())).isNotNull();
        return machine;
    }

    private CompiledMachineProgram compileBpmn(String xml) {
        DurableMachinePlan machine = compilePlan(xml);
        return new CompiledMachineProgram(new DurableJavaProgramCompiler()
                    .compile(machine, getClass().getClassLoader()), machine,
                ScriptProgramCatalog.compile(machine.semanticPlan(), scripts));
    }

    private DurableExecutionContext context(CompiledMachineProgram compiled) {
        return new DurableExecutionContext(compiled.machinePlan(), DurableActionInvoker.unavailable(),
                DurableWaitDescriptionProvider.defaults(), new DurableValueSerializer(compiled.machinePlan()));
    }

    private static FrontierSnapshot frontierAt(ContinuationSnapshot continuation, String elementId) {
        return continuation
            .frontiers()
            .stream()
            .filter(frontier -> elementId.equals(frontier.resumePoint().elementId()))
            .findFirst()
            .orElseThrow();
    }

    private static OccurrenceResult waitResult(FrontierSnapshot frontier, long identity, String boundaryId,
            String event) {
        return new OccurrenceResult(new OccurrenceKey(BoundaryKind.WAIT, new UUID(0L, identity)), frontier.frontierId(),
                new BoundaryCompletion.WaitCompleted(identity, boundaryId, event, Map.of()));
    }

    private static void assertEffect(DurableMachinePlan machine, EffectRecovery recovery) {
        assertThat(machine.requireStep("activity")).isInstanceOf(DurableMachinePlan.Step.Effect.class);
        ActionPlan action = (ActionPlan) machine.semanticPlan().requireNode("activity").operation();
        assertThat(action.effectPolicy().recovery()).isEqualTo(recovery);
    }

    private static String singleActivity(String activity) {
        return definitions("",
                """
            <bpmn:startEvent id="start"/>
            %s
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_activity" sourceRef="start" targetRef="activity"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="activity" targetRef="end"/>
            """
                    .formatted(activity));
    }

    private static String javaServiceTask(String execution) {
        return """
            <bpmn:serviceTask id="activity">
              <bpmn:extensionElements>
                <cf:action type="java" execution="%s" class="java.lang.System" method="currentTimeMillis"/>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
            """
            .formatted(execution);
    }

    private static String loopedService(String loop) {
        return """
            <bpmn:serviceTask id="activity">
              <bpmn:extensionElements>
                <cf:action type="java" execution="replayable" class="java.lang.System" method="currentTimeMillis"/>
              </bpmn:extensionElements>
              %s
            </bpmn:serviceTask>
            """
            .formatted(loop);
    }

    private static String effectTask(String policy) {
        return """
            <bpmn:serviceTask id="activity">
              <bpmn:extensionElements>
                <cf:action type="java" execution="effect" class="java.lang.System" method="getProperty">
                    <cf:input target="effectId" dataType="java.lang.String"
                            source="__cf_effect_id"/>

                  %s
                </cf:action>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
            """
            .formatted(policy);
    }

    private static String exclusiveProcess() {
        return definitions("",
                """
            <bpmn:extensionElements>
              <cf:var name="approved" dataType="java.lang.Boolean" inOutType="param"/>
            </bpmn:extensionElements>
            <bpmn:startEvent id="start"/>
            <bpmn:exclusiveGateway id="fork" default="to_right"/>
            <bpmn:scriptTask id="left" scriptFormat="test-script">
              <bpmn:script>1</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:scriptTask id="right" scriptFormat="test-script">
              <bpmn:script>2</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:exclusiveGateway id="join"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_fork" sourceRef="start" targetRef="fork"/>
            <bpmn:sequenceFlow id="to_left" sourceRef="fork" targetRef="left">
              <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"
                                        language="java">approved</bpmn:conditionExpression>
            </bpmn:sequenceFlow>
            <bpmn:sequenceFlow id="to_right" sourceRef="fork" targetRef="right"/>
            <bpmn:sequenceFlow id="left_join" sourceRef="left" targetRef="join"/>
            <bpmn:sequenceFlow id="right_join" sourceRef="right" targetRef="join"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="join" targetRef="end"/>
            """);
    }

    private static String concurrentProcess(String gateway) {
        String conditional = gateway.equals("inclusiveGateway")
                ? """
                    <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"
                                              language="java">leftEnabled</bpmn:conditionExpression>
                    """
                : "";
        String processExtension = gateway.equals("inclusiveGateway")
                ? """
                    <bpmn:extensionElements>
                      <cf:var name="leftEnabled" dataType="java.lang.Boolean" inOutType="param"/>
                    </bpmn:extensionElements>
                    """
                : "";
        String defaultAttribute = gateway.equals("inclusiveGateway") ? " default=\"to_right\"" : "";
        return definitions("",
                """
            %4$s
            <bpmn:startEvent id="start"/>
            <bpmn:%1$s id="fork"%2$s/>
            <bpmn:scriptTask id="left" scriptFormat="test-script">
              <bpmn:script>1</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:scriptTask id="right" scriptFormat="test-script">
              <bpmn:script>2</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:%1$s id="join"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_fork" sourceRef="start" targetRef="fork"/>
            <bpmn:sequenceFlow id="to_left" sourceRef="fork" targetRef="left">%3$s</bpmn:sequenceFlow>
            <bpmn:sequenceFlow id="to_right" sourceRef="fork" targetRef="right"/>
            <bpmn:sequenceFlow id="left_join" sourceRef="left" targetRef="join"/>
            <bpmn:sequenceFlow id="right_join" sourceRef="right" targetRef="join"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="join" targetRef="end"/>
            """
                    .formatted(gateway, defaultAttribute, conditional, processExtension));
    }

    private static String embeddedSubprocess() {
        return definitions("",
                """
            <bpmn:startEvent id="start"/>
            <bpmn:subProcess id="activity">
              <bpmn:startEvent id="nested_start"/>
              <bpmn:scriptTask id="nested_task" scriptFormat="test-script">
                <bpmn:script>1</bpmn:script>
              </bpmn:scriptTask>
              <bpmn:endEvent id="nested_end"/>
              <bpmn:sequenceFlow id="nested_to_task" sourceRef="nested_start" targetRef="nested_task"/>
              <bpmn:sequenceFlow id="nested_to_end" sourceRef="nested_task" targetRef="nested_end"/>
            </bpmn:subProcess>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_subprocess" sourceRef="start" targetRef="activity"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="activity" targetRef="end"/>
            """);
    }

    private static String receiveTask() {
        return definitions("<bpmn:message id=\"message_order\" name=\"order-received\"/>",
                """
            <bpmn:startEvent id="start"/>
            <bpmn:receiveTask id="activity" messageRef="message_order"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_receive" sourceRef="start" targetRef="activity"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="activity" targetRef="end"/>
            """);
    }

    private static String nestedReceiveProcess() {
        return definitions("<bpmn:message id=\"message_order\" name=\"order-received\"/>",
                """
            <bpmn:startEvent id="start"/>
            <bpmn:subProcess id="subprocess">
              <bpmn:startEvent id="nested_start"/>
              <bpmn:receiveTask id="nested_wait" messageRef="message_order"/>
              <bpmn:endEvent id="nested_end"/>
              <bpmn:sequenceFlow id="nested_to_wait" sourceRef="nested_start" targetRef="nested_wait"/>
              <bpmn:sequenceFlow id="nested_to_end" sourceRef="nested_wait" targetRef="nested_end"/>
            </bpmn:subProcess>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_subprocess" sourceRef="start" targetRef="subprocess"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="subprocess" targetRef="end"/>
            """);
    }

    private static String nestedParallelReceiveProcess() {
        return definitions("""
            <bpmn:message id="message_left" name="left"/>
            <bpmn:message id="message_right" name="right"/>
            """,
                """
            <bpmn:startEvent id="start"/>
            <bpmn:subProcess id="subprocess">
              <bpmn:startEvent id="nested_start"/>
              <bpmn:parallelGateway id="nested_fork"/>
              <bpmn:receiveTask id="left_wait" messageRef="message_left"/>
              <bpmn:receiveTask id="right_wait" messageRef="message_right"/>
              <bpmn:parallelGateway id="nested_join"/>
              <bpmn:endEvent id="nested_end"/>
              <bpmn:sequenceFlow id="nested_to_fork" sourceRef="nested_start" targetRef="nested_fork"/>
              <bpmn:sequenceFlow id="fork_to_left" sourceRef="nested_fork" targetRef="left_wait"/>
              <bpmn:sequenceFlow id="fork_to_right" sourceRef="nested_fork" targetRef="right_wait"/>
              <bpmn:sequenceFlow id="left_to_join" sourceRef="left_wait" targetRef="nested_join"/>
              <bpmn:sequenceFlow id="right_to_join" sourceRef="right_wait" targetRef="nested_join"/>
              <bpmn:sequenceFlow id="join_to_end" sourceRef="nested_join" targetRef="nested_end"/>
            </bpmn:subProcess>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_subprocess" sourceRef="start" targetRef="subprocess"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="subprocess" targetRef="end"/>
            """);
    }

    private static String loopedReceiveProcess() {
        return definitions("<bpmn:message id=\"message_order\" name=\"order-received\"/>",
                """
            <bpmn:startEvent id="start"/>
            <bpmn:receiveTask id="activity" messageRef="message_order">
              <bpmn:standardLoopCharacteristics testBefore="false" loopMaximum="2"/>
            </bpmn:receiveTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_receive" sourceRef="start" targetRef="activity"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="activity" targetRef="end"/>
            """);
    }

    private static String multiInstanceProcess(boolean sequential) {
        String aggregation = sequential ? "" : " cf:target=\"results\" cf:source=\"result\"";
        return definitions("",
                """
            <bpmn:extensionElements>
              <cf:var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <cf:var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <cf:var name="result" dataType="java.lang.String" inOutType="inner"/>
            </bpmn:extensionElements>
            <bpmn:startEvent id="start"/>
            <bpmn:serviceTask id="activity">
              <bpmn:extensionElements>
                <cf:action type="java" execution="replayable" class="com.alibaba.compileflow.durable.runtime.program.TbbpmDifferentialActions"
                                   method="identity">
                    <cf:input target="item" dataType="java.lang.String" source="item"/>

                </cf:action>
              </bpmn:extensionElements>
              <bpmn:multiInstanceLoopCharacteristics isSequential="%s" cf:collection="items"
                  cf:item="item" cf:itemType="java.lang.String" cf:index="index"%s/>
            </bpmn:serviceTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_activity" sourceRef="start" targetRef="activity"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="activity" targetRef="end"/>
            """
                    .formatted(sequential, aggregation));
    }

    private static String catchEvent(String definition, String definitionsBody) {
        return definitions(definitionsBody,
                """
            <bpmn:startEvent id="start"/>
            <bpmn:intermediateCatchEvent id="activity">
              %s
            </bpmn:intermediateCatchEvent>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_catch" sourceRef="start" targetRef="activity"/>
            <bpmn:sequenceFlow id="to_end" sourceRef="activity" targetRef="end"/>
            """
                    .formatted(definition));
    }

    private static String definitions(String definitionsBody, String processBody) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:cf="http://www.compileflow.org"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              targetNamespace="urn:compileflow:durable-profile">
              %s
              <bpmn:process id="durable.profile" isExecutable="true">
                %s
              </bpmn:process>
            </bpmn:definitions>
            """
            .formatted(definitionsBody, processBody);
    }

    private static final class TestScriptExecutor implements ScriptExecutor {
        @Override
        public String name() {
            return "test-script";
        }

        @Override
        public void validate(ScriptProgramSpec spec) {
            assertThat(spec.source()).isNotBlank();
        }

        @Override
        public ScriptProgram compile(ScriptProgramSpec spec) {
            return new TestScriptProgram(spec.source());
        }

        @Override
        public Object evaluate(ScriptProgram script, Map<String, Object> context) {
            return null;
        }
    }

    private record TestScriptProgram(String source) implements ScriptProgram {
        @Override
        public String language() {
            return "test-script";
        }
    }
}
