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
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.java.compiler.GeneratedClassCompiler;
import java.util.Map;
import java.util.Objects;

/**
 * Java realization compiler for one exact source-format-neutral Durable machine.
 *
 * @author yusu
 */
public final class DurableJavaProgramCompiler implements DurableProgramCompiler {
    /**
     * Runtime-only generator label used for diagnostics and cache eviction.
     */
    public static final String GENERATOR_VERSION = "compileflow.durable-java/v1";
    private final GeneratedClassCompiler classCompiler;

    public DurableJavaProgramCompiler() {
        this(JavaDiagnosticsConfig.defaults());
    }

    public DurableJavaProgramCompiler(JavaDiagnosticsConfig compilationConfig) {
        this.classCompiler = new GeneratedClassCompiler(Objects.requireNonNull(compilationConfig, "compilationConfig"));
    }

    @Override
    public DurableProgram compile(DurableMachinePlan machinePlan, ClassLoader classLoader) {
        DurableMachinePlan plan = Objects.requireNonNull(machinePlan, "machinePlan");
        ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
        DurableJavaProgramCodeGenerator generator = new DurableJavaProgramCodeGenerator(plan, loader);
        String source = generator.generateCode();
        Class<? extends DurableProgram> programClass = classCompiler
            .compile(generator.getClassFullName(), source, loader,
                    Map.of("generator-version", GENERATOR_VERSION, "process-code", plan
                                .semanticPlan()
                                .getProcessCode(), "machine-digest", plan.digest()))
            .asSubclass(DurableProgram.class);
        try {
            DurableProgram program = programClass.getConstructor().newInstance();
            return program;
        } catch (NoSuchMethodException missingConstructor) {
            // The generator always has a public no-arg constructor, so a miss means the
            // generated source and this compiler have drifted apart.
            throw new IllegalStateException("Generated Durable program " + programClass.getName()
                    + " declares an unexpected constructor", missingConstructor);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Generated Durable program " + programClass.getName()
                    + " could not be instantiated", failure);
        }
    }

    /**
     * Generates diagnostics on demand; loaded-program cache entries never retain source text.
     */
    public String generateSource(DurableMachinePlan machinePlan) {
        return new DurableJavaProgramCodeGenerator(Objects.requireNonNull(machinePlan, "machinePlan")).generateCode();
    }

    /**
     * Generates diagnostics against the same application types used by compilation.
     */
    public String generateSource(DurableMachinePlan machinePlan, ClassLoader classLoader) {
        return new DurableJavaProgramCodeGenerator(Objects.requireNonNull(machinePlan, "machinePlan"),
                Objects.requireNonNull(classLoader, "classLoader"))
            .generateCode();
    }
}
