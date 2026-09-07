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
package com.alibaba.compileflow.engine.bpmn.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModel;
import com.alibaba.compileflow.engine.bpmn.model.EndEvent;
import com.alibaba.compileflow.engine.bpmn.model.ExclusiveGateway;
import com.alibaba.compileflow.engine.bpmn.model.IntermediateCatchEvent;
import com.alibaba.compileflow.engine.bpmn.model.Message;
import com.alibaba.compileflow.engine.bpmn.model.MessageEventDefinition;
import com.alibaba.compileflow.engine.bpmn.model.Process;
import com.alibaba.compileflow.engine.bpmn.model.ScriptTask;
import com.alibaba.compileflow.engine.bpmn.model.SequenceFlow;
import com.alibaba.compileflow.engine.bpmn.model.StartEvent;
import com.alibaba.compileflow.engine.bpmn.model.SubProcess;
import com.alibaba.compileflow.engine.bpmn.model.TimerEventDefinition;
import com.alibaba.compileflow.engine.bpmn.model.TimerValue;
import com.alibaba.compileflow.engine.core.model.ProcessVariableContainer;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class BpmnSemanticFrontendTest {
    @Test
    void normalizesScriptTasksAndDefaultSequenceFlows() {
        Process process = new Process();
        process.setId("semantic.bpmn");
        process.addVariable(variable("value"));
        BpmnModel model = new BpmnModel(process);
        StartEvent start = node(new StartEvent(), "start");
        ExclusiveGateway gateway = node(new ExclusiveGateway(), "gateway");
        EndEvent preferred = node(new EndEvent(), "preferred");
        EndEvent fallback = node(new EndEvent(), "fallback");
        ScriptTask script = node(new ScriptTask(), "script");
        script.setScriptFormat("groovy");
        script.setScript("return value > 0");
        process.addNode(start);
        process.addNode(gateway);
        process.addNode(preferred);
        process.addNode(fallback);
        process.addNode(script);
        start.addOutgoingTransition(transition("start-flow", "start", "gateway", null));
        gateway.addOutgoingTransition(transition("preferred-flow", "gateway", "preferred", "value > 0"));
        gateway.addOutgoingTransition(transition("fallback-flow", "gateway", "fallback", null));
        gateway.setDefaultFlowId("fallback-flow");

        ProcessSemanticPlan plan = new BpmnSemanticFrontend().compile(model);

        assertThat(plan.getNodes().get("gateway").outgoingTransitions())
            .extracting(ProcessSemanticPlan.TransitionPlan::defaultFlow)
            .containsExactly(false, true);
        assertThat(plan.getNodes().get("script").operation()).isInstanceOf(ActionPlan.class);
        ActionPlan scriptPlan = (ActionPlan) plan.getNodes().get("script").operation();
        assertThat(scriptPlan.invocation()).isEqualTo(new ActionInvocation.Script("groovy", "return value > 0"));
        assertThat(scriptPlan.execution()).isEqualTo(ActionExecution.REPLAYABLE);
    }

    @Test
    void retainsSubProcessEntryAndExitAsOwnedScopeSemantics() {
        Process process = new Process();
        process.setId("semantic.bpmn.subprocess");
        BpmnModel model = new BpmnModel(process);
        SubProcess subProcess = node(new SubProcess(), "sub");
        StartEvent childStart = node(new StartEvent(), "child-start");
        EndEvent childEnd = node(new EndEvent(), "child-end");
        childStart.addOutgoingTransition(transition("child-flow", "child-start", "child-end", null));
        subProcess.addNode(childStart);
        subProcess.addNode(childEnd);
        process.addNode(subProcess);

        ProcessSemanticPlan plan = new BpmnSemanticFrontend().compile(model);

        assertThat(plan.requireNode("sub").scopeBoundary())
            .isEqualTo(new ProcessSemanticPlan.ScopeBoundary("child-start", "child-end"));
        assertThat(plan.nodesInScope("sub"))
            .extracting(ProcessSemanticPlan.NodePlan::id)
            .containsExactly("child-start", "child-end");
    }

    @Test
    void normalizesMessageCatchEventToAwaitSemantics() {
        Message message = new Message();
        message.setId("message-order");
        message.setName("order-received");
        MessageEventDefinition definition = new MessageEventDefinition();
        definition.setMessageRef(message.getId());
        IntermediateCatchEvent event = node(new IntermediateCatchEvent(), "catch");
        event.setMessageEventDefinition(definition);

        ProcessSemanticPlan plan = new BpmnSemanticFrontend().compile(model(event, message));

        assertThat(plan.requireNode("catch").operation()).isEqualTo(new AwaitPlan("order-received", null));
    }

    @Test
    void normalizesSupportedTimerCatchEventValues() {
        assertThat(timerOperation(new TimerValue(TimerValue.Kind.DURATION, "PT5M", false)))
            .isEqualTo(new TimerPlan(TimerPlan.Kind.DURATION_LITERAL, "PT5M"));
        assertThat(timerOperation(new TimerValue(TimerValue.Kind.DURATION, "delay", true)))
            .isEqualTo(new TimerPlan(TimerPlan.Kind.DURATION_EXPRESSION, "delay"));
        assertThat(timerOperation(new TimerValue(TimerValue.Kind.DATE, "2026-08-19T00:00:00Z", false)))
            .isEqualTo(new TimerPlan(TimerPlan.Kind.WAKE_AT_LITERAL, "2026-08-19T00:00:00Z"));
        assertThat(timerOperation(new TimerValue(TimerValue.Kind.DATE, "wakeAt", true)))
            .isEqualTo(new TimerPlan(TimerPlan.Kind.WAKE_AT_EXPRESSION, "wakeAt"));
    }

    @Test
    void rejectsUnsupportedTimerCatchEventValuesExplicitly() {
        assertThatThrownBy(() -> timerOperation(new TimerValue(TimerValue.Kind.CYCLE, "R/PT5M", false)))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_VALIDATION_006);
                assertThat(failure.getContext()).containsEntry("semantic", "timeCycle").containsEntry("nodeId", "timer");
            });
    }

    private static TimerPlan timerOperation(TimerValue value) {
        TimerEventDefinition definition = new TimerEventDefinition();
        definition.setValue(value);
        IntermediateCatchEvent event = node(new IntermediateCatchEvent(), "timer");
        event.setTimerEventDefinition(definition);
        return (TimerPlan) new BpmnSemanticFrontend().compile(model(event)).requireNode("timer").operation();
    }

    private static BpmnModel model(com.alibaba.compileflow.engine.bpmn.model.FlowNode flowNode, Message... messages) {
        Process process = new Process();
        process.setId("semantic.bpmn.event");
        process.addNode(flowNode);
        BpmnModel model = new BpmnModel(process);
        model.setMessages(List.of(messages));
        return model;
    }

    private static SequenceFlow transition(String id, String source, String target, String condition) {
        SequenceFlow transition = new SequenceFlow();
        transition.setId(id);
        transition.setSource(source);
        transition.setTarget(target);
        transition.setCondition(condition);
        return transition;
    }

    private static Variable variable(String name) {
        Variable variable = new Variable();
        variable.setName(name);
        variable.setDataType(Integer.class.getName());
        variable.setInOutType(ProcessVariableContainer.VARIABLE_TYPE_INNER);
        return variable;
    }

    private static <T extends com.alibaba.compileflow.engine.bpmn.model.FlowNode> T node(T node, String id) {
        node.setId(id);
        return node;
    }
}
