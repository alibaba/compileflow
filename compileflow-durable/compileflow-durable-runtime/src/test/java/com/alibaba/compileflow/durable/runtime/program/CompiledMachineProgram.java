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

import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.Map;
import java.util.Objects;

/**
 * Test fixture pairing a machine with its Java realization.
 *
 * @author yusu
 */
public record CompiledMachineProgram(DurableProgram program, DurableMachinePlan machinePlan,
        Map<ScriptProgramSpec, ScriptProgram> scriptPrograms) {
    public CompiledMachineProgram {
        program = Objects.requireNonNull(program, "program");
        machinePlan = Objects.requireNonNull(machinePlan, "machinePlan");
        scriptPrograms = Map.copyOf(Objects.requireNonNull(scriptPrograms, "scriptPrograms"));
    }
}
