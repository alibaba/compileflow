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
import com.alibaba.compileflow.engine.core.java.codegen.JavaProcessCodeGenerator;
import com.alibaba.compileflow.engine.core.java.compiler.GeneratedClassCompiler;
import com.alibaba.compileflow.engine.core.java.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.core.runtime.executable.ExecutableProcess;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptProgramCatalog;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.Map;
import java.util.Objects;

/**
 * Creates compiled runtimes through the single source-to-semantics pipeline.
 *
 * @author yusu
 */
public final class CompiledProcessRuntimeFactory implements ProcessRuntimeFactory {
    private final ProcessSemanticCompiler<?> semanticCompiler;
    private final ScriptExecutorRegistry scripts;
    private final JavaCompiler javaCompiler;
    private final JavaDiagnosticsConfig compilationConfig;

    public CompiledProcessRuntimeFactory(ProcessSemanticCompiler<?> semanticCompiler, ScriptExecutorRegistry scripts,
            JavaCompiler javaCompiler, JavaDiagnosticsConfig compilationConfig) {
        this.semanticCompiler = Objects.requireNonNull(semanticCompiler, "semanticCompiler");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.javaCompiler = Objects.requireNonNull(javaCompiler, "javaCompiler");
        this.compilationConfig = Objects.requireNonNull(compilationConfig, "compilationConfig");
    }

    @Override
    public ProcessRuntime createRuntime(ProcessDefinitionSnapshot definition, ClassLoader classLoader) {
        ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(loader);
            return createInClassLoaderScope(definition, loader);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private ProcessRuntime createInClassLoaderScope(ProcessDefinitionSnapshot definition, ClassLoader classLoader) {
        ProcessSemanticCompiler.ProcessSemanticCompilation compilation = semanticCompiler.compile(definition);
        ProcessRuntimeEligibilityChecker.validate(compilation.semanticPlan(), compilation.structuredPlan());

        Map<ScriptProgramSpec, ScriptProgram> scriptPrograms =
                ScriptProgramCatalog.compile(compilation.semanticPlan(), scripts);
        JavaProcessCodeGenerator generator = new JavaProcessCodeGenerator(compilation.semanticPlan(),
                compilation.structuredPlan(), compilation.nodeNames());
        GeneratedClassCompiler compiler = new GeneratedClassCompiler(compilationConfig, javaCompiler);
        Map<String, String> metadata = Map.of("generator-version", JavaProcessCodeGenerator.GENERATOR_VERSION,
                "process-code", compilation.semanticPlan().getProcessCode(), "semantic-digest",
                compilation.semanticPlan().getDigest());
        Class<? extends ExecutableProcess> executableClass = compiler
            .compile(generator.getClassFullName(), generator.generateCode(), classLoader, metadata)
            .asSubclass(ExecutableProcess.class);
        return new CompiledProcessRuntime(compilation.semanticPlan(), executableClass, scriptPrograms);
    }
}
