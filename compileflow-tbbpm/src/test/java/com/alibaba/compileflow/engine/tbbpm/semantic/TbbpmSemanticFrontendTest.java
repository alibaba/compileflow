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
package com.alibaba.compileflow.engine.tbbpm.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.core.model.ProcessVariableContainer;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.tbbpm.model.AutoTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.EndNode;
import com.alibaba.compileflow.engine.tbbpm.model.StartNode;
import com.alibaba.compileflow.engine.tbbpm.model.SubBpmNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.model.Transition;
import java.util.List;
import org.junit.jupiter.api.Test;

class TbbpmSemanticFrontendTest {
    @Test
    void normalizesTbbpmActionAndDeclarationOrderedTransitions() {
        TbbpmModel model = new TbbpmModel();
        model.setCode("semantic.tbbpm");
        model.setVars(List.of(variable("value")));
        StartNode start = node(new StartNode(), "start");
        AutoTaskNode task = node(new AutoTaskNode(), "task");
        EndNode high = node(new EndNode(), "high");
        EndNode low = node(new EndNode(), "low");
        task.setAction(javaAction());
        start.addOutgoingTransition(transition("start", "task", null));
        task.addOutgoingTransition(transition("task", "low", "value == 0"));
        task.addOutgoingTransition(transition("task", "high", "value > 0"));
        model.setAllNodes(List.of(start, task, high, low));

        ProcessSemanticPlan plan = new TbbpmSemanticFrontend().compile(model);

        assertThat(plan.getNodes().get("task").outgoingTransitions())
            .extracting(ProcessSemanticPlan.TransitionPlan::targetId)
            .containsExactly("low", "high");
        assertThat(plan.getNodes().get("task").operation()).isInstanceOf(ActionPlan.class);
        ActionPlan action = (ActionPlan) plan.getNodes().get("task").operation();
        assertThat(action.invocation()).isEqualTo(new ActionInvocation.Java(Worker.class.getName(), "execute"));
        assertThat(plan.getVariables()).containsKey("value");
    }

    @Test
    void lowersEmbeddedSubBpmToAPlainSemanticScope() {
        TbbpmModel model = new TbbpmModel();
        model.setCode("semantic.embedded");
        StartNode rootStart = node(new StartNode(), "rootStart");
        SubBpmNode subBpm = node(new SubBpmNode(), "validation");
        StartNode childStart = node(new StartNode(), "validationStart");
        EndNode childEnd = node(new EndNode(), "validationEnd");
        EndNode rootEnd = node(new EndNode(), "rootEnd");
        subBpm.addNode(childStart);
        subBpm.addNode(childEnd);
        model.setAllNodes(List.of(rootStart, subBpm, rootEnd));

        ProcessSemanticPlan plan = new TbbpmSemanticFrontend().compile(model);

        assertThat(plan.requireNode("validation").scopeBoundary())
            .isEqualTo(new ProcessSemanticPlan.ScopeBoundary("validationStart", "validationEnd"));
        assertThat(plan.requireNode("validation").operation()).isNull();
        assertThat(plan.requireNode("validationStart").scopeId()).isEqualTo("validation");
        assertThat(plan.requireNode("validationEnd").scopeId()).isEqualTo("validation");
    }

    private static Action javaAction() {
        Action action = new Action();
        action.setType(ActionType.JAVA);
        action.setClassName(Worker.class.getName());
        action.setMethod("execute");
        return action;
    }

    private static Transition transition(String source, String target, String expression) {
        Transition transition = new Transition();
        transition.setSource(source);
        transition.setTarget(target);
        transition.setCondition(expression);
        return transition;
    }

    private static Variable variable(String name) {
        Variable variable = new Variable();
        variable.setName(name);
        variable.setDataType(Integer.class.getName());
        variable.setInOutType(ProcessVariableContainer.VARIABLE_TYPE_INNER);
        return variable;
    }

    private static <T extends com.alibaba.compileflow.engine.tbbpm.model.FlowNode> T node(T node, String id) {
        node.setId(id);
        return node;
    }

    public static final class Worker {
        public static void execute() {}
    }
}
