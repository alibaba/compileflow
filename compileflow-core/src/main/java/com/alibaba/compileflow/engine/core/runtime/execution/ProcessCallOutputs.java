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
package com.alibaba.compileflow.engine.core.runtime.execution;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.util.Map;
import java.util.Objects;

/**
 * Enforces synchronous called-process result boundaries.
 *
 * @author yusu
 */
public final class ProcessCallOutputs {
    private ProcessCallOutputs() {
    }

    /**
     * Returns a declared child output while preserving explicit {@code null}.
     *
     * @param outputs     child-process result map
     * @param processCode called process code
     * @param nodeId      call node identifier
     * @param outputName  expected child output name
     * @return mapped value, including an explicitly returned {@code null}
     */
    public static Object requireOutput(Map<String, Object> outputs, String processCode, String nodeId,
            String outputName) {
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(processCode, "processCode");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(outputName, "outputName");
        if (outputs.containsKey(outputName)) {
            return outputs.get(outputName);
        }

        CompileFlowException failure = new CompileFlowException(ErrorCode.CF_EXEC_008,
                "Called process '" + processCode + "' did not return mapped output '" + outputName + "' at node '" + nodeId + "'");
        failure.withContext("processCode", processCode);
        failure.withContext("nodeId", nodeId);
        failure.withContext("outputName", outputName);
        throw failure;
    }
}
