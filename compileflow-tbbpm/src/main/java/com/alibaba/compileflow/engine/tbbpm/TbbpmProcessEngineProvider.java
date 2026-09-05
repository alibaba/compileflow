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
package com.alibaba.compileflow.engine.tbbpm;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.AbstractProcessEngineProvider;
import com.alibaba.compileflow.engine.core.DefaultProcessEngine;
import com.alibaba.compileflow.engine.core.assembly.EngineDependencies;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompilerProvider;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.alibaba.compileflow.engine.tbbpm.validation.TbbpmModelValidator;

/**
 * Service provider for the TBBPM process engine.
 *
 * <p>Discovered and used by {@link com.alibaba.compileflow.engine.ProcessEngineFactory}
 * to create TBBPM-fronted engines. Follows the standard Java
 * {@link java.util.ServiceLoader} pattern.
 *
 * @author yusu
 */
public final class TbbpmProcessEngineProvider extends AbstractProcessEngineProvider
        implements ProcessSemanticCompilerProvider {
    @Override
    public ProcessModelType getModelType() {
        return ProcessModelType.TBBPM;
    }

    @Override
    protected DefaultProcessEngine doCreateEngine(ProcessEngineConfig config, EngineDependencies dependencies) {
        return new DefaultProcessEngine(config, dependencies, createSemanticCompiler());
    }

    @Override
    public ProcessSemanticCompiler<?> createSemanticCompiler() {
        TbbpmModelReader reader = new TbbpmModelReader();
        TbbpmModelValidator validator = new TbbpmModelValidator();
        TbbpmSemanticFrontend frontend = new TbbpmSemanticFrontend();
        return new ProcessSemanticCompiler<>(reader, validator, frontend::compile);
    }
}
