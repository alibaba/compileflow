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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessCallOutputsTest {
    @Test
    void distinguishesExplicitNullFromMissingOutput() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", null);

        assertThat(ProcessCallOutputs.requireOutput(outputs, "child", "call", "result")).isNull();
    }

    @Test
    void reportsMissingOutputWithCallBoundaryContext() {
        assertThatThrownBy(() -> ProcessCallOutputs.requireOutput(Map.of(), "child", "call", "result"))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_008);
                assertThat(failure.getContext())
                    .containsEntry("processCode", "child")
                    .containsEntry("nodeId", "call")
                    .containsEntry("outputName", "result");
            });
    }
}
