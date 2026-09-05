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
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;

/**
 * Service Provider Interface (SPI) for {@link ProcessEngine} implementations.
 * <p>
 * This interface allows plain-Java applications and implementation modules to discover process
 * engine implementations (for example BPMN or TBBPM) through Java's
 * {@link java.util.ServiceLoader} mechanism. Each implementation is responsible for creating a
 * complete {@link ProcessEngine} for one existing {@link ProcessModelType} from public configuration
 * alone.
 * <p>
 * {@code ProcessModelType} is a closed set, so this SPI selects one implementation for a known
 * model type; it does not add model types. Provider resolution requires exactly one provider for
 * the requested model and fails closed on duplicates. Spring applications that replace the
 * complete engine should expose a {@code ProcessEngine} bean, allowing auto-configuration to back
 * off; that integration-level bean replacement is not an SPI conflict policy.
 *
 * @author yusu
 * @see com.alibaba.compileflow.engine.ProcessEngineFactory
 */
public interface ProcessEngineProvider {
    /**
     * Returns the specific {@link ProcessModelType} that this provider supports.
     * This is used by the {@link ProcessEngineFactory} to find the correct provider
     * for a given configuration.
     *
     * @return non-null process model type supported by this provider
     */
    ProcessModelType getModelType();

    /**
     * Creates a new {@link ProcessEngine} instance configured according to the provided
     * {@link ProcessEngineConfig}.
     *
     * @param config configuration for the engine to create
     * @return new non-null configured process engine
     */
    ProcessEngine createEngine(ProcessEngineConfig config);
}
