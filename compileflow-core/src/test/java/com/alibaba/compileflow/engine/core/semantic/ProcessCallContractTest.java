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
package com.alibaba.compileflow.engine.core.semantic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.VariablePlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.VariableRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessCallContractTest {
    private static final ClassLoader CLASS_LOADER = ProcessCallContractTest.class.getClassLoader();

    @Test
    void acceptsPartialMappingsAndUsesTheCalledProcessTypeForDefaults() {
        ProcessCallPlan call = call(List.of(new ProcessCallPlan.Input(null, "count", "42")),
                List.of(new ProcessCallPlan.Output("result", "parentResult", Long.class.getName())));

        assertThatCode(() -> ProcessCallContract.validate("call", call,
                contract(variable("count", Integer.class, VariableRole.PARAM),
                        variable("optional", String.class, VariableRole.PARAM),
                        variable("result", Integer.class, VariableRole.RETURN)), CLASS_LOADER))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownAndWrongDirectionBindings() {
        ProcessSemanticPlan child = contract(variable("state", String.class, VariableRole.INNER),
                variable("parameter", String.class, VariableRole.PARAM));

        assertThatThrownBy(() -> ProcessCallContract.validate("call",
                call(List.of(new ProcessCallPlan.Input("value", "missing", null)), List.of()), child, CLASS_LOADER))
            .hasMessageContaining("input target 'missing' is not declared");
        assertThatThrownBy(() -> ProcessCallContract.validate("call",
                call(List.of(new ProcessCallPlan.Input("value", "state", null)), List.of()), child, CLASS_LOADER))
            .hasMessageContaining("input target 'state' is not a Process parameter");
        assertThatThrownBy(() -> ProcessCallContract.validate("call",
                call(List.of(), List.of(new ProcessCallPlan.Output("parameter", "result", String.class.getName()))),
                child, CLASS_LOADER))
            .hasMessageContaining("output source 'parameter' is not a Process return value");
    }

    @Test
    void rejectsInvalidCalledProcessIdentityAndDefaultLiteral() {
        ProcessSemanticPlan child = contract(variable("count", Integer.class, VariableRole.PARAM));

        assertThatThrownBy(() -> ProcessCallContract.validate("call",
                new ProcessCallPlan("other", new ProcessCallTarget.Classpath("child.bpm"), List.of(), List.of()), child,
                CLASS_LOADER))
            .hasMessageContaining("does not match resolved code");
        assertThatThrownBy(() -> ProcessCallContract.validate("call",
                call(List.of(new ProcessCallPlan.Input(null, "count", "not-an-integer")), List.of()), child,
                CLASS_LOADER))
            .hasMessageContaining("not a valid java.lang.Integer literal");
    }

    private static ProcessCallPlan call(List<ProcessCallPlan.Input> inputs, List<ProcessCallPlan.Output> outputs) {
        return new ProcessCallPlan("child", new ProcessCallTarget.Classpath("child.bpm"), inputs, outputs);
    }

    private static ProcessSemanticPlan contract(VariablePlan... variables) {
        Map<String, VariablePlan> contract = new LinkedHashMap<>();
        for (VariablePlan variable : variables) {
            contract.put(variable.name(), variable);
        }
        return new ProcessSemanticPlan("child", contract, Map.of());
    }

    private static VariablePlan variable(String name, Class<?> type, VariableRole role) {
        return new VariablePlan(name, type.getName(), role, null);
    }
}
