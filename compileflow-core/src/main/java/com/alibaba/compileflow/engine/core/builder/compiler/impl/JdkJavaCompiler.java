package com.alibaba.compileflow.engine.core.builder.compiler.impl;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.compiler.ClassOutput;
import com.alibaba.compileflow.engine.core.builder.compiler.CompileOption;
import com.alibaba.compileflow.engine.core.builder.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.core.builder.compiler.JavaSource;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A JavaCompiler implementation that uses the JDK's built-in javax.tools.JavaCompiler.
 *
 * <p>This implementation supports JDK 8 and above. It requires a full JDK (not just a JRE)
 * because the compiler is part of the JDK. On JDK 8, ensure tools.jar is on the classpath;
 * on JDK 9 and above, ensure the jdk.compiler module is present.
 *
 * <p>Compilation options are constructed to be compatible with both Java 8 and later versions.
 * The --release option is used when targeting JDK 9 and above; otherwise, -source and -target
 * are used. The classpath is merged with the current process's classpath to ensure all
 * necessary dependencies are available during compilation.
 *
 * @author yusu
 */
@ExtensionRealization()
public class JdkJavaCompiler implements JavaCompiler {

    /**
     * Builds the compiler options, prioritizing `--release` (for JDK 9+) and falling back
     * to `-source`/`-target`. It also merges the provided classpath with the process's
     * default classpath.
     */
    private static List<String> buildOptions(CompileOption opt) {
        final List<String> options = new ArrayList<>();

        // Encoding
        if (opt != null && opt.getEncoding() != null && !opt.getEncoding().isEmpty()) {
            options.addAll(Arrays.asList("-encoding", opt.getEncoding()));
        }

        // Version: prefer --release (JDK 9+)
        String targetVersion = (opt != null ? opt.getJavaVersion() : null);
        if (targetVersion != null && !targetVersion.isEmpty()) {
            if (isAtLeastJava9() && isNumberLike(targetVersion)) {
                options.addAll(Arrays.asList("--release", normalizeRelease(targetVersion)));
            } else {
                options.addAll(Arrays.asList("-source", targetVersion, "-target", targetVersion));
            }
        }

        // Classpath: merge user-provided and process classpaths (deduplicated)
        final List<String> mergedCp = new ArrayList<>();
        if (opt != null && opt.getClasspath() != null && !opt.getClasspath().isEmpty()) {
            mergedCp.addAll(opt.getClasspath());
        }
        String selfCp = System.getProperty("java.class.path");
        if (selfCp != null && !selfCp.isEmpty()) {
            mergedCp.addAll(Arrays.asList(selfCp.split(File.pathSeparator)));
        }
        final String cp = mergedCp.stream()
                .filter(s -> s != null && !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(File.pathSeparator));
        if (!cp.isEmpty()) {
            options.add("-classpath");
            options.add(cp);
        }

        // Optional: module path (if provided by CompileOption)
        try {
            @SuppressWarnings("unchecked")
            List<String> modulePath = (List<String>) CompileOption.class
                    .getMethod("getModulepath")
                    .invoke(opt);
            if (modulePath != null && !modulePath.isEmpty()) {
                options.add("--module-path");
                options.add(String.join(File.pathSeparator, modulePath));
            }
        } catch (Exception ignore) {
            // Ignore if the method is not available in CompileOption
        }

        // Optional: keep parameter names (if isKeepParameterNames is available)
        try {
            Boolean keep = (Boolean) CompileOption.class
                    .getMethod("isKeepParameterNames")
                    .invoke(opt);
            if (Boolean.TRUE.equals(keep)) {
                options.add("-parameters");
            }
        } catch (Exception ignore) {
        }

        // Optional: enable preview features (if isEnablePreview is available)
        try {
            Boolean preview = (Boolean) CompileOption.class
                    .getMethod("isEnablePreview")
                    .invoke(opt);
            if (Boolean.TRUE.equals(preview)) {
                options.add("--enable-preview");
            }
        } catch (Exception ignore) {
        }

        // Optional: annotation processing config (processorpath/processor/proc)
        try {
            @SuppressWarnings("unchecked")
            List<String> pp = (List<String>) CompileOption.class
                    .getMethod("getProcessorPath")
                    .invoke(opt);
            if (pp != null && !pp.isEmpty()) {
                options.add("-processorpath");
                options.add(String.join(File.pathSeparator, pp));
            }
            @SuppressWarnings("unchecked")
            List<String> processors = (List<String>) CompileOption.class
                    .getMethod("getProcessors")
                    .invoke(opt);
            if (processors != null && !processors.isEmpty()) {
                options.add("-processor");
                options.add(String.join(",", processors));
            }
            String procMode = (String) CompileOption.class
                    .getMethod("getProcMode")
                    .invoke(opt);
            if (procMode != null && !procMode.isEmpty()) {
                // e.g., "none" or "only"
                options.add("-proc:" + procMode);
            }
        } catch (Exception ignore) {
        }

        // Optional: pass through other native compiler args (if getExtraJavacArgs is available)
        try {
            @SuppressWarnings("unchecked")
            List<String> extra = (List<String>) CompileOption.class
                    .getMethod("getExtraJavacArgs")
                    .invoke(opt);
            if (extra != null && !extra.isEmpty()) {
                options.addAll(extra);
            }
        } catch (Exception ignore) {
        }

        return options;
    }

