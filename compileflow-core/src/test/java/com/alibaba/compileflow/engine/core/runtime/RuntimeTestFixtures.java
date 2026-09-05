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

import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.runtime.cache.RuntimeCacheKeys;
import com.alibaba.compileflow.engine.core.runtime.context.ProcessCallInvoker;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Test-only factories for exact local runtime identities.
 *
 * @author yusu
 */
public final class RuntimeTestFixtures {
    private static final ProcessRuntimeIdentity.PipelineIdentity DEFAULT_PIPELINE_IDENTITY =
            ProcessRuntimeIdentity.newPipelineIdentity();
    private static final ProcessCallInvoker REJECTING_PROCESS_CALL_INVOKER =
            (graph, target, variables) -> {
        throw new AssertionError("Unexpected Process call");
    };

    private RuntimeTestFixtures() {
    }

    public static ProcessRuntimeRequest inline(String code, String content) {
        return ProcessRuntimeRequest.from(ProcessDefinition.inline(code, content));
    }

    public static ProcessRuntimeRequest versioned(String namespace, String code, String version, String content) {
        return ProcessRuntimeRequest.versioned(ProcessRef.version(namespace, code, version),
                ProcessDefinition.inline(code, content));
    }

    public static ProcessDefinitionSnapshot resolved(ProcessRuntimeRequest request) {
        ProcessRuntimeRequest supplied = Objects.requireNonNull(request, "request");
        ProcessDefinition definition =
                Objects.requireNonNull(supplied.getDefinition(), "test request must contain a definition");
        if (!(definition instanceof ProcessDefinition.Inline inline)) {
            throw new IllegalArgumentException("test request must contain exact inline content");
        }
        return ProcessDefinitionSnapshot.of(supplied.getNamespace(), supplied.getCode(), supplied.getVersion(),
                inline.content().getBytes(StandardCharsets.UTF_8), "test inline content");
    }

    public static ProcessRuntimeIdentity runtimeIdentity(ProcessRuntimeRequest request, ClassLoader classLoader) {
        return ProcessRuntimeIdentity.of(resolved(request), ProcessModelType.TBBPM, DEFAULT_PIPELINE_IDENTITY,
                classLoader);
    }

    public static ProcessRuntimeIdentity runtimeIdentity(ProcessDefinitionSnapshot definition,
            ProcessRuntimeIdentity.PipelineIdentity pipelineIdentity, ClassLoader classLoader) {
        return ProcessRuntimeIdentity.of(definition, ProcessModelType.TBBPM, pipelineIdentity, classLoader);
    }

    public static String bindingKey(ProcessRuntimeRequest request) {
        return RuntimeCacheKeys.forProcess(request.getNamespace(), request.getCode(), request.getVersion());
    }

    public static ProcessCallInvoker rejectingProcessCallInvoker() {
        return REJECTING_PROCESS_CALL_INVOKER;
    }
}
