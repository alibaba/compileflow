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
package com.alibaba.compileflow.engine.core.runtime.script;

import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptException;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Trusted in-process Java method-body executor for definition-owned Script actions.
 *
 * <p>This executor intentionally is not a sandbox. Deployments that accept untrusted source must
 * execute it through an isolated runner with operating-system or container boundaries. The local
 * provider targets Java 17, exposes only explicitly declared inputs, and compiles without a
 * process-specific application class loader. Compiled bytecode is owned by the Process runtime
 * that requested it, rather than by this provider.
 *
 * @author yusu
 */
public final class JavaSourceScriptExecutor implements ScriptExecutor {
    private static final String LANGUAGE = "java";
    private static final String PACKAGE = "com.alibaba.compileflow.engine.core.runtime.script.generated";
    private static final Set<String> PRIMITIVES =
            Set.of("byte", "short", "int", "long", "float", "double", "boolean", "char");
    private static final Set<String> TYPE_KEYWORDS = Set.of("extends", "super");

    @Override
    public String name() {
        return LANGUAGE;
    }

    @Override
    public void validate(ScriptProgramSpec spec) {
        compileProgram(spec, ScriptException.Kind.INVALID_SOURCE);
    }

    @Override
    public ScriptProgram compile(ScriptProgramSpec spec) {
        return compileProgram(spec, ScriptException.Kind.COMPILATION_FAILED);
    }

    @Override
    public Object evaluate(ScriptProgram script, Map<String, Object> context) {
        if (!(script instanceof CompiledJavaProgram program)) {
            throw new ScriptException(ScriptException.Kind.EVALUATION_FAILED,
                    "Script program was not created by the Java source script executor");
        }
        Objects.requireNonNull(context, "context");
        try {
            return (Object) program.execute().invokeExact(context);
        } catch (ScriptException classified) {
            throw classified;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ScriptException(ScriptException.Kind.CANCELLED, "Java code evaluation was interrupted",
                    interrupted);
        } catch (Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new ScriptException(ScriptException.Kind.EVALUATION_FAILED, "Java code evaluation failed", failure);
        }
    }

    private CompiledJavaProgram compileProgram(ScriptProgramSpec spec, ScriptException.Kind failureKind) {
        Objects.requireNonNull(spec, "spec");
        if (!LANGUAGE.equals(spec.language())) {
            throw new ScriptException(failureKind,
                    "Java source script executor only compiles language 'java', not '" + spec.language() + "'");
        }
        return compile(spec, failureKind);
    }

