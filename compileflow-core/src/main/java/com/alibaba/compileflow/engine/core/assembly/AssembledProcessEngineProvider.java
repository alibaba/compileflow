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
package com.alibaba.compileflow.engine.core.assembly;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spi.ProcessEngineProvider;

/**
 * Internal provider contract for official engines that accept an assembled dependency snapshot.
 *
 * <p>This is deliberately separate from the public {@link ProcessEngineProvider} contract.
 * Application providers create a complete engine from public configuration alone; composition
 * roots use this contract only when they must inject core-owned deployment state atomically.
 *
 * @author yusu
 */
public interface AssembledProcessEngineProvider extends ProcessEngineProvider {
    /**
     * Creates an engine from validated configuration and complete core dependencies.
     *
     * @param config       validated engine configuration
     * @param dependencies complete immutable construction dependencies
     * @return newly created engine
     */
    ProcessEngine createEngine(ProcessEngineConfig config, EngineDependencies dependencies);
}
