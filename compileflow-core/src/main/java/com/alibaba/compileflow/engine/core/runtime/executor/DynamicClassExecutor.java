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
package com.alibaba.compileflow.engine.core.runtime.executor;

import com.alibaba.compileflow.engine.config.ProcessPropertyProvider;
import com.alibaba.compileflow.engine.core.builder.compiler.Compiler;
import com.alibaba.compileflow.engine.core.builder.compiler.impl.DefaultCompiler;
import com.alibaba.compileflow.engine.core.classloader.ClassLoaderManager;
import com.alibaba.compileflow.engine.core.executor.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ExecutorUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.Weigher;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine-scoped dynamic class compiler and executor with caching and
 * single-flight compilation.
 *
 * @author yusu
 */
public class DynamicClassExecutor implements AutoCloseable {

    public static final String DYNAMIC_CLASS_PACKAGE_NAME = "compileflow.dynamic";
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicClassExecutor.class);
    private static final Pattern PUBLIC_CLASS_RX =
            Pattern.compile("\\bpublic\\s+(?:\\w+\\s+)*class\\s+([A-Za-z$_][A-Za-z0-9$_]*)\\b");
    private static final Pattern PACKAGE_RX =
            Pattern.compile("^\\s*package\\s+([A-Za-z_][\\w\\.]*)\\s*;", Pattern.MULTILINE);
    private static final long COMPILE_TIMEOUT_SECONDS = ProcessPropertyProvider.Compilation.getTimeoutSeconds();

    private static final int DYNAMIC_CACHE_MAX_SIZE = ProcessPropertyProvider.Cache.getDynamicClassMaxSize();
    private static final int DYNAMIC_CACHE_EXPIRE_MINUTES = ProcessPropertyProvider.Cache.getDynamicClassExpireMinutes();
    private static final int FUTURES_MAX_SIZE = ProcessPropertyProvider.Cache.getDynamicFuturesMaxSize();

    private static final int DIGEST_CACHE_MAX_WEIGHT = ProcessPropertyProvider.Cache.getDynamicDigestMaxWeight();
    private static final int DIGEST_CACHE_EXPIRE_MINUTES = ProcessPropertyProvider.Cache.getDynamicDigestExpireMinutes();
    private static final int DIGEST_CACHE_SKIP_IF_LENGTH_EXCEEDS = ProcessPropertyProvider.Cache.getDynamicDigestSkipThreshold();

    private final ClassLoaderManager classLoaderManager;
    private final ProcessEngineExecutors processEngineExecutors;
    /**
     * Multi-version cache of compiled classes: key = className@clId@digest.
     */
    private final Cache<String, Entry> compiledClassCache;
    /**
     * Single-flight map of in-flight compilations: key = className@clId#digest.
     */
    private final Cache<String, Future<Class<?>>> inflightCompilationFutures;
    private final Compiler javaSourceCompiler = new DefaultCompiler();
    private final Cache<String, String> sourceCodeDigestCache = CacheBuilder.newBuilder()
            .maximumWeight(DIGEST_CACHE_MAX_WEIGHT)
            .weigher((Weigher<String, String>) (key, value) -> key != null ? key.length() : 0)
            .expireAfterAccess(DIGEST_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    /**
     * Creates a new DynamicClassExecutor for the given engine.
     *
     * @param classLoaderManager     ClassLoader lifecycle manager
     * @param processEngineExecutors engine thread pools
     */
    public DynamicClassExecutor(ClassLoaderManager classLoaderManager, ProcessEngineExecutors processEngineExecutors) {
        this.classLoaderManager = Objects.requireNonNull(classLoaderManager, "classLoaderManager cannot be null");
        this.processEngineExecutors = Objects.requireNonNull(processEngineExecutors, "processEngineExecutors cannot be null");

        this.compiledClassCache = CacheBuilder.newBuilder()
                .maximumSize(DYNAMIC_CACHE_MAX_SIZE)
                .expireAfterAccess(DYNAMIC_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, Entry>) n -> {
                    Entry val = n.getValue();
                    if (val != null && val.clazz != null) {
                        LOGGER.debug("Evicting dynamic class cache entry: key={}, cause={}", n.getKey(), n.getCause());
                        if (this.classLoaderManager != null) {
                            this.classLoaderManager.unregisterClassLoader(val.identifier);
                        }
                    }
                })
                .build();

        this.inflightCompilationFutures = CacheBuilder.newBuilder()
                .maximumSize(FUTURES_MAX_SIZE)
                .expireAfterWrite(COMPILE_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
                .removalListener((RemovalListener<String, Future<Class<?>>>) n -> {
                    Future<Class<?>> f = n.getValue();
                    if (f != null && !f.isDone()) {
                        f.cancel(true);
                        LOGGER.warn("Evicted and cancelled an in-flight compilation future: key={}, cause={}",
                                n.getKey(), n.getCause());
                    } else {
                        LOGGER.debug("Removed completed or inactive compilation future from cache: key={}, cause={}",
                                n.getKey(), n.getCause());
                    }
                })
                .build();

        LOGGER.info("DynamicClassExecutor initialized.");
    }

    private static String clIdentity(ClassLoader classLoader) {
        if (classLoader == null) {
            return "null";
        }
        return classLoader.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(classLoader));
    }

    private static String sha256Hex(String content) {
        return DigestUtils.sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String rewriteClassName(String originalClassName, String uniqueClassName, String sourceCode) {
        String normalizedSource = normalizePackage(sourceCode, DYNAMIC_CLASS_PACKAGE_NAME);

        Matcher classDeclarationMatcher = PUBLIC_CLASS_RX.matcher(normalizedSource);
        if (!classDeclarationMatcher.find()) {
            throw new IllegalArgumentException("No 'public class' declaration found in source");
        }

        String foundClassName = classDeclarationMatcher.group(1);
        if (!Objects.equals(foundClassName, originalClassName)) {
            throw new IllegalArgumentException("Top-level public class name mismatch: source='" + foundClassName
                    + "', param='" + originalClassName + "'");
        }

        int classNameStart = classDeclarationMatcher.start(1);
        int classNameEnd = classDeclarationMatcher.end(1);
        String sourceWithReplacedClassName = normalizedSource.substring(0, classNameStart) + uniqueClassName + normalizedSource.substring(classNameEnd);
        return renameClassEverywhere(sourceWithReplacedClassName, originalClassName, uniqueClassName);
    }

    private static String extractClassNameFromSource(String sourceCode) {
        Matcher matcher = PUBLIC_CLASS_RX.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("No 'public class' declaration found in source");
    }

    private static String normalizePackage(String sourceCode, String targetPackageName) {
        Matcher packageMatcher = PACKAGE_RX.matcher(sourceCode);
        if (packageMatcher.find()) {
            return packageMatcher.replaceFirst("package " + targetPackageName + ";");
        }
        return "package " + targetPackageName + ";\n" + sourceCode;
    }

    private static String renameClassEverywhere(String sourceCode, String oldClassName, String newClassName) {
        String modifiedSource = sourceCode;

        // Replace constructor invocations: OldName(
        modifiedSource = Pattern.compile("(?<![\\w$])" + Pattern.quote(oldClassName) + "\\s*\\(")
                .matcher(modifiedSource).replaceAll(newClassName + "(");

        // Replace qualified/type/static refs: OldName followed by ., <, (, or whitespace+
        modifiedSource = Pattern.compile("(?<![\\w$])" + Pattern.quote(oldClassName) + "(?=\\s*[\\.<(])")
                .matcher(modifiedSource).replaceAll(newClassName);

        // Replace this/super qualifiers
        modifiedSource = Pattern.compile("(?<![\\w$])" + Pattern.quote(oldClassName) + "(?=\\s*\\.(?:this|super)\\b)")
                .matcher(modifiedSource).replaceAll(newClassName);

        // Fallback: whole-word replacement to cover return/field/local type references
        modifiedSource = Pattern.compile("(?<![\\w$])" + Pattern.quote(oldClassName) + "(?![\\w$])")
                .matcher(modifiedSource).replaceAll(newClassName);

        return modifiedSource;
    }

    /**
     * Executes a method in a dynamically compiled Java class.
     */
    public <T> T execute(String sourceCode,
                         String methodName,
                         Class<?>[] paramTypes,
                         Object... args) throws Exception {
        Objects.requireNonNull(sourceCode, "sourceCode");
        if (StringUtils.isBlank(methodName)) {
            throw new IllegalArgumentException("methodName must not be blank");
        }

        final String sourceDigest = computeDigestWithCache(sourceCode);
        final String shortDigest = sourceDigest.length() >= 16 ? sourceDigest.substring(0, 16) : sourceDigest;

        final ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
        final String clId = clIdentity(contextCl);
        final String className = extractClassNameFromSource(sourceCode);

        final String baseKey = className + "@" + clId;
        final String cacheKey = baseKey + "@" + sourceDigest;
        final String versionKey = baseKey + "#" + sourceDigest;

        Entry cached = compiledClassCache.getIfPresent(cacheKey);
        if (cached == null) {
            final String uniqueName = className + "_" + shortDigest;
            final String identifier = "dynamic_" + uniqueName + "@" + clId;
            final String fqn = DYNAMIC_CLASS_PACKAGE_NAME + "." + uniqueName;

            Callable<Class<?>> compileCall = () -> {
                String rewritten = rewriteClassName(className, uniqueName, sourceCode);
                Class<?> compiled = javaSourceCompiler.compileJavaCode(fqn, rewritten, contextCl);
                if (classLoaderManager != null) {
                    classLoaderManager.registerClassLoader(identifier, compiled.getClassLoader(), sourceDigest);
                }
                return compiled;
            };

            FutureTask<Class<?>> task = new FutureTask<>(compileCall);
            Future<Class<?>> existing = inflightCompilationFutures.asMap().putIfAbsent(versionKey, task);
            final boolean creator = (existing == null);
            final Future<Class<?>> active = creator ? task : existing;
            if (creator) {
                try {
                    processEngineExecutors.compilation().execute(task);
                } catch (RejectedExecutionException ree) {
                    inflightCompilationFutures.asMap().remove(versionKey, task);
                    LOGGER.warn("Dynamic compilation task rejected: name={}, clId={}, digest={}. Executor queue may be full.",
                            className, clId, shortDigest, ree);
                    throw new RuntimeException("Dynamic compilation submission rejected", ree);
                }
            }

            final long startNs = System.nanoTime();
            try {
                Class<?> compiled = creator
                        ? ExecutorUtils.getWithTimeout(active, COMPILE_TIMEOUT_SECONDS * 1000L)
                        : ExecutorUtils.getWithTimeoutNoCancel(active, COMPILE_TIMEOUT_SECONDS * 1000L);
                cached = new Entry(sourceDigest, identifier, compiled);
                compiledClassCache.put(cacheKey, cached);
                LOGGER.debug("Successfully compiled and cached dynamic class: name={}, clId={}, digest={}",
                        className, clId, shortDigest);
            } catch (TimeoutException te) {
                long costMs = (System.nanoTime() - startNs) / 1_000_000L;
                LOGGER.error("Dynamic compilation timed out: name={}, clId={}, digest={}, costMs={} (timeout={}s)",
                        className, clId, shortDigest, costMs, COMPILE_TIMEOUT_SECONDS, te);
                throw new RuntimeException("Dynamic compilation timed out after " + COMPILE_TIMEOUT_SECONDS * 1000L + " ms", te);
            } catch (InterruptedException ie) {
                long costMs = (System.nanoTime() - startNs) / 1_000_000L;
                Thread.currentThread().interrupt();
                LOGGER.warn("Dynamic compilation was interrupted: name={}, clId={}, digest={}, costMs={}",
                        className, clId, shortDigest, costMs, ie);
                throw new RuntimeException("Dynamic compilation was interrupted", ie);
            } catch (CancellationException ce) {
                long costMs = (System.nanoTime() - startNs) / 1_000_000L;
                LOGGER.warn("Dynamic compilation was cancelled: name={}, clId={}, digest={}, costMs={}",
                        className, clId, shortDigest, costMs, ce);
                throw new RuntimeException("Dynamic compilation was cancelled", ce);
            } catch (ExecutionException ee) {
                long costMs = (System.nanoTime() - startNs) / 1_000_000L;
                Throwable cause = (ee.getCause() != null) ? ee.getCause() : ee;
                LOGGER.error("Dynamic compilation failed: name={}, clId={}, digest={}, costMs={}",
                        className, clId, shortDigest, costMs, cause);
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw new RuntimeException("Dynamic compilation failed", cause);
            } finally {
                if (creator) {
                    inflightCompilationFutures.asMap().remove(versionKey, task);
                }
            }
        } else {
            LOGGER.trace("Using cached dynamic class: name={}, clId={}, digest={}",
                    className, clId, cached.digest.length() >= 16 ? cached.digest.substring(0, 16) : cached.digest);
        }

        if (Modifier.isAbstract(cached.clazz.getModifiers()) || cached.clazz.isInterface()) {
            throw new IllegalArgumentException("Dynamic class is not instantiable (abstract/interface): " + cached.clazz.getName());
        }

        Class<?>[] effectiveParamTypes = (paramTypes != null) ? paramTypes : ClassUtils.toClass(args);
        Method target = MethodUtils.getMatchingAccessibleMethod(cached.clazz, methodName, effectiveParamTypes);
        if (target != null && Modifier.isStatic(target.getModifiers())) {
            try {
                return (T) MethodUtils.invokeStaticMethod(cached.clazz, methodName, args, effectiveParamTypes);
            } catch (NoSuchMethodException ignored) {
            }
        }

        try {
            Object inst = cached.clazz.getDeclaredConstructor().newInstance();
            return (T) MethodUtils.invokeMethod(inst, methodName, args, effectiveParamTypes);
        } catch (NoSuchMethodException nsme) {
            throw new IllegalArgumentException("No accessible no-arg constructor for dynamic class: " + cached.clazz.getName(), nsme);
        }
    }

    /**
     * Convenience overload with no args.
     */
    public <T> T execute(String sourceCode, String methodName) throws Exception {
        return execute(sourceCode, methodName, new Object[0]);
    }

    /**
     * Convenience overload inferring parameter types from args.
     */
    public <T> T execute(String sourceCode,
                         String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = ClassUtils.toClass(args);
        return execute(sourceCode, methodName, parameterTypes, args);
    }

    @Override
    public void close() throws Exception {
        compiledClassCache.invalidateAll();
        inflightCompilationFutures.asMap().forEach((cacheKey, compilationFuture) -> {
            if (compilationFuture != null && !compilationFuture.isDone()) {
                compilationFuture.cancel(true);
            }
        });
        inflightCompilationFutures.invalidateAll();
        sourceCodeDigestCache.invalidateAll();
        LOGGER.info("DynamicClassExecutor shut down: all caches cleared and in-flight compilations cancelled.");
    }

    /**
     * Evicts all cached versions for the given className and ClassLoader id.
     * Also cancels in-flight compilations with the same base key.
     */
    public void evictByBaseKey(String className, String classLoaderId) {
        final String cacheKeyPrefix = className + "@" + classLoaderId + "@";
        compiledClassCache.asMap().keySet().removeIf(cacheKey -> cacheKey.startsWith(cacheKeyPrefix));

        final String inflightKeyPrefix = className + "@" + classLoaderId + "#";
        inflightCompilationFutures.asMap().forEach((futureKey, compilationFuture) -> {
            if (futureKey.startsWith(inflightKeyPrefix)) {
                if (compilationFuture != null && !compilationFuture.isDone()) {
                    compilationFuture.cancel(true);
                }
                inflightCompilationFutures.invalidate(futureKey);
            }
        });

        LOGGER.info("Evicted all dynamic class versions and cancelled in-flight compilations for baseKey: {}@{}",
                className, classLoaderId);
    }

    /**
     * Returns number of cached versions for the given className and ClassLoader id.
     */
    public int sizeByBaseKey(String className, String classLoaderId) {
        final String cacheKeyPrefix = className + "@" + classLoaderId + "@";
        final int[] matchingEntryCount = new int[1];
        compiledClassCache.asMap().keySet().forEach(cacheKey -> {
            if (cacheKey.startsWith(cacheKeyPrefix)) {
                matchingEntryCount[0]++;
            }
        });
        return matchingEntryCount[0];
    }

    public int getCacheSize() {
        return (int) compiledClassCache.size();
    }

    private String computeDigestWithCache(String sourceCode) {
        if (sourceCode.length() > DIGEST_CACHE_SKIP_IF_LENGTH_EXCEEDS) {
            return sha256Hex(sourceCode);
        }
        String cachedDigest = sourceCodeDigestCache.getIfPresent(sourceCode);
        if (cachedDigest != null) {
            return cachedDigest;
        }
        String computedDigest = sha256Hex(sourceCode);
        sourceCodeDigestCache.put(sourceCode, computedDigest);
        return computedDigest;
    }

    /**
     * Cache entry containing digest, identifier and compiled Class.
     */
    private static final class Entry {
        final String digest;
        final String identifier;
        final Class<?> clazz;

        Entry(String digest, String identifier, Class<?> clazz) {
            this.digest = digest;
            this.identifier = identifier;
            this.clazz = clazz;
        }
    }
}
