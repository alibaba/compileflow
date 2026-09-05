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

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.VariablePlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.VariableRole;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.util.Objects;

/**
 * Validates one caller mapping against the exact called-Process contract.
 *
 * @author yusu
 */
public final class ProcessCallContract {
    private ProcessCallContract() {
    }

    public static void validate(String callSiteId, ProcessCallPlan call, ProcessSemanticPlan calledProcess,
            ClassLoader classLoader) {
        String nodeId = ProcessIdentifiers.requireNodeId(callSiteId);
        ProcessCallPlan mapping = Objects.requireNonNull(call, "call");
        ProcessSemanticPlan contract = Objects.requireNonNull(calledProcess, "calledProcess");
        ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
        if (!mapping.code().equals(contract.getProcessCode())) {
            throw invalid(nodeId,
                    "declared code '" + mapping.code() + "' does not match resolved code '" + contract.getProcessCode() + "'");
        }
        for (ProcessCallPlan.Input input : mapping.inputs()) {
            VariablePlan target = contract.getVariables().get(input.target());
            if (target == null) {
                throw invalid(nodeId, "input target '" + input.target() + "' is not declared by the called Process");
            }
            if (target.role() != VariableRole.PARAM) {
                throw invalid(nodeId, "input target '" + input.target() + "' is not a Process parameter");
            }
            if (input.defaultValue() != null) {
                try {
                    DataTypes.parseDefaultValue(DataTypes.getJavaClass(target.dataType(), loader), input.defaultValue());
                } catch (RuntimeException failure) {
                    throw invalid(nodeId,
                            "default for input target '" + input.target() + "' is not a valid " + target.dataType() + " literal",
                            failure);
                }
            }
        }
        for (ProcessCallPlan.Output output : mapping.outputs()) {
            VariablePlan source = contract.getVariables().get(output.source());
            if (source == null) {
                throw invalid(nodeId, "output source '" + output.source() + "' is not declared by the called Process");
            }
            if (source.role() != VariableRole.RETURN) {
                throw invalid(nodeId, "output source '" + output.source() + "' is not a Process return value");
            }
        }
    }

    private static IllegalArgumentException invalid(String callSiteId, String detail) {
        return new IllegalArgumentException("Invalid Process call '" + callSiteId + "': " + detail);
    }

    private static IllegalArgumentException invalid(String callSiteId, String detail, RuntimeException cause) {
        return new IllegalArgumentException("Invalid Process call '" + callSiteId + "': " + detail, cause);
    }
}
