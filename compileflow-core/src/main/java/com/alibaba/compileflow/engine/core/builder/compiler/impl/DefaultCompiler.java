/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.builder.compiler.impl;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.compiler.*;
import com.alibaba.compileflow.engine.core.builder.compiler.constants.FileConstants;
import com.alibaba.compileflow.engine.core.classloader.ProcessClassLoaderResolver;
import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.core.infrastructure.utils.JavaIdentifierUtils;
import com.google.common.util.concurrent.Striped;
import io.github.classgraph.ClassGraph;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * This compiler orchestrates the end-to-end process of turning Java source code into a
 * loadable {@link Class}. It performs the following key steps:
 * <ol>
 *   <li>Persists the source code to a temporary file in a local directory ({@code .compileflow-classes}).</li>
 *   <li>Uses a striped lock to ensure that only one thread compiles the same class at a time,
 *       preventing race conditions while allowing concurrent compilation of different classes.</li>
 *   <li>Extracts the classpath from the provided classloader to supply to the compilationTask Java compiler.</li>
 *   <li>Delegates the actual Java compilation to a {@link JavaCompiler} extension (e.g., {@link EcJavaCompiler}).</li>
 *   <li>Uses a {@link FlowClassLoaderFactory} extension to create a classloader for the compiled bytecode.</li>
 *   <li>Loads and returns the compiled class.</li>
 * </ol>
 *
 * @author yusu
 */
