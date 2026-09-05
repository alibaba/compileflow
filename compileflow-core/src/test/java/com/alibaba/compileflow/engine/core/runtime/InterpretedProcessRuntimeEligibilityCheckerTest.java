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
package com.alibaba.compileflow.engine.core.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.runtime.action.ProcessActionInvoker;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterpretedProcessRuntimeEligibilityCheckerTest {
    @Test
    void rejectsUnavailableScriptProviders() {
        ActionPlan action = new ActionPlan(null, new ActionInvocation.Script("missing", "return input + 1;"),
                List.of(ActionPlan.Input.expression("input", "input", Integer.class.getName())),
                new ActionPlan.Output("output", Integer.class.getName(), Integer.class.getName()),
                EffectiveInvocationPolicy.defaults(), null);
        ProcessSemanticPlan semantics = new ProcessSemanticPlan("interpreted.eligibility",
                Map.of("input",
                        new ProcessSemanticPlan.VariablePlan("input", Integer.class.getName(),
                                ProcessSemanticPlan.VariableRole.PARAM, null), "output",
                        new ProcessSemanticPlan.VariablePlan("output", Integer.class.getName(),
                                ProcessSemanticPlan.VariableRole.RETURN, null)),
                Map.of("start", node("start", ProcessSemanticPlan.NodeKind.START, "task", null), "task",
                        node("task", ProcessSemanticPlan.NodeKind.ACTIVITY, "end", action), "end",
                        node("end", ProcessSemanticPlan.NodeKind.END, null, null)));
        ProcessActionInvoker invoker = new ProcessActionInvoker(ProcessComponentResolver.disabled(),
                ScriptExecutorRegistry.from(List.of()), getClass().getClassLoader());

        assertThatThrownBy(() -> InterpretedProcessRuntimeEligibilityChecker.validate(semantics, invoker))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Action implementation is unavailable");
    }

    private static ProcessSemanticPlan.NodePlan node(String id, ProcessSemanticPlan.NodeKind kind, String target,
            ActionPlan action) {
        return new ProcessSemanticPlan.NodePlan(id, kind, ProcessSemanticPlan.ROOT_SCOPE_ID, null,
                target == null ? List.of() : List.of(new ProcessSemanticPlan.TransitionPlan(target, null, false)), null,
                action, null);
    }
}
