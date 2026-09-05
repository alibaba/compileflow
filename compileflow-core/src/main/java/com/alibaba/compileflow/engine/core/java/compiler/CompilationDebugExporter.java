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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes optional compilation debug artifacts when configured.
 *
 * @author yusu
 */
final class CompilationDebugExporter {
    private static final Set<String> RESERVED_METADATA_KEYS =
            Set.of("primary-class", "source-sha256", "source-count", "java-release", "debug-symbols", "source-file");
    private final Path outputDirectory;
    private final boolean bytecodeEnabled;

    CompilationDebugExporter(JavaDiagnosticsConfig config) {
        this.outputDirectory = config.getDebugOutputDirectory();
        this.bytecodeEnabled = config.isDebugBytecodeEnabled();
    }

    private static String escapeProperty(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '=', ':' -> escaped.append('\\').append(character);
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static String portableRelativePath(Path root, Path target) {
        return root.relativize(target).toString().replace('\\', '/');
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Debug artifact has no parent directory");
        }
        Path temporary = Files.createTempFile(parent, ".compileflow-", ".tmp");
        try {
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path resolveClassPath(Path root, String className, String suffix) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        Path target = base.resolve(className.replace('.', '/') + suffix).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Resolved debug artifact escapes its output directory");
        }
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Resolved debug artifact has no parent directory");
        }
        Files.createDirectories(parent);
        return target;
    }

    private static CompileFlowException.ConfigurationException exportFailure(String message, Throwable failure) {
        CompileFlowException.ConfigurationException exception =
                new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001, message, failure);
        exception.withContext("failureType", failure.getClass().getName());
        return exception;
    }

    DebugArtifacts open(JavaSource source, JavaCompileOptions options) {
        return open(source, List.of(), options);
    }

    DebugArtifacts open(JavaSource source, List<JavaSource> additionalSources, JavaCompileOptions options) {
        if (outputDirectory == null) {
            return DebugArtifacts.disabled();
        }
        try {
            Files.createDirectories(outputDirectory);
            Path realOutputDirectory = outputDirectory.toRealPath();
            if (!Files.isDirectory(realOutputDirectory)) {
                throw new IOException("Configured debug output is not a directory");
            }
            String sourceDigest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(source
                .getJavaSourceCode()
                .getBytes(StandardCharsets.UTF_8));
            Path sourcePath =
                    resolveClassPath(realOutputDirectory.resolve("source"), source.getTargetFullClassName(), ".java");
            writeAtomically(sourcePath, source.getJavaSourceCode().getBytes(StandardCharsets.UTF_8));
            List<JavaSource> companions = List.copyOf(additionalSources);
            for (JavaSource companion : companions) {
                Path companionPath =
                        resolveClassPath(realOutputDirectory.resolve("source"), companion.getTargetFullClassName(),
                                ".java");
                writeAtomically(companionPath, companion.getJavaSourceCode().getBytes(StandardCharsets.UTF_8));
            }

            Path metadataPath =
                    resolveClassPath(realOutputDirectory.resolve("metadata"), source.getTargetFullClassName(),
                            ".properties");
            StringBuilder metadata = new StringBuilder()
                .append("primary-class=")
                .append(escapeProperty(source.getTargetFullClassName()))
                .append('\n')
                .append("source-sha256=")
                .append(sourceDigest)
                .append('\n')
                .append("source-count=")
                .append(companions.size() + 1)
                .append('\n')
                .append("java-release=")
                .append(JavaCompileOptions.JAVA_RELEASE)
                .append('\n')
                .append("debug-symbols=")
                .append(options.debugSymbols().name())
                .append('\n')
                .append("source-file=")
                .append(escapeProperty(portableRelativePath(realOutputDirectory, sourcePath)))
                .append('\n');
            source
                .getDebugMetadata()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (RESERVED_METADATA_KEYS.contains(entry.getKey())) {
                        throw new IllegalArgumentException("Reserved debug metadata key: " + entry.getKey());
                    }
                    metadata.append(escapeProperty(entry.getKey())).append('=').append(escapeProperty(entry.getValue())).append(
                            '\n');
                });
            writeAtomically(metadataPath, metadata.toString().getBytes(StandardCharsets.UTF_8));
            return new DebugArtifacts(realOutputDirectory, source.getTargetFullClassName(), bytecodeEnabled);
        } catch (IOException | RuntimeException e) {
            throw exportFailure("Failed to export compilation debug source", e);
        }
    }

    static final class DebugArtifacts {
        private static final DebugArtifacts DISABLED = new DebugArtifacts(null, null, false);
        private final Path outputDirectory;
        private final String artifactId;
        private final boolean bytecodeEnabled;

        private DebugArtifacts(Path outputDirectory, String artifactId, boolean bytecodeEnabled) {
            this.outputDirectory = outputDirectory;
            this.artifactId = outputDirectory == null ? null : requireArtifactId(artifactId);
            this.bytecodeEnabled = bytecodeEnabled;
        }

        static DebugArtifacts disabled() {
            return DISABLED;
        }

        private static String requireArtifactId(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Debug artifact id must not be blank");
            }
            return value;
        }

        void exportBytecode(CompiledClasses compiledClasses) {
            if (outputDirectory == null || !bytecodeEnabled) {
                return;
            }
            try {
                for (Map.Entry<String, byte[]> entry : compiledClasses.copyClassBytes().entrySet()) {
                    Path classPath = resolveClassPath(outputDirectory.resolve("classes"), entry.getKey(), ".class");
                    writeAtomically(classPath, entry.getValue());
                }
            } catch (IOException | RuntimeException e) {
                throw exportFailure("Failed to export compilation debug bytecode", e);
            }
        }

        String getArtifactId() {
            return artifactId;
        }

        Path getOutputDirectory() {
            return outputDirectory;
        }
    }
}
