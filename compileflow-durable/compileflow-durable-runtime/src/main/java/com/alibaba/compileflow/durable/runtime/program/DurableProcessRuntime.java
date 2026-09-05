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
package com.alibaba.compileflow.durable.runtime.program;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Loaded, disposable executable realization of one immutable stored Process.
 *
 * @author yusu
 */
public record DurableProcessRuntime(UUID processId, String processCode, ProcessModelType modelType,
        String definitionDigest, DurableMachinePlan machinePlan, DurableProgram program,
        DurableValueSerializer valueSerializer, Map<ScriptProgramSpec, ScriptProgram> scriptPrograms) {
    public DurableProcessRuntime {
        processId = Objects.requireNonNull(processId, "processId");
        processCode = ProcessIdentifiers.requireCode(processCode);
        modelType = Objects.requireNonNull(modelType, "modelType");
        definitionDigest = DurableIdentifiers.requireSha256(definitionDigest, "definitionDigest");
        machinePlan = Objects.requireNonNull(machinePlan, "machinePlan");
        program = Objects.requireNonNull(program, "program");
        valueSerializer = Objects.requireNonNull(valueSerializer, "valueSerializer");
        scriptPrograms = Map.copyOf(Objects.requireNonNull(scriptPrograms, "scriptPrograms"));
        if (!processCode.equals(machinePlan.semanticPlan().getProcessCode())) {
            throw new IllegalArgumentException("Stored Process does not match Durable Machine Plan");
        }
    }
}