    private static boolean isAtLeastJava9() {
        String ver = System.getProperty("java.specification.version");
        // "1.8" for Java 8; "9", "11", "17", ... afterwards
        if (ver == null) return false;
        if (ver.startsWith("1.")) {
            return false; // 1.8
        }
        try {
            return Integer.parseInt(ver) >= 9;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isNumberLike(String v) {
        // Allows versions like "8", "11", "17", "21", or "1.8"
        return v.matches("(?:\\d+|1\\.\\d+)");
    }

    private static String normalizeRelease(String v) {
        // Normalizes "1.8" to "8"
        if (v.startsWith("1.")) {
            return v.substring(2);
        }
        return v;
    }

    private static Charset safeCharset(String name) {
        if (name == null || name.isEmpty()) return StandardCharsets.UTF_8;
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String safeName(JavaFileObject jfo) {
        try {
            String n = jfo.getName();
            return (n != null && !n.isEmpty()) ? n : jfo.toUri().toString();
        } catch (Exception e) {
            return jfo.toUri().toString();
        }
    }

    @Override
    public void compile(JavaSource javaSource, ClassOutput classOutput, CompileOption compileOption) throws Exception {
        Objects.requireNonNull(javaSource, "javaSource must not be null");
        Objects.requireNonNull(classOutput, "classOutput must not be null");

        final javax.tools.JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
        if (systemCompiler == null) {
            String msg = String.format(Locale.ROOT,
                    "JdkJavaCompiler requires a full JDK (java.version=%s, java.home=%s). " +
                            "On JDK 8 ensure tools.jar is on the classpath; on JDK 9+ ensure jdk.compiler module is present.",
                    System.getProperty("java.version"), System.getProperty("java.home"));
            throw new CompileFlowException.ConfigurationException(
                    ErrorCode.CF_CONFIG_001,
                    msg
            );
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager stdFileManager = systemCompiler.getStandardFileManager(
                diagnostics, null,
                StandardCharsets.UTF_8);
             JavaFileManager memFileManager = new ForwardingJavaFileManager<StandardJavaFileManager>(stdFileManager) {
                 @Override
                 public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                                                            FileObject sibling) throws IOException {
                     return new BytecodeJavaFileObject(className, kind, classOutput);
                 }
             }) {

            // Source from string (prefer) to avoid re-reading the temp file
            JavaFileObject srcObject = new StringJavaFileObject(javaSource.getTargetFullClassName(),
                    javaSource.getJavaSourceCode());

            List<String> options = buildJavacOptions(compileOption);

            javax.tools.JavaCompiler.CompilationTask task = systemCompiler.getTask(
                    null, memFileManager, diagnostics, options, null, Arrays.asList(srcObject));

            Boolean success = task.call();

            if (!Boolean.TRUE.equals(success)) {
                throw new CompileFlowException.SystemException(
                        ErrorCode.CF_COMPILE_002,
                        formatDiagnostics(diagnostics),
                        null
                );
            }
        }
    }

    /**
     * Build javac option list with Java 8+ compatibility
     */
    private List<String> buildJavacOptions(CompileOption compileOption) {
        List<String> opts = new ArrayList<>();
        if (compileOption.getEncoding() != null) {
            opts.addAll(Arrays.asList("-encoding", compileOption.getEncoding()));
        }

        String v = compileOption.getJavaVersion();
        if (v != null) {
            String norm = v.startsWith("1.") ? v.substring(2) : v;
            try {
                int major = Integer.parseInt(norm);
                if (major >= 9) {
                    opts.addAll(Arrays.asList("--release", norm));
                } else {
                    opts.addAll(Arrays.asList("-source", v, "-target", v));
                }
            } catch (NumberFormatException ignore) {
                opts.addAll(Arrays.asList("-source", v, "-target", v));
            }
        }

        if (compileOption.getClasspath() != null && !compileOption.getClasspath().isEmpty()) {
            opts.add("-classpath");
            opts.add(String.join(File.pathSeparator, compileOption.getClasspath()));
        }
        return opts;
    }

    private String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder("JdkJavaCompiler failed:\n");
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            sb.append("[").append(d.getKind()).append("] ")
                    .append(d.getSource() != null ? d.getSource().getName() : "<unknown>")
                    .append(":").append(d.getLineNumber()).append(" ")
                    .append(d.getMessage(null)).append('\n');
        }
        return sb.toString();
    }

    /**
     * In-memory representation of a Java source file.
     */
    static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        StringJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        @Override
        public String getName() {
            return toUri().getPath();
        }
    }

    /**
     * In-memory representation of a Java bytecode file.
     */
    static final class BytecodeJavaFileObject extends SimpleJavaFileObject {
        private final String className;
        private final ClassOutput classOutput;

        BytecodeJavaFileObject(String className, Kind kind, ClassOutput classOutput) {
            super(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind);
            this.className = className;
            this.classOutput = classOutput;
        }

        @Override
        public OutputStream openOutputStream() {
            return new ClassOutputByteArray(className, classOutput);
        }
    }

    /**
     * ByteArray buffer that writes into ClassOutput on close.
     */
    static final class ClassOutputByteArray extends ByteArrayOutputStream {
        private final String className;
        private final ClassOutput classOutput;

        ClassOutputByteArray(String className, ClassOutput classOutput) {
            this.className = className;
            this.classOutput = classOutput;
        }

        @Override
        public void close() throws IOException {
            super.close();
            try {
                classOutput.writeClass(className, toByteArray());
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
