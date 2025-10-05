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

import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeWrapper;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Single-flight registry for compilation tasks keyed by digest.
 * First caller installs; others share the same future; removed on completion.
 *
 * @author yusu
 */
public interface InflightCompilationRegistry extends AutoCloseable {

    /**
     * Puts a new slot for the digest if absent, returning the existing one if already present.
     */
    CompletableFuture<ProcessRuntimeWrapper> putIfAbsent(String digest, CompletableFuture<ProcessRuntimeWrapper> slot);

    /**
     * Removes the digest-slot mapping if it is the same slot.
     */
    void remove(String digest, CompletableFuture<ProcessRuntimeWrapper> slot);

    /**
     * @return a snapshot view of all inflight futures.
     */
    Collection<CompletableFuture<ProcessRuntimeWrapper>> values();

    /**
     * @return number of inflight entries.
     */
    int size();

    /**
     * Clears all entries.
     */
    void clear();
}


