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
package com.alibaba.compileflow.engine.core.runtime.compilation;

import com.alibaba.compileflow.engine.core.infrastructure.utils.ExecutorUtils;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;


/**
 * @author yusu
 */
public class DefaultInflightCompilationRegistry implements InflightCompilationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultInflightCompilationRegistry.class);

    private final Map<String, CompletableFuture<ProcessRuntimeWrapper>> inflight = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<ProcessRuntimeWrapper> putIfAbsent(String digest, CompletableFuture<ProcessRuntimeWrapper> slot) {
        CompletableFuture<ProcessRuntimeWrapper> existing = inflight.putIfAbsent(digest, slot);
        return existing != null ? existing : slot;
    }

    @Override
    public void remove(String digest, CompletableFuture<ProcessRuntimeWrapper> slot) {
        inflight.remove(digest, slot);
    }

    @Override
    public Collection<CompletableFuture<ProcessRuntimeWrapper>> values() {
        return inflight.values();
    }

    @Override
    public int size() {
        return inflight.size();
    }

    @Override
    public void clear() {
        inflight.clear();
    }

    @Override
    public void close() throws Exception {
        long start = System.currentTimeMillis();
        int inflightCount = inflight.size();

        if (inflightCount == 0) {
            LOGGER.info("InflightCompilationRegistry closing: no pending compilations.");
            return;
        }

        LOGGER.info("InflightCompilationRegistry closing: awaiting {} pending compilations (timeout: 5s)...", inflightCount);

        try {
            CompletableFuture<Void> all = CompletableFuture.allOf(
                    inflight.values().toArray(new CompletableFuture[0]));
            ExecutorUtils.getWithTimeout(all, 5000L);
            LOGGER.info("All {} inflight compilations completed successfully.", inflightCount);
        } catch (TimeoutException te) {
            LOGGER.warn("Timeout waiting for inflight compilations. Cancelling {} remaining tasks...", inflight.size());
            ExecutorUtils.cancelUnfinished(inflight.values());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while waiting for inflight compilations. Cancelling all pending tasks...", ie);
            ExecutorUtils.cancelUnfinished(inflight.values());
        } catch (CancellationException ce) {
            LOGGER.warn("Some inflight compilations were cancelled during shutdown.");
            ExecutorUtils.cancelUnfinished(inflight.values());
        } catch (ExecutionException ee) {
            LOGGER.warn("Some inflight compilations failed during shutdown.", ee.getCause());
        } finally {
            inflight.clear();
            LOGGER.info("InflightCompilationRegistry shutdown complete in {}ms.", (System.currentTimeMillis() - start));
        }
    }

}


