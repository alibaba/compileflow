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
package com.alibaba.compileflow.engine.spi;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;

/**
 * Version-coupled bootstrap contract separating the API artifact from the engine implementation.
 * Discovery requires exactly one implementation and does not select a semantic format.
 * Application extensions use engine plugins or replace the engine bean at their framework boundary.
 *
 * @author yusu
 * @see com.alibaba.compileflow.engine.ProcessEngineFactory
 */
public interface ProcessEngineProvider {
    /**
     * Creates a new {@link ProcessEngine} instance configured according to the provided
     * {@link ProcessEngineConfig}.
     *
     * @param config configuration for the engine to create
     * @return new non-null configured process engine
     */
    ProcessEngine createEngine(ProcessEngineConfig config);
}
