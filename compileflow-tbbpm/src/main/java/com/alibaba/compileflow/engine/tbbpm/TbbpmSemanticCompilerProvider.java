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
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompilerProvider;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.alibaba.compileflow.engine.tbbpm.validation.TbbpmModelValidator;

/**
 * Installs the TBBPM semantic frontend through ServiceLoader.
 *
 * @author yusu
 */
public final class TbbpmSemanticCompilerProvider implements ProcessSemanticCompilerProvider {
    @Override
    public ProcessModelType getModelType() {
        return ProcessModelType.TBBPM;
    }

    @Override
    public ProcessSemanticCompiler<?> createSemanticCompiler() {
        TbbpmModelReader reader = new TbbpmModelReader();
        TbbpmModelValidator validator = new TbbpmModelValidator();
        TbbpmSemanticFrontend frontend = new TbbpmSemanticFrontend();
        return new ProcessSemanticCompiler<>(reader, validator, frontend::compile);
    }
}
