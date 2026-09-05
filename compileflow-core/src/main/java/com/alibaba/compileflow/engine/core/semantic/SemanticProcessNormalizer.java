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

import com.alibaba.compileflow.engine.core.model.ProcessCallModel;
import com.alibaba.compileflow.engine.core.model.ProcessVariableContainer;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared normalization of source-neutral Process variables and calls.
 *
 * @author yusu
 */
public final class SemanticProcessNormalizer {
    private SemanticProcessNormalizer() {
    }

    public static Map<String, ProcessSemanticPlan.VariablePlan> variables(List<Variable> source) {
        Map<String, ProcessSemanticPlan.VariablePlan> result = new LinkedHashMap<>();
        for (Variable variable : source) {
            ProcessSemanticPlan.VariableRole role = switch (variable.getInOutType()) {
                case ProcessVariableContainer.VARIABLE_TYPE_PARAM -> ProcessSemanticPlan.VariableRole.PARAM;
                case ProcessVariableContainer.VARIABLE_TYPE_INNER -> ProcessSemanticPlan.VariableRole.INNER;
                case ProcessVariableContainer.VARIABLE_TYPE_RETURN -> ProcessSemanticPlan.VariableRole.RETURN;
                default -> throw new IllegalArgumentException(
                        "Unsupported Process variable role '" + variable.getInOutType() + "' for '" + variable.getName() + "'");
            };
            ProcessSemanticPlan.VariablePlan plan = new ProcessSemanticPlan.VariablePlan(variable.getName(),
                    variable.getDataType(), role, variable.getDefaultValue());
            if (result.putIfAbsent(plan.name(), plan) != null) {
                throw new IllegalArgumentException("Duplicate Process variable '" + plan.name() + "'");
            }
        }
        return result;
    }

    public static ProcessCallPlan processCall(ProcessCallModel call,
            Map<String, ProcessSemanticPlan.VariablePlan> variables) {
        List<ProcessCallPlan.Input> inputs = call
            .getInputMappings()
            .stream()
            .map(mapping -> new ProcessCallPlan.Input(mapping.getSource(), mapping.getTarget(),
                    mapping.getDefaultValue()))
            .toList();
        List<ProcessCallPlan.Output> outputs = call
            .getOutputMappings()
            .stream()
            .map(mapping -> new ProcessCallPlan.Output(mapping.getSource(), mapping.getTarget(),
                    targetType(mapping, variables)))
            .toList();
        ProcessCallTarget target =
                ProcessCallTarget.from(call.getCalledProcessClasspath(), call.getCalledProcessVersion());
        return new ProcessCallPlan(call.getCalledProcessCode(), target, inputs, outputs);
    }

    private static String targetType(OutputMapping mapping, Map<String, ProcessSemanticPlan.VariablePlan> variables) {
        ProcessSemanticPlan.VariablePlan target = variables.get(mapping.getTarget());
        if (target == null) {
            throw new IllegalArgumentException(
                    "Child output '" + mapping.getSource() + "' references unknown Process variable '" + mapping.getTarget() + "'");
        }
        return target.dataType();
    }
}
