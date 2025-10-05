/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.runtime.cache;

import com.alibaba.compileflow.engine.config.ProcessPropertyProvider;
import com.alibaba.compileflow.engine.core.classloader.ProcessClassLoaderManager;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeWrapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Guava-backed {@link ProcessRuntimeCache}.
 * Max size is configured; removal unregisters classloaders; CAS on install;
 * async cleanup for discarded runtimes.
 *
 * @author yusu
 */
public class DefaultProcessRuntimeCache implements ProcessRuntimeCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProcessRuntimeCache.class);

    private final Cache<String, ProcessRuntimeWrapper> cache;
    private final ProcessClassLoaderManager classLoaderManager;
    private final ScheduledExecutorService scheduler;

    public DefaultProcessRuntimeCache(ProcessClassLoaderManager classLoaderManager,
                                      ScheduledExecutorService scheduler) {
        this.classLoaderManager = Objects.requireNonNull(classLoaderManager, "classLoaderManager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");

        this.cache = CacheBuilder.newBuilder()
                .maximumSize(ProcessPropertyProvider.Cache.getRuntimeMaxSize())
                .removalListener((RemovalListener<String, ProcessRuntimeWrapper>) n -> {
                    ProcessRuntimeWrapper wrapper = n.getValue();
                    if (wrapper == null) {
                        return;
                    }
                    try {
                        classLoaderManager.unregisterClassLoader(wrapper.getDigest());
                        LOGGER.debug("Cache entry cleaned up: code={}, digest={}, cause={}",
                                n.getKey(), wrapper.getDigest(), n.getCause());
                    } catch (Exception e) {
                        LOGGER.warn("Failed to unregister ClassLoader on cache removal: code={}, digest={}",
                                n.getKey(), wrapper.getDigest(), e);
                    }
                })
                .build();
    }

    @Override
    public ProcessRuntimeWrapper getIfPresent(String code) {
        return cache.getIfPresent(code);
    }

    @Override
    public void compareAndInstall(String code, String expectedDigest, ProcessRuntimeWrapper newWrapper) {
        final Holder<ProcessRuntimeWrapper> discarded = new Holder<>();

        cache.asMap().compute(code, (k, cur) -> {
            boolean canReplace = (cur == null) || Objects.equals(cur.getDigest(), expectedDigest);
            if (canReplace) {
                return newWrapper;
            }
            discarded.value = newWrapper;
            return cur;
        });

        if (discarded.value != null) {
            final String finalCode = code;
            scheduler.submit(() -> cleanupDiscardedRuntime(finalCode, discarded.value));
        }
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    @Override
    public long size() {
        return cache.size();
    }

    private void cleanupDiscardedRuntime(String code, ProcessRuntimeWrapper discarded) {
        try {
            LOGGER.debug("Cleaning up discarded runtime: code={}, digest={}", code, discarded.getDigest());
            ProcessRuntime runtime = discarded.getRuntime();
            if (runtime != null) {
                classLoaderManager.unregisterClassLoader(discarded.getDigest());
            }
            LOGGER.debug("Discarded runtime cleaned up: code={}, digest={}", code, discarded.getDigest());
        } catch (Exception e) {
            LOGGER.warn("Failed to cleanup discarded runtime: code={}, digest={}",
                    code, discarded.getDigest(), e);
        }
    }

    /**
     * Small holder to avoid additional library deps for mutable reference.
     */
    private static final class Holder<T> {
        T value;
    }
}