    private static CompiledJavaProgram compile(ScriptProgramSpec spec, ScriptException.Kind failureKind) {
        validateInputs(spec, failureKind);
        validateOutput(spec, failureKind);
        String simpleName = "CfJavaCode_" + digest(spec);
        String binaryName = PACKAGE + '.' + simpleName;
        GeneratedSource source = new GeneratedSource(binaryName, renderSource(simpleName, spec));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ScriptException(ScriptException.Kind.COMPILATION_FAILED,
                    "Java code execution requires a Java runtime with the jdk.compiler module");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standard =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            // --release selects the Java 17 platform API. Clearing CLASS_PATH also prevents
            // definition-owned code from accidentally binding to the embedding application's jars.
            standard.setLocationFromPaths(StandardLocation.CLASS_PATH, List.<Path>of());
            MemoryFileManager files = new MemoryFileManager(standard);
            JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics,
                    List.of("--release", "17", "-encoding", "UTF-8", "-proc:none"), null, List.of(source));
            if (!Boolean.TRUE.equals(task.call())) {
                throw compilationFailure(diagnostics, failureKind, userSourceStartLine(spec));
            }
            ClassLoader loader =
                    new ByteArrayClassLoader(JavaSourceScriptExecutor.class.getClassLoader(), files.classes());
            Class<?> compiled = Class.forName(binaryName, true, loader);
            Method executeMethod = compiled.getDeclaredMethod("execute", Map.class);
            MethodHandle execute =
                    MethodHandles
                .publicLookup()
                .unreflect(executeMethod)
                .asType(MethodType.methodType(Object.class, Map.class));
            return new CompiledJavaProgram(execute);
        } catch (ScriptException classified) {
            throw classified;
        } catch (ReflectiveOperationException | IOException failure) {
            throw new ScriptException(failureKind, "Java code compilation failed", failure);
        }
    }

    private static void validateInputs(ScriptProgramSpec spec, ScriptException.Kind failureKind) {
        Set<String> names = new LinkedHashSet<>();
        for (ScriptProgramSpec.Input input : spec.inputs()) {
            if (!SourceVersion.isIdentifier(input.name()) || SourceVersion.isKeyword(input.name())) {
                throw new ScriptException(failureKind,
                        "Java code input name is not a valid Java identifier: " + input.name());
            }
            if (!names.add(input.name())) {
                throw new ScriptException(failureKind, "Java code input names must be unique: " + input.name());
            }
            validateJdkType(input.declaredType(), failureKind);
        }
    }

    private static void validateOutput(ScriptProgramSpec spec, ScriptException.Kind failureKind) {
        if (spec.expectedOutputType() != null) {
            validateJdkType(spec.expectedOutputType(), failureKind);
        }
    }

    private static void validateJdkType(String declaredType, ScriptException.Kind failureKind) {
        String type = declaredType.replace("[]", "").replaceAll("[<>,?]", " ").trim();
        for (String token : type.split("\\s+")) {
            if (token.isEmpty() || TYPE_KEYWORDS.contains(token) || PRIMITIVES.contains(token)
                    || token.startsWith("java.")) {
                continue;
            }
            throw new ScriptException(failureKind,
                    "Java code inputs must use Java 17 platform types, not '" + declaredType + "'");
        }
    }

    private static String renderSource(String simpleName, ScriptProgramSpec spec) {
        Set<String> inputNames = spec
            .inputs()
            .stream()
            .map(ScriptProgramSpec.Input::name)
            .collect(Collectors.toSet());
        String contextParameter = "input";
        while (inputNames.contains(contextParameter)) {
            contextParameter = '_' + contextParameter;
        }
        StringBuilder source = new StringBuilder();
        source.append("package ").append(PACKAGE).append(";\n\n");
        source.append("import java.math.*;\n");
        source.append("import java.time.*;\n");
        source.append("import java.util.*;\n\n");
        source.append("public final class ").append(simpleName).append(" {\n");
        source
            .append("    public static ")
            .append(spec.expectedOutputType() == null ? "Object" : spec.expectedOutputType())
            .append(" execute(Map<String, Object> ")
            .append(contextParameter)
            .append(") throws Exception {\n");
        for (ScriptProgramSpec.Input input : spec.inputs()) {
            source.append("        ").append(inputDeclaration(input, contextParameter)).append("\n");
        }
        appendIndentedSource(source, spec.source());
        source.append("    }\n\n");
        source.append("    private static Object requireInput(Map<String, Object> input, String name) {\n");
        source.append("        if (!input.containsKey(name)) {\n");
        source.append("            throw new IllegalArgumentException(\"Missing script input: \" + name);\n");
        source.append("        }\n");
        source.append("        return input.get(name);\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private static long userSourceStartLine(ScriptProgramSpec spec) {
        // package/imports/class/method occupy eight lines before declarations; each input is one line.
        return 9L + spec.inputs().size();
    }

    private static String inputDeclaration(ScriptProgramSpec.Input input, String contextParameter) {
        String value = "requireInput(" + contextParameter + ", " + stringLiteral(input.name()) + ")";
        String type = input.declaredType();
        return switch (type) {
            case "byte" -> "byte " + input.name() + " = ((Number) " + value + ").byteValue();";
            case "short" -> "short " + input.name() + " = ((Number) " + value + ").shortValue();";
            case "int" -> "int " + input.name() + " = ((Number) " + value + ").intValue();";
            case "long" -> "long " + input.name() + " = ((Number) " + value + ").longValue();";
            case "float" -> "float " + input.name() + " = ((Number) " + value + ").floatValue();";
            case "double" -> "double " + input.name() + " = ((Number) " + value + ").doubleValue();";
            case "boolean" -> "boolean " + input.name() + " = ((Boolean) " + value + ").booleanValue();";
            case "char" -> "char " + input.name() + " = ((Character) " + value + ").charValue();";
            default -> type + " " + input.name() + " = (" + type + ") " + value + ";";
        };
    }

    private static void appendIndentedSource(StringBuilder target, String source) {
        String[] lines = source.split("\\R", -1);
        for (String line : lines) {
            target.append("        ").append(line).append('\n');
        }
    }

    private static ScriptException compilationFailure(DiagnosticCollector<JavaFileObject> diagnostics,
            ScriptException.Kind failureKind, long userSourceStartLine) {
        List<String> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                long userLine = Math.max(1, diagnostic.getLineNumber() - userSourceStartLine + 1L);
                errors.add(
                        "line " + userLine + ':' + diagnostic.getColumnNumber() + ' ' + diagnostic.getMessage(
                                Locale.ROOT));
            }
        }
        String detail = errors.isEmpty() ? "Java code compilation failed" : String.join("\n", errors);
        return new ScriptException(failureKind, detail);
    }

    private static String digest(ScriptProgramSpec spec) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(spec.toString().getBytes(StandardCharsets.UTF_8));
            byte[] bytes = digest.digest();
            StringBuilder result = new StringBuilder(24);
            for (int index = 0; index < 12; index++) {
                result.append(String.format(Locale.ROOT, "%02x", bytes[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static String stringLiteral(String value) {
        StringBuilder literal = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> literal.append("\\\\");
                case '"' -> literal.append("\\\"");
                case '\n' -> literal.append("\\n");
                case '\r' -> literal.append("\\r");
                case '\t' -> literal.append("\\t");
                default -> literal.append(character);
            }
        }
        return literal.append('"').toString();
    }

    private record CompiledJavaProgram(MethodHandle execute) implements ScriptProgram {
        @Override
        public String language() {
            return LANGUAGE;
        }
    }

    private static final class GeneratedSource extends SimpleJavaFileObject {
        private final String source;

        private GeneratedSource(String binaryName, String source) {
            super(URI.create("mem:///" + binaryName.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteCode> outputs = new LinkedHashMap<>();

        private MemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                FileObject sibling) {
            ByteCode output = new ByteCode(className, kind);
            outputs.put(className, output);
            return output;
        }

        private Map<String, byte[]> classes() {
            Map<String, byte[]> classes = new LinkedHashMap<>();
            outputs.forEach((name, value) -> classes.put(name, value.bytes()));
            return Map.copyOf(classes);
        }
    }

    private static final class ByteCode extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        private ByteCode(String binaryName, Kind kind) {
            super(URI.create("mem:///" + binaryName.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }

        private byte[] bytes() {
            return bytes.toByteArray();
        }
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        private ByteArrayClassLoader(ClassLoader parent, Map<String, byte[]> classes) {
            super(parent);
            this.classes = new java.util.HashMap<>(classes);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            Class<?> defined = defineClass(name, bytes, 0, bytes.length);
            classes.remove(name);
            return defined;
        }
    }
}
