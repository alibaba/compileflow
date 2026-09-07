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
package com.alibaba.compileflow.engine.core;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.spi.ProcessEngineProvider;

/**
 * API-to-Core bootstrap adapter for the canonical engine assembly.
 *
 * @author yusu
 */
public final class DefaultProcessEngineProvider implements ProcessEngineProvider {
    @Override
    public ProcessEngine createEngine(ProcessEngineConfig config) {
        return EngineAssembly.create(config);
    }
}
