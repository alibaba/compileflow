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
package com.alibaba.compileflow.engine;

/**
 * Manages this engine's local runtime lifecycle.
 *
 * <p>Loading is a node-local runtime acquisition and cache operation. It is not a control-plane publish,
 * rollout, or alias-routing operation.
 *
 * @author yusu
 * @see ProcessEngine
 */
public interface ProcessRuntimeManager {
    /**
     * Warms exact runtimes without creating a code or version binding.
     *
     * @param definitions explicit process definitions
     */
    void warmUp(ProcessDefinition... definitions);

    /**
     * Loads exact definition content under an immutable local version binding.
     *
     * @param ref        exact version reference
     * @param definition exact process definition
     */
    void load(ProcessRef.Version ref, ProcessDefinition definition);

    /**
     * Releases this service's local ownership of exact-version runtimes.
     *
     * @param refs exact version references
     */
    void unload(ProcessRef.Version... refs);
}
