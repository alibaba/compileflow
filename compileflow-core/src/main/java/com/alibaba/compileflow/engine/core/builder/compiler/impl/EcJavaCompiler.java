/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements...
 */
package com.alibaba.compileflow.engine.core.builder.compiler.impl;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.compiler.ClassOutput;
import com.alibaba.compileflow.engine.core.builder.compiler.CompileOption;
import com.alibaba.compileflow.engine.core.builder.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.core.builder.compiler.JavaSource;
import com.alibaba.compileflow.engine.core.builder.compiler.constants.FileConstants;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An implementation of {@link JavaCompiler} that uses the Eclipse JDT (ECJ) compiler.
 * <p>
 * This compiler operates in-memory and is generally faster than the standard JDK compiler.
 * It provides robust diagnostics with detailed error messages and source code highlighting.
 * The implementation supports resolving dependencies from a parent classloader and a
 * configurable classpath of directories and JAR files.
 * <p>
 * It is registered as an extension with a priority of 600, making it preferred over
 * the default JDK compiler if the ECJ library is on the classpath.
 *
 * @author yusu
 */
@ExtensionRealization(priority = 600)
public class EcJavaCompiler implements JavaCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EcJavaCompiler.class);
    private static final Set<String> SUPPORTED_ECJ_VERSIONS = new HashSet<>(Arrays.asList(
            CompilerOptions.VERSION_1_5, CompilerOptions.VERSION_1_6,
            CompilerOptions.VERSION_1_7, CompilerOptions.VERSION_1_8));
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(1\\.)?([0-9]+)(\\.[0-9]+)*$");

    /**
     * Creates the compiler options for ECJ based on the provided CompileOption.
     *
     * @param compileOption The compilation settings.
     * @return A configured {@link CompilerOptions} instance.
     */
    private static CompilerOptions getCompilerOptions(CompileOption compileOption) {
        Map<String, String> settings = new HashMap<>();

        // Generate debug information (line numbers, source file, local variables)
        // to enable better stack traces and debugging.
        settings.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE);
        settings.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.GENERATE);
        settings.put(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.GENERATE);

        // Enable reporting of deprecation warnings.
        settings.put(CompilerOptions.OPTION_ReportDeprecation, CompilerOptions.WARNING);

        buildVersionSettings(settings, compileOption);
        buildEncodingSettings(settings, compileOption);

        CompilerOptions compilerOptions = new CompilerOptions(settings);
        // This optimization helps in certain scenarios but is generally safe.
        compilerOptions.parseLiteralExpressionsAsConstants = true;
        return compilerOptions;
    }

    // ----------------------------------------------------------------------
    // Options / Diagnostics
    // ----------------------------------------------------------------------

    /**
     * Configures Java version settings for the ECJ compiler.
     */
    private static void buildVersionSettings(Map<String, String> settings, CompileOption compileOption) {
        String requestedVersion = compileOption.getJavaVersion();
        if (requestedVersion == null) {
            requestedVersion = CompilerOptions.VERSION_1_8;
        }

        // Normalize versions like "8" to "1.8" and validate against ECJ's supported versions.
        String normalizedVersion = normalizeAndValidateVersion(requestedVersion);

        settings.put(CompilerOptions.OPTION_Compliance, normalizedVersion);
        settings.put(CompilerOptions.OPTION_Source, normalizedVersion);
        settings.put(CompilerOptions.OPTION_TargetPlatform, normalizedVersion);
    }

    /**
     * Configures the source file encoding for the ECJ compiler.
     */
    private static void buildEncodingSettings(Map<String, String> settings, CompileOption compileOption) {
        if (compileOption.getEncoding() != null) {
            settings.put(CompilerOptions.OPTION_Encoding, compileOption.getEncoding());
        }
    }

    /**
     * Normalizes the Java version string and validates if it's supported by this version of ECJ.
     * If the version is not supported, it logs a warning and falls back to Java 1.8.
     *
     * @param version The requested Java version string (e.g., "8", "1.8", "11").
     * @return A normalized and validated version string compatible with ECJ (e.g., "1.8").
     */
    private static String normalizeAndValidateVersion(String version) {
        if (version == null) {
            return CompilerOptions.VERSION_1_8;
        }

        // Check if it's already in a format that ECJ understands (e.g., "1.8").
        if (SUPPORTED_ECJ_VERSIONS.contains(version)) {
            return version;
        }

        // Attempt to normalize other common formats (e.g., "8" -> "1.8").
        String normalized = normalizeVersionFormat(version);
        if (SUPPORTED_ECJ_VERSIONS.contains(normalized)) {
            return normalized;
        }

        // The bundled ECJ version (4.6.x) only supports up to Java 1.8.
        LOGGER.warn("ECJ compiler (4.6.x) does not support Java version '{}'; falling back to 1.8. Consider using JdkJavaCompiler for Java 9+ targets.", version);
        return CompilerOptions.VERSION_1_8;
    }

    /**
     * Normalizes common Java version formats (e.g., "8") into the "1.x" format required by ECJ.
     *
     * @param version The version string to normalize.
     * @return The normalized version string, or the original string if it cannot be normalized.
     */
    private static String normalizeVersionFormat(String version) {
        if (!VERSION_PATTERN.matcher(version).matches()) {
            // Return as-is if the pattern doesn't match to avoid breaking valid but unrecognized formats.
            return version;
        }

        // Handle modern single-digit versions like "8" -> "1.8", "7" -> "1.7".
        if (!version.startsWith("1.")) {
            try {
                int major = Integer.parseInt(version);
                if (major >= 5 && major <= 8) {
                    return "1." + major;
                }
            } catch (NumberFormatException e) {
                LOGGER.debug("Failed to parse version number: {}", version, e);
            }
        }

        return version;
    }

    /**
     * Renders a collection of ECJ compilation problems into a detailed, human-readable string.
     */
    private static String renderProblems(JavaSource js, String className,
                                         Collection<IProblem> problems,
                                         CompileOption compileOption) {
        StringBuilder sb = new StringBuilder();
        sb.append("ECJ compilation failed for class: ").append(className).append('\n');

        Charset charset = safeCharset(compileOption.getEncoding());
        String srcText;
        String[] sourceLines;

        try {
            if (js.getJavaSourceCode() != null) {
                srcText = js.getJavaSourceCode();
            } else if (js.getJavaSourceFile() != null) {
                byte[] bytes = Files.readAllBytes(js.getJavaSourceFile().toPath());
                srcText = new String(bytes, charset);
            } else {
                srcText = "";
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read source for diagnostics: {}", js.getJavaSourceFile(), e);
            srcText = "";
        }
        sourceLines = splitLines(srcText);

        for (IProblem p : problems) {
            if (p == null) {
                continue;
            }
            if (p.isError()) {
                sb.append(formatDiagnostic(p, js, srcText, sourceLines));
            } else {
                sb.append(formatDiagnostic(p, js, srcText, sourceLines));
            }
        }
        return sb.toString();
    }

    /**
     * Formats a single ECJ diagnostic problem, including the source line and a caret pointer
     * to highlight the error location.
     */
    private static String formatDiagnostic(IProblem problem,
                                           JavaSource js,
                                           String srcText,
                                           String[] sourceLines) {
        StringBuilder sb = new StringBuilder();

        String fileDisplay;
        char[] origin = problem.getOriginatingFileName();
        if (origin != null && origin.length > 0) {
            fileDisplay = new String(origin);
        } else if (js.getJavaSourceFile() != null) {
            fileDisplay = js.getJavaSourceFile().getAbsolutePath();
        } else {
            fileDisplay = "<memory:" + js.getTargetFullClassName() + ">";
        }

        int lineNo = problem.getSourceLineNumber();
        int start = problem.getSourceStart();
        int end = problem.getSourceEnd();
        if (start < 0) {
            start = 0;
        }
        if (end < start) {
            end = start;
        }

        int lineStart = (srcText != null && !srcText.isEmpty())
                ? nthLineStart(srcText, lineNo)
                : 0;
        int colStart = Math.max(1, start - lineStart + 1);
        int colEnd = Math.max(colStart, end - lineStart + 1);

        String severity = problem.isError() ? "ERROR" : (problem.isWarning() ? "WARN" : "INFO");
        sb.append('[').append(severity).append("] ")
                .append(fileDisplay).append(':').append(lineNo).append(':').append(colStart)
                .append("  ").append(problem.getMessage()).append('\n');

        if (lineNo > 0 && lineNo <= (sourceLines != null ? sourceLines.length : 0)) {
            String line = sourceLines[lineNo - 1];
            sb.append("    ").append(line).append('\n');

            int markerLen = Math.max(1, Math.min(Math.max(0, line.length() - (colStart - 1)),
                    Math.max(1, colEnd - colStart + 1)));
            sb.append("    ")
                    .append(StringUtils.repeat(' ', Math.max(0, colStart - 1)))
                    .append('^')
                    .append(markerLen > 1 ? StringUtils.repeat('~', markerLen - 1) : "")
                    .append('\n');
        }

        sb.append("    [problem id: ").append(problem.getID()).append("]").append('\n');
        return sb.toString();
    }


    /**
     * Safely resolves a charset name, falling back to UTF-8 if the name is invalid.
     */
    private static Charset safeCharset(String name) {
        if (name == null || name.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * Splits a string into lines, preserving trailing empty lines.
     * Uses a regex that is compatible with older Java versions.
     */
    private static String[] splitLines(String s) {
        // The "\\R" regex for line breaks was introduced in Java 8.
        return s.split("\\R", -1);
    }

    /**
     * Finds the starting character index of a given line number (1-based) in a string.
     */
    private static int nthLineStart(String src, int lineNo) {
        // Returns the start index of the (1-based) line number.
        int count = 1, idx = 0;
        while (count < lineNo && idx >= 0) {
            int p = src.indexOf('\n', idx);
            if (p < 0) {
                return src.length();
            }
            idx = p + 1;
            count++;
        }
        return Math.max(0, idx);
    }

    /**
     * Converts an ECJ compound name (char[][]) to a fully qualified class name string.
     */
    private static String compoundNameToFqn(char[][] compoundName) {
        if (compoundName == null || compoundName.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < compoundName.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(new String(compoundName[i]));
        }
        return sb.toString();
    }

    @Override
    public void compile(JavaSource javaSource, ClassOutput classOutput, CompileOption compileOption) {
        Objects.requireNonNull(javaSource, "javaSource must not be null");
        Objects.requireNonNull(classOutput, "classOutput must not be null");
        Objects.requireNonNull(compileOption, "compileOption must not be null");

        final String targetClassName = Objects.requireNonNull(
                javaSource.getTargetFullClassName(), "Target full class name must not be null");

        final List<IProblem> problems = new ArrayList<>();

        // The NameEnvironment is ECJ's abstraction for a classpath. It's responsible for
        // finding type definitions (source or binary) during compilation.
        final INameEnvironment env = new ClasspathNameEnvironment(
                compileOption.getParentClassLoader(),
                compileOption.getClasspath(),
                javaSource
        );

        // The CompilerRequestor is a callback that receives the results of the compilation,
        // including compiled class files and any problems encountered.
        final ICompilerRequestor requestor = result -> {
            if (result.hasProblems()) {
                for (IProblem p : result.getProblems()) {
                    if (p != null && p.isError()) {
                        problems.add(p);
                    }
                }
            }
            if (problems.isEmpty()) {
                try {
                    for (ClassFile classFile : result.getClassFiles()) {
                        String className = compoundNameToFqn(classFile.getCompoundName());
                        classOutput.writeClass(className, classFile.getBytes());
                    }
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                } catch (Exception e) {
                    throw new CompileFlowException.SystemException(
                            ErrorCode.CF_COMPILE_002,
                            "Failed to write compiled class bytes",
                            e
                    );
                }
            }
        };

        // The CompilationUnit wraps our Java source code in a way that ECJ can understand.
        final ICompilationUnit[] units = {new CompilationUnit(javaSource)};
        final CompilerOptions options = getCompilerOptions(compileOption);

        // The main Compiler instance.
        final Compiler compiler = new Compiler(
                env,
                DefaultErrorHandlingPolicies.proceedWithAllProblems(),
                options,
                requestor,
                new DefaultProblemFactory(Locale.getDefault())
        );

        try {
            compiler.compile(units);
        } catch (UncheckedIOException e) {
            // Unwrap IO exceptions that may have been thrown by the requestor callback.
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_COMPILE_002,
                    "Failed to write compiled class file",
                    e.getCause()
            );
        } finally {
            try {
                // It's crucial to clean up the environment to release resources, especially file handles for JARs.
                env.cleanup();
            } catch (Throwable e) {
                LOGGER.debug("Failed to cleanup name environment", e);
            }
        }

        if (!problems.isEmpty()) {
            String err = renderProblems(javaSource, targetClassName, problems, compileOption);
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_COMPILE_002,
                    err,
                    null
            );
        }
    }

    // ----------------------------------------------------------------------
    // ICompilationUnit
    // ----------------------------------------------------------------------

    /**
     * An adapter that wraps a {@link JavaSource} object to conform to the ECJ
     * {@link ICompilationUnit} interface, which represents a single source file
     * to be compiled.
     */
    private static class CompilationUnit implements ICompilationUnit {
        private final JavaSource javaSource;

        CompilationUnit(JavaSource javaSource) {
            this.javaSource = javaSource;
        }

        @Override
        public char[] getFileName() {
            File f = javaSource.getJavaSourceFile();
            return (f != null ? f.getAbsolutePath() : "<memory>").toCharArray();
        }

        @Override
        public char[] getContents() {
            return javaSource.getJavaSourceCode().toCharArray();
        }

        @Override
        public char[] getMainTypeName() {
            String full = javaSource.getTargetFullClassName();
            int dot = full.lastIndexOf('.');
            return (dot >= 0 ? full.substring(dot + 1) : full).toCharArray();
        }

        @Override
        public char[][] getPackageName() {
            String full = javaSource.getTargetFullClassName();
            int dot = full.lastIndexOf('.');
            if (dot > 0) {
                String pkg = full.substring(0, dot);
                String[] parts = pkg.split("\\.");
                char[][] arr = new char[parts.length][];
                for (int i = 0; i < parts.length; i++) {
                    arr[i] = parts[i].toCharArray();
                }
                return arr;
            }
            return new char[0][];
        }

        @Override
        public boolean ignoreOptionalProblems() {
            return false;
        }
    }

    // ----------------------------------------------------------------------
    // Name Environment: parent CL + directory/jar classpath
    // ----------------------------------------------------------------------

    /**
     * An implementation of ECJ's {@link INameEnvironment} that resolves type
     * and package names by searching a hierarchy of locations:
     * <ol>
     *   <li>The current compilation unit being compiled.</li>
     *   <li>A cache of previously found types.</li>
     *   <li>A parent {@link ClassLoader}.</li>
     *   <li>A list of classpath entries (directories or JAR files).</li>
     * </ol>
     * This class is responsible for loading the bytecode of dependency classes
     * that the compiler needs to perform its analysis.
     */
    private static class ClasspathNameEnvironment implements INameEnvironment {
        private final ClassLoader parentClassLoader;
        private final List<ClasspathEntry> entries;
        private final JavaSource currentCompilationUnit;

        private final Map<String, NameEnvironmentAnswer> typeCache = new HashMap<>();
        private final Map<String, Boolean> pkgCache = new HashMap<>();

        ClasspathNameEnvironment(ClassLoader parentClassLoader, List<String> classpath, JavaSource currentUnit) {
            this.parentClassLoader = (parentClassLoader != null)
                    ? parentClassLoader : EcJavaCompiler.class.getClassLoader();
            this.currentCompilationUnit = currentUnit;
            this.entries = buildEntries(classpath);
        }

        private static List<ClasspathEntry> buildEntries(List<String> classpath) {
            if (classpath == null || classpath.isEmpty()) {
                return Collections.emptyList();
            }
            List<ClasspathEntry> list = new ArrayList<>();
            for (String p : classpath) {
                if (p == null || p.trim().isEmpty()) {
                    continue;
                }
                File f = new File(p);
                if (!f.exists()) {
                    continue;
                }
                if (f.isDirectory()) {
                    list.add(new DirEntry(f));
                } else {
                    String name = f.getName().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".jar") || name.endsWith(".zip")) {
                        try {
                            list.add(new JarEntry(f));
                        } catch (IOException e) {
                            LOGGER.warn("Failed to open classpath jar: {}", f, e);
                        }
                    } else {
                        // treat as directory fallback
                        list.add(new DirEntry(f));
                    }
                }
            }
            return Collections.unmodifiableList(list);
        }

        private static byte[] readAll(InputStream is) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }

        @Override
        public NameEnvironmentAnswer findType(char[][] compoundTypeName) {
            return findType(CharOperation.toString(compoundTypeName));
        }

        @Override
        public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
            return findType(CharOperation.toString(packageName) + "." + new String(typeName));
        }

        /**
         * The core type resolution logic. Searches for a class definition in the
         * configured locations and returns it in a format ECJ can consume.
         */
        private NameEnvironmentAnswer findType(String className) {
            // 1. Check if it's the class we are currently compiling.
            if (className.equals(currentCompilationUnit.getTargetFullClassName())) {
                return new NameEnvironmentAnswer(new CompilationUnit(currentCompilationUnit), null);
            }

            // 2. Check our cache to avoid redundant lookups.
            NameEnvironmentAnswer cached = typeCache.get(className);
            if (cached != null || typeCache.containsKey(className)) {
                return cached;
            }

            final String resourceName = className.replace('.', '/') + FileConstants.CLASS_FILE_SUFFIX;

            // 3. Try to load from the parent ClassLoader.
            try (InputStream is = parentClassLoader.getResourceAsStream(resourceName)) {
                if (is != null) {
                    byte[] bytes = readAll(is);
                    ClassFileReader reader = new ClassFileReader(bytes, className.toCharArray(), true);
                    NameEnvironmentAnswer ans = new NameEnvironmentAnswer(reader, null);
                    typeCache.put(className, ans);
                    return ans;
                }
            } catch (Throwable t) {
                LOGGER.warn("ClassLoader lookup failed for {}", className, t);
            }

            // 4. Search through the explicit classpath entries (directories and JARs).
            for (ClasspathEntry e : entries) {
                try {
                    byte[] bytes = e.readClass(resourceName);
                    if (bytes != null) {
                        ClassFileReader reader = new ClassFileReader(bytes, className.toCharArray(), true);
                        NameEnvironmentAnswer ans = new NameEnvironmentAnswer(reader, null);
                        typeCache.put(className, ans);
                        return ans;
                    }
                } catch (Throwable ex) {
                    LOGGER.warn("Classpath lookup failed for {} in {}", className, e, ex);
                }
            }

            // Type not found, cache the negative result.
            typeCache.put(className, null);
            return null;
        }

        @Override
        public boolean isPackage(char[][] parentPackageName, char[] packageName) {
            String q = CharOperation.toString(parentPackageName);
            if (!q.isEmpty()) {
                q += ".";
            }
            q += new String(packageName);
            return isPackage(q);
        }

        /**
         * Determines if a fully qualified name refers to a package.
         * It uses a heuristic: if a class file with that name exists, it's not a package.
         * Otherwise, it checks if any classpath entry contains a directory corresponding
         * to the package path.
         */
        private boolean isPackage(String qualified) {
            Boolean cached = pkgCache.get(qualified);
            if (cached != null) {
                return cached;
            }

            String pkgPath = qualified.replace('.', '/');

            // If a .class file with this name exists, then it cannot be a package.
            String classRes = pkgPath + FileConstants.CLASS_FILE_SUFFIX;

            // Check parent ClassLoader first.
            try (InputStream is = parentClassLoader.getResourceAsStream(classRes)) {
                if (is != null) {
                    pkgCache.put(qualified, false);
                    return false;
                }
            } catch (IOException ignore) {
            }

            // Check classpath entries.
            for (ClasspathEntry e : entries) {
                try {
                    if (e.hasClass(classRes)) {
                        pkgCache.put(qualified, false);
                        return false;
                    }
                    if (e.containsPackage(pkgPath)) {
                        pkgCache.put(qualified, true);
                        return true;
                    }
                } catch (Throwable ex) {
                    // Ignore errors on this entry and continue.
                }
            }

            // Default heuristic: if we can't find a class, assume it might be a package.
            // ECJ can handle cases where this is wrong.
            pkgCache.put(qualified, true);
            return true;
        }

        // ---- helpers ----

        @Override
        public void cleanup() {
            for (ClasspathEntry e : entries) {
                try {
                    e.closeQuietly();
                } catch (Throwable ignore) {
                }
            }
            typeCache.clear();
            pkgCache.clear();
        }

        /**
         * An abstraction for a single entry on the classpath, which can be
         * either a directory or a JAR file.
         */
        private interface ClasspathEntry {
            byte[] readClass(String resourceName) throws IOException;

            boolean hasClass(String resourceName) throws IOException;

            boolean containsPackage(String pkgPath) throws IOException;

            void closeQuietly();
        }

        /**
         * A classpath entry representing a directory.
         */
        private static final class DirEntry implements ClasspathEntry {
            private final File root;

            DirEntry(File root) {
                this.root = root;
            }

            @Override
            public byte[] readClass(String resourceName) throws IOException {
                File f = new File(root, resourceName);
                if (f.isFile()) {
                    return Files.readAllBytes(f.toPath());
                }
                return null;
            }

            @Override
            public boolean hasClass(String resourceName) {
                return new File(root, resourceName).isFile();
            }

            @Override
            public boolean containsPackage(String pkgPath) {
                File d = new File(root, pkgPath);
                return d.isDirectory();
            }

            @Override
            public void closeQuietly() { /* no-op */ }

            @Override
            public String toString() {
                return "Dir(" + root + ")";
            }
        }

        /**
         * A classpath entry representing a JAR or ZIP file.
         */
        private static final class JarEntry implements ClasspathEntry {
            private final File jarFile;
            private final ZipFile zip;

            JarEntry(File jarFile) throws IOException {
                this.jarFile = jarFile;
                // Since Java 7, ZipFile defaults to UTF-8 for JARs, which is correct.
                this.zip = new ZipFile(jarFile, ZipFile.OPEN_READ, StandardCharsets.UTF_8);
            }

            @Override
            public byte[] readClass(String resourceName) throws IOException {
                ZipEntry e = zip.getEntry(resourceName);
                if (e == null) {
                    return null;
                }
                try (InputStream is = zip.getInputStream(e)) {
                    return ClasspathNameEnvironment.readAll(is);
                }
            }

            @Override
            public boolean hasClass(String resourceName) {
                return zip.getEntry(resourceName) != null;
            }

            @Override
            public boolean containsPackage(String pkgPath) {
                String prefix = pkgPath.endsWith("/") ? pkgPath : (pkgPath + "/");
                // A JAR might not have explicit directory entries. The presence of any
                // file starting with the package path prefix implies the package exists.
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.getName().startsWith(prefix)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void closeQuietly() {
                try {
                    zip.close();
                } catch (IOException e) {
                    LOGGER.debug("Failed to close jar file: {}", jarFile, e);
                }
            }

            @Override
            public String toString() {
                return "Jar(" + jarFile + ")";
            }
        }
    }
}
