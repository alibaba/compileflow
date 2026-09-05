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
package com.alibaba.compileflow.engine.core.runtime.loading;

import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import java.util.concurrent.CompletableFuture;

/**
 * Loads exact process definitions into engine-local executable runtimes.
 *
 * @author yusu
 */
public interface ProcessRuntimeLoader extends AutoCloseable {
    CompletableFuture<ProcessRuntimeEntry> loadAsync(ProcessRuntimeRequest request, ClassLoader classLoader,
            ProcessRuntimeEntry expectedEntry);

    ProcessRuntimeEntry loadSync(ProcessRuntimeRequest request, ClassLoader classLoader);

    ProcessRuntimeEntry loadExactSync(ProcessRuntimeRequest request, ClassLoader classLoader);

    ProcessDefinitionSnapshot resolve(ProcessRuntimeRequest request, ClassLoader classLoader);

    ProcessRuntimeEntry runtimeCheckSync(ProcessDefinitionSnapshot definition, ClassLoader classLoader);

    void loadBatch(String ownerId, ClassLoader classLoader, ProcessRuntimeRequest... requests);

    void loadExactBatch(ClassLoader classLoader, ProcessRuntimeRequest... requests);

    @Override
    void close();
}
