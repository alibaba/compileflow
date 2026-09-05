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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessCallGraphTest {
    @Test
    void resolvesTheExactTargetByCallerAndCallId() {
        ProcessCallGraph.ProcessNode root = processNode("order@v7", "order", "v7");
        ProcessCallGraph.ProcessNode oldPayment = processNode("payment@v3", "payment", "v3");
        ProcessCallGraph.ProcessNode newPayment = processNode("payment@v4", "payment", "v4");
        ProcessCallGraph graph = new ProcessCallGraph(root,
                Map.of(new ProcessCallGraph.CallSite(root.id(), "oldPayment"), oldPayment,
                        new ProcessCallGraph.CallSite(root.id(), "newPayment"), newPayment));

        assertThat(graph.requireTarget(root, "oldPayment")).isSameAs(oldPayment);
        assertThat(graph.requireTarget(root, "newPayment")).isSameAs(newPayment);
    }

    private static ProcessCallGraph.ProcessNode processNode(String id, String code, String version) {
        return new ProcessCallGraph.ProcessNode(id, "shop", code, ProcessRef.version("shop", code, version),
                runtimeEntry(code, version));
    }

    private static ProcessRuntimeEntry runtimeEntry(String code, String version) {
        ProcessDefinitionSnapshot definition =
                ProcessDefinitionSnapshot.of("shop", code, version, "<process/>".getBytes(StandardCharsets.UTF_8),
                        "test");
        ProcessRuntimeIdentity identity = ProcessRuntimeIdentity.of(definition, ProcessModelType.TBBPM,
                ProcessRuntimeIdentity.newPipelineIdentity(), ProcessCallGraphTest.class.getClassLoader());
        return new ProcessRuntimeEntry(NoOpProcessRuntime.INSTANCE, identity);
    }
}
