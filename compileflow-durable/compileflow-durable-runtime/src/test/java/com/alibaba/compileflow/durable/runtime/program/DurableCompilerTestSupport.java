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
import com.alibaba.compileflow.durable.runtime.machine.DurableModelEligibility;
import com.alibaba.compileflow.durable.runtime.machine.DurableProcessCompiler;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptProgramCatalog;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.util.List;

/**
 * Shared end-to-end compilation fixture; production APIs remain realization-neutral.
 *
 * @author yusu
 */
public final class DurableCompilerTestSupport {
    private DurableCompilerTestSupport() {
    }

    public static CompiledMachineProgram compile(TbbpmModel model, ClassLoader classLoader) {
        return compile(model, ScriptExecutorRegistry.from(List.of()), classLoader);
    }

    public static CompiledMachineProgram compile(TbbpmModel model, ScriptExecutorRegistry scripts,
            ClassLoader classLoader) {
        DurableMachinePlan machinePlan = lower(model, scripts);
        return new CompiledMachineProgram(new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults())
                    .compile(machinePlan, classLoader), machinePlan,
                ScriptProgramCatalog.compile(machinePlan.semanticPlan(), scripts));
    }

    public static DurableMachinePlan lower(TbbpmModel model) {
        return lower(model, ScriptExecutorRegistry.from(List.of()));
    }

    public static DurableMachinePlan lower(TbbpmModel model, ScriptExecutorRegistry scripts) {
        return new DurableProcessCompiler(scripts).lower(new TbbpmSemanticFrontend().compile(model));
    }

    public static DurableModelEligibility check(TbbpmModel model) {
        return new DurableProcessCompiler().check(new TbbpmSemanticFrontend().compile(model));
    }
}
