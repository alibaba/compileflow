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

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.runtime.action.ProcessActionInvoker;
import java.util.Objects;

/**
 * Enforces the deliberately small realization boundary of the Process interpreter.
 *
 * @author yusu
 */
final class InterpretedProcessRuntimeEligibilityChecker {
    private InterpretedProcessRuntimeEligibilityChecker() {
    }

    static void validate(ProcessSemanticPlan semanticPlan, ProcessActionInvoker actionInvoker) {
        Objects.requireNonNull(semanticPlan, "semanticPlan");
        Objects.requireNonNull(actionInvoker, "actionInvoker");
        for (ProcessSemanticPlan.NodePlan node : semanticPlan.getNodes().values()) {
            if (node.operation() instanceof ActionPlan action) {
                validateAction(node.id(), action, actionInvoker);
            }
        }
    }

    private static void validateAction(String nodeId, ActionPlan action, ProcessActionInvoker actionInvoker) {
        if (!actionInvoker.isStructurallyReady(action)) {
            throw invalid(nodeId, "Action implementation is unavailable to the Process interpreter");
        }
    }

    private static CompileFlowException invalid(String nodeId, String detail) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_005,
                "Interpreted ProcessRuntime cannot execute node '" + nodeId + "': " + detail);
    }
}
