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
import java.util.Objects;

/**
 * Runtime validation helpers for named trigger entries.
 *
 * @author yusu
 */
public final class TriggerValidation {
    private TriggerValidation() {
    }

    public static void requireEvent(String nodeId, String expectedEvent, String actualEvent) {
        if (Objects.equals(expectedEvent, actualEvent)) {
            return;
        }
        CompileFlowException failure =
                new CompileFlowException(ErrorCode.CF_EXEC_008, "Unexpected event at trigger entry '" + nodeId + "'");
        failure.withContext("nodeId", nodeId);
        failure.withContext("expectedEvent", expectedEvent);
        if (actualEvent != null) {
            failure.withContext("actualEvent", actualEvent);
        }
        throw failure;
    }

    public static CompileFlowException unknownNodeId(String nodeId) {
        CompileFlowException failure =
                new CompileFlowException(ErrorCode.CF_EXEC_008, "Unknown trigger entry id '" + nodeId + "'");
        if (nodeId != null) {
            failure.withContext("nodeId", nodeId);
        }
        return failure;
    }
}
