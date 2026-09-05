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
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * In-memory compiler for generated process classes using the standard Java Compiler API.
 *
 * @author yusu
 */
public final class JdkJavaCompiler implements JavaCompiler {
    /**
     * Creates a compiler and fails immediately when the runtime omits {@code jdk.compiler}.
     */
    public JdkJavaCompiler() {
        requireCompilerModule();
    }

    private static ClassLoader effectiveParentClassLoader(JavaCompileOptions options) {
        return options.parentClassLoader() != null
                ? options.parentClassLoader()
                : JdkJavaCompiler.class.getClassLoader();
    }

    private static List<String> compilerOptions(JavaCompileOptions options) {
        List<String> flags = new ArrayList<>();
        flags.add("--release");
        flags.add(JavaCompileOptions.JAVA_RELEASE);
        flags.add("-encoding");
        flags.add(StandardCharsets.UTF_8.name());
        flags.add("-proc:none");
        switch (options.debugSymbols()) {
            case NONE -> flags.add("-g:none");
            case LINES -> flags.add("-g:source,lines");
            case FULL -> flags.add("-g");
        }
        return List.copyOf(flags);
    }

    private static CompileFlowException.ConfigurationException missingCompiler() {
        return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                "Runtime Java image must include the jdk.compiler module for generated-code "
                + "compilation (java.version=" + System.getProperty("java.version") + ")");
    }

    private static javax.tools.JavaCompiler systemCompiler() {
        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw missingCompiler();
        }
        return compiler;
    }

    private static void requireCompilerModule() {
        if (ToolProvider.getSystemJavaCompiler() == null) {
            throw missingCompiler();
        }
    }

    private static CompileFlowException compilationFailure(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder message = new StringBuilder("Java compilation failed:\n");
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            String sourceName = diagnostic.getSource() instanceof SourceFileObject source
                    ? source.displayName()
                    : Objects.toString(diagnostic.getSource(), "unknown-source");
            long lineNumber = diagnostic.getSource() instanceof SourceFileObject source
                    ? source.displayLine(diagnostic.getLineNumber())
                    : diagnostic.getLineNumber();
            message
                .append("[ERROR] ")
                .append(sourceName)
                .append(':')
                .append(lineNumber)
                .append(':')
                .append(diagnostic.getColumnNumber())
                .append(' ')
                .append(diagnostic.getMessage(Locale.ROOT))
                .append('\n');
        }
        return new CompileFlowException(ErrorCode.CF_COMPILE_002, message.toString(), null);
    }

    private static URI memoryUri(String binaryName, JavaFileObject.Kind kind) {
        return URI.create("mem:///" + binaryName.replace('.', '/') + kind.extension);
    }

    @Override
    public void compile(List<JavaSource> javaSources, ClassOutput classOutput, JavaCompileOptions options)
            throws Exception {
        List<JavaSource> sources = List.copyOf(Objects.requireNonNull(javaSources, "javaSources must not be null"));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("javaSources must not be empty");
        }
        LinkedHashSet<String> binaryNames = new LinkedHashSet<>();
        for (JavaSource source : sources) {
            JavaSource unit = Objects.requireNonNull(source, "javaSources must not contain null");
            if (!binaryNames.add(unit.getTargetFullClassName())) {
                throw new IllegalArgumentException("Duplicate Java source unit: " + unit.getTargetFullClassName());
            }
        }
        ClassOutput output = Objects.requireNonNull(classOutput, "classOutput must not be null");
        JavaCompileOptions compileOptions = Objects.requireNonNull(options, "options must not be null");

        javax.tools.JavaCompiler compiler = systemCompiler();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standard =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            standard.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of());
            GeneratedCodeFileManager files =
                    new GeneratedCodeFileManager(standard, effectiveParentClassLoader(compileOptions));
            List<JavaFileObject> sourceFiles =
                    sources.stream().map(SourceFileObject::new).map(JavaFileObject.class::cast).toList();
            javax.tools.JavaCompiler.CompilationTask task =
                    compiler.getTask(null, files, diagnostics, compilerOptions(compileOptions), null, sourceFiles);
            if (!Boolean.TRUE.equals(task.call())) {
                throw compilationFailure(diagnostics);
            }
            files.writeClasses(output);
        } catch (CompileFlowException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new CompileFlowException(ErrorCode.CF_COMPILE_002, "Java compiler file-manager failure", failure);
        }
    }

    private static final class SourceFileObject extends SimpleJavaFileObject {
        private final JavaSource source;

        private SourceFileObject(JavaSource source) {
            super(memoryUri(source.getTargetFullClassName(), Kind.SOURCE), Kind.SOURCE);
            this.source = source;
        }

        private String displayName() {
            return source.getDebugMetadata().getOrDefault("source-origin", source.getTargetFullClassName());
        }

        private long displayLine(long compilerLine) {
            if (compilerLine < 1) {
                return compilerLine;
            }
            String configuredOffset = source.getDebugMetadata().get("diagnostic-line-offset");
            if (configuredOffset == null) {
                return compilerLine;
            }
            try {
                int offset = Integer.parseInt(configuredOffset);
                return offset < 0 ? compilerLine : Math.max(1, compilerLine - offset);
            } catch (NumberFormatException ignored) {
                return compilerLine;
            }
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source.getJavaSourceCode();
        }
    }
}
