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
package com.alibaba.compileflow.engine.core.source.loader;

import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;

/**
 * Loads direct definition sources into exact, bounded parser snapshots.
 *
 * @author yusu
 */
@FunctionalInterface
public interface ProcessDefinitionLoader {
    /**
     * Loads one direct source into an immutable byte snapshot.
     *
     * <p>The returned snapshot is the sole source for digesting, parsing, and compilation. The
     * Implementations must read file or classpath content at most once and enforce resource access
     * and size policies before returning.
     *
     * @param request       runtime request carrying an explicit definition source
     * @param classLoader   exact class-loader scope used for classpath lookup
     * @return loaded snapshot
     */
    ProcessDefinitionSnapshot load(ProcessRuntimeRequest request, ClassLoader classLoader);
}
