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
package com.alibaba.compileflow.workbench.server.execution;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessEngine;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Runs explicit draft definitions for Workbench Learn and Designer previews.
 *
 * @author yusu
 */
@Service
@ConditionalOnProperty(prefix = "compileflow.workbench.server.preview-execution", name = "enabled", havingValue = "true")
public final class PreviewExecutionService {
    private final ProcessEngine engine;

    public PreviewExecutionService(ProcessEngine engine) {
        this.engine = java.util.Objects.requireNonNull(engine, "engine");
    }

    private static String resolveInvocationId(String invocationId) {
        String resolved = ProcessIdentifiers.optionalInvocationId(invocationId);
        return resolved == null ? "inv-" + UUID.randomUUID() : resolved;
    }

    /**
     * Executes the supplied trusted definition without persistence, publication, or deployment
     * routing.
     *
     * <p>The process runs real actions with this server's privileges. This method is not a
     * sandbox or a validation-only operation.
     *
     * @param code         process code
     * @param modelType    definition format
     * @param xml          explicit XML definition
     * @param invocationId optional caller correlation id
     * @param params       process variables
     * @return structured process outcome
     */
    public ProcessExecutionResponse execute(String code, ProcessModelType modelType, String xml, String invocationId,
            Map<String, Object> params) {
        long startedAtNanos = System.nanoTime();
        ProcessExecutionOptions options =
                ProcessExecutionOptions.builder().invocationId(resolveInvocationId(invocationId)).build();
        ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.inline(modelType, code, xml),
                params == null ? new HashMap<>() : new HashMap<>(params), options);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        return ProcessExecutionResponse.from(result, durationMs);
    }
}
