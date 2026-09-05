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

import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared inert runtime fixture for cache, compilation, and ownership tests.
 *
 * @author yusu
 */
public enum NoOpProcessRuntime implements ProcessRuntime {
    INSTANCE;
    private static final ProcessSemanticPlan PLAN = new ProcessSemanticPlan("test", Map.of(),
            new LinkedHashMap<>(Map.of("start",
                    new ProcessSemanticPlan.NodePlan("start", ProcessSemanticPlan.NodeKind.START,
                            ProcessSemanticPlan.ROOT_SCOPE_ID, null,
                            List.of(new ProcessSemanticPlan.TransitionPlan("end", null, false)), null, null, null),
                    "end",
                    new ProcessSemanticPlan.NodePlan("end", ProcessSemanticPlan.NodeKind.END,
                            ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null, null))));

    @Override
    public ProcessSemanticPlan getSemanticPlan() {
        return PLAN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> variables) {
        return Map.copyOf(variables);
    }

    @Override
    public Map<String, Object> trigger(ProcessTrigger trigger, Map<String, Object> variables) {
        return execute(variables);
    }
}
