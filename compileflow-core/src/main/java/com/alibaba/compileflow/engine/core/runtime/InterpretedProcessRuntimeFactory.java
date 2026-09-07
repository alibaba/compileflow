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

import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.java.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler.ProcessSemanticCompilation;
import com.alibaba.compileflow.engine.core.runtime.action.ProcessActionInvoker;
import com.alibaba.compileflow.engine.core.runtime.expression.CompiledExpressionEvaluator;
import com.alibaba.compileflow.engine.core.runtime.expression.RuntimeExpressionCompiler;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptProgramCatalog;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.Map;
import java.util.Objects;

/**
 * Realizes shared process semantics as interpreted execution.
 *
 * @author yusu
 */
public final class InterpretedProcessRuntimeFactory implements ProcessRuntimeFactory {
    private final RuntimeExpressionCompiler expressionCompiler;
    private final ProcessComponentResolver components;
    private final ScriptExecutorRegistry scripts;

    public InterpretedProcessRuntimeFactory(JavaCompiler javaCompiler, JavaDiagnosticsConfig compilationConfig,
            ProcessComponentResolver components, ScriptExecutorRegistry scripts) {
        this.expressionCompiler = new RuntimeExpressionCompiler(javaCompiler, compilationConfig);
        this.components = Objects.requireNonNull(components, "components");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
    }

    @Override
    public ProcessRuntime createRuntime(ProcessSemanticCompilation compilation, ClassLoader classLoader) {
        Objects.requireNonNull(compilation, "compilation");
        ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
        ProcessRuntimeEligibilityChecker.validate(compilation.semanticPlan(), compilation.structuredPlan());
        Map<ScriptProgramSpec, ScriptProgram> scriptPrograms =
                ScriptProgramCatalog.compile(compilation.semanticPlan(), scripts);
        ProcessActionInvoker actions = new ProcessActionInvoker(components, scripts, loader, scriptPrograms);
        InterpretedProcessRuntimeEligibilityChecker.validate(compilation.semanticPlan(), actions);
        InterpretedExpressionCatalog catalog = new InterpretedExpressionCatalog(compilation.semanticPlan());
        CompiledExpressionEvaluator evaluator =
                expressionCompiler.compile(compilation.semanticPlan().getProcessCode(), catalog.expressions(), loader);
        return new InterpretedProcessRuntime(compilation.semanticPlan(), compilation.structuredPlan(), catalog,
                evaluator, actions, loader);
    }
}