public class DefaultCompiler implements com.alibaba.compileflow.engine.core.builder.compiler.Compiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCompiler.class);
    private static final String FLOW_COMPILED_CLASS_DIR = System.getProperty("user.dir") + File.separator
            + ".compileflow-classes" + File.separator;

    private static final int MAX_FQN_LENGTH = 500;
    private static final int STRIPED_LOCK_SIZE = 128;

    private final File compileOutputDir;

    // A striped lock to ensure that only one thread can compile the same flow at a time.
    private final Striped<ReadWriteLock> stripedLock = Striped.lazyWeakReadWriteLock(STRIPED_LOCK_SIZE);

    public DefaultCompiler() {
        try {
            compileOutputDir = new File(FLOW_COMPILED_CLASS_DIR);
            Files.createDirectories(compileOutputDir.toPath());
            LOGGER.info("Initialized persistent compile directory: {}", this.compileOutputDir.getAbsolutePath());
        } catch (Exception e) {
            throw new CompileFlowException.ConfigurationException(
                    ErrorCode.CF_CONFIG_001,
                    "Failed to create base compile directory",
                    e
            );
        }
    }

    @Override
    public Class<?> compileJavaCode(String fullClassName, String sourceCode, ClassLoader classLoader) {
        Lock writeLock = stripedLock.get(fullClassName).writeLock();
        writeLock.lock();
        try {
            long startTime = System.currentTimeMillis();
            LOGGER.debug("Starting compilation: class={}", fullClassName);

            File sourceFile = writeJavaFile(compileOutputDir, fullClassName, sourceCode);
            JavaSource javaSource = JavaSource.of(sourceFile, sourceCode, fullClassName);

            ClassLoader parentClassLoader = ProcessClassLoaderResolver
                    .resolveEffectiveClassLoader(classLoader, DefaultCompiler.class);

            // Extract the classpath to provide dependencies to the compiler.
            List<String> classPath = extractClasspath(parentClassLoader);
            CompileOption compileOption = new CompileOption();
            compileOption.setClasspath(classPath);
            compileOption.setParentClassLoader(parentClassLoader);

            ClassOutput classOutput = ExtensionInvoker.getInstance().invokeFirst(
                    ClassOutput.EXT_CLASS_OUTPUT_CODE, (ClassOutput out) -> out);
            classOutput.open(fullClassName, compileOption);

            // Delegate to the registered JavaCompiler extension (e.g., EcJavaCompiler).
            ExtensionInvoker.getInstance().invokeFirst(
                    JavaCompiler.EXT_COMPILE_CODE, (JavaCompiler e) -> e.compile(javaSource, classOutput, compileOption));
            CompiledArtifact artifact = classOutput.finish();

            // Use a factory to create a classloader for the compiled artifact.
            ClassLoader finalClassLoader = ExtensionInvoker.getInstance().invokeFirst(
                    FlowClassLoaderFactory.EXT_FLOW_CLASS_LOADER_CODE,
                    (FlowClassLoaderFactory f) -> f.getFlowClassLoader(artifact, parentClassLoader));
            if (finalClassLoader == null) {
                LOGGER.debug("No FlowClassLoaderFactory extension found, using default FlowUrlClassLoader.");
                URL[] urls = artifact.getUrls();
                finalClassLoader = new FlowUrlClassLoader(urls, parentClassLoader);
            }

            Class<?> compiledClass = finalClassLoader.loadClass(fullClassName);
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Compilation successful: class={}, durationMs={}", fullClassName, duration);
            return compiledClass;
        } catch (Exception e) {
            LOGGER.error("Compilation failed: class={}", fullClassName, e);
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_COMPILE_002,
                    "Failed to compile java code for class: " + fullClassName,
                    e
            );
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Writes the Java source code to a file within the compilation output directory.
     * This method also performs security checks to prevent path traversal attacks.
     *
     * @param baseDir       The base directory for compiled classes.
     * @param fullClassName The fully qualified name of the class.
     * @param javaCode      The source code to write.
     * @return The {@link File} object for the written source file.
     */
    private File writeJavaFile(File baseDir, String fullClassName, String javaCode) {
        validateClassName(fullClassName);
        try {
            Path rel = Paths.get(fullClassName.replace('.', File.separatorChar) + FileConstants.JAVA_FILE_SUFFIX);
            if (rel.isAbsolute()) {
                throw new IllegalArgumentException("Absolute path not allowed for class output: " + rel);
            }
            rel = rel.normalize();

            Path base = baseDir.toPath().toAbsolutePath().normalize();
            Path target = base.resolve(rel).normalize();
            if (!target.startsWith(base)) {
                throw new IllegalArgumentException("Resolved path escapes base directory: " + target);
            }

            Files.createDirectories(target.getParent());
            try (PrintWriter printWriter = new PrintWriter(new FileOutputStream(target.toFile()))) {
                printWriter.write(javaCode);
                printWriter.flush();
            }
            return target.toFile();
        } catch (Exception e) {
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_RESOURCE_003,
                    "Failed to write java source file.",
                    e
            );
        }
    }

    /**
     * Validates that the provided string is a valid and safe Java fully qualified class name.
     *
     * @param fullClassName The class name to validate.
     */
    private void validateClassName(String fullClassName) {
        if (StringUtils.isBlank(fullClassName)) {
            throw new IllegalArgumentException("Class name cannot be blank or null.");
        }
        if (fullClassName.length() > MAX_FQN_LENGTH) {
            throw new IllegalArgumentException("Class name too long: " + fullClassName.length());
        }
        if (fullClassName.indexOf('/') >= 0 || fullClassName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Class name must be a Java FQN using dots only.");
        }
        String[] parts = fullClassName.split("\\.");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid class name: " + fullClassName);
        }
        for (String p : parts) {
            if (!JavaIdentifierUtils.isJavaIdentifier(p)) {
                throw new IllegalArgumentException("Invalid Java identifier segment: '" + p + "' in " + fullClassName);
            }
        }
    }

    /**
     * Extracts the classpath from the given {@link ClassLoader} to provide as a dependency
     * list to the compilationTask Java compiler. It uses ClassGraph for robust classpath scanning
     * and falls back to the 'java.class.path' system property if needed.
     *
     * @param classLoader The classloader to scan.
     * @return A list of classpath entry paths.
     */
    private List<String> extractClasspath(ClassLoader classLoader) {
        Set<String> classpath = new LinkedHashSet<>();

        try {
            new ClassGraph()
                    .overrideClassLoaders(classLoader)
                    .getClasspathURIs()
                    .stream()
                    .map(URI::getPath)
                    .filter(Objects::nonNull)
                    .forEach(classpath::add);
        } catch (Exception e) {
            LOGGER.warn("Failed to scan classpath using ClassGraph, falling back to system property 'java.class.path'.", e);
        }

        String systemClasspath = System.getProperty("java.class.path");
        if (systemClasspath != null && !systemClasspath.isEmpty()) {
            classpath.addAll(Arrays.asList(systemClasspath.split(File.pathSeparator)));
        }

        LOGGER.debug("Compiler classpath constructed with {} entries.", classpath.size());
        return new ArrayList<>(classpath);
    }
}
