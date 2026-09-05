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
package com.alibaba.compileflow.engine.core.java.compiler;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.java.naming.JavaNames;
import com.alibaba.compileflow.engine.core.classloader.ProcessClassLoaderResolver;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles generated Java source, exports optional diagnostics, and loads the result.
 *
 * @author yusu
 */
public final class GeneratedClassCompiler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratedClassCompiler.class);
    private final JavaDiagnosticsConfig compilationConfig;
    private final CompilationDebugExporter debugExporter;
    private final JavaCompiler javaCompiler;

    public GeneratedClassCompiler() {
        this(JavaDiagnosticsConfig.defaults());
    }

    public GeneratedClassCompiler(JavaDiagnosticsConfig compilationConfig) {
        this(compilationConfig, new JdkJavaCompiler());
    }

    public GeneratedClassCompiler(JavaDiagnosticsConfig compilationConfig, JavaCompiler javaCompiler) {
        this.compilationConfig = Objects.requireNonNull(compilationConfig, "compilationConfig");
        this.debugExporter = new CompilationDebugExporter(compilationConfig);
        this.javaCompiler = Objects.requireNonNull(javaCompiler, "javaCompiler");
    }

    private static String failureType(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getName();
    }

    public Class<?> compile(String fullClassName, String sourceCode, ClassLoader classLoader) {
        return compile(fullClassName, sourceCode, classLoader, Map.of());
    }

    public Class<?> compile(String fullClassName, String sourceCode, ClassLoader classLoader,
            Map<String, String> debugMetadata) {
        return compile(fullClassName, sourceCode, List.of(), classLoader, debugMetadata);
    }

    /**
     * Compiles the generated primary class and definition-owned companion sources atomically.
     *
     * @param fullClassName primary generated class name
     * @param sourceCode primary generated source
     * @param additionalSources companion compilation units
     * @param classLoader parent application class loader
     * @param debugMetadata primary-source diagnostic metadata
     * @return loaded primary class; all emitted classes share the same loader
     */
    public Class<?> compile(String fullClassName, String sourceCode, List<JavaSource> additionalSources,
            ClassLoader classLoader, Map<String, String> debugMetadata) {
        String validatedClassName = JavaNames.requireValidClassName(fullClassName);
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        Objects.requireNonNull(debugMetadata, "debugMetadata must not be null");
        try {
            long startedAtNanos = System.nanoTime();
            LOGGER.debug("Starting compilation: class={}", validatedClassName);

            JavaSource javaSource = JavaSource.of(sourceCode, validatedClassName, debugMetadata);
            List<JavaSource> companions =
                    List.copyOf(Objects.requireNonNull(additionalSources, "additionalSources must not be null"));
            LinkedHashSet<String> sourceNames = new LinkedHashSet<>();
            sourceNames.add(validatedClassName);
            for (JavaSource companion : companions) {
                JavaSource unit = Objects.requireNonNull(companion, "additionalSources must not contain null");
                if (!sourceNames.add(unit.getTargetFullClassName())) {
                    throw new IllegalArgumentException("Duplicate Java source unit: " + unit.getTargetFullClassName());
                }
            }
            ArrayList<JavaSource> compilationUnits = new ArrayList<>(companions.size() + 1);
            compilationUnits.add(javaSource);
            compilationUnits.addAll(companions);

            ClassLoader parentClassLoader =
                    ProcessClassLoaderResolver.resolveEffectiveClassLoader(classLoader, GeneratedClassCompiler.class);

            JavaCompileOptions compileOptions =
                    new JavaCompileOptions(compilationConfig.getDebugSymbols(), parentClassLoader);

            MemoryClassOutput classOutput = new MemoryClassOutput(validatedClassName);
            CompilationDebugExporter.DebugArtifacts debugArtifacts =
                    debugExporter.open(javaSource, companions, compileOptions);

            javaCompiler.compile(compilationUnits, classOutput, compileOptions);
            CompiledClasses compiledClasses = classOutput.finish();
            debugArtifacts.exportBytecode(compiledClasses);
            if (debugArtifacts.getArtifactId() != null) {
                LOGGER.info("Compilation debug artifacts exported: class={}, artifact={}", validatedClassName,
                        debugArtifacts.getArtifactId());
            }
            ClassLoader finalClassLoader = new CompiledClassLoader(parentClassLoader, compiledClasses);

            Class<?> compiledClass = finalClassLoader.loadClass(validatedClassName);
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
            LOGGER.info("Compilation successful: class={}, durationMs={}", validatedClassName, duration);
            return compiledClass;
        } catch (CompileFlowException failure) {
            LOGGER.error("Compilation failed: class={}, errorCode={}, failureType={}", validatedClassName,
                    failure.getErrorCode().getCode(), failureType(failure));
            throw failure;
        } catch (Exception failure) {
            LOGGER.error("Compilation failed: class={}, errorCode={}, failureType={}", validatedClassName,
                    ErrorCode.CF_COMPILE_002.getCode(), failureType(failure));
            throw new CompileFlowException(ErrorCode.CF_COMPILE_002,
                    "Failed to compile java code for class: " + validatedClassName, failure);
        }
    }
}
