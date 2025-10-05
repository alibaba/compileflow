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

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeWrapper;

import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates compilation with single-flight, retries, and batch parallelism.
 *
 * @author yusu
 */
public interface CompilationService extends AutoCloseable {

    CompletableFuture<ProcessRuntimeWrapper> compileAsync(
            ProcessSource processSource,
            ClassLoader cl,
            String newDigest,
            String expectedDigest);

    ProcessRuntimeWrapper compileSync(
            ProcessSource processSource,
            ClassLoader cl);

    void compileBatch(ClassLoader cl, ProcessSource... sources);

}


