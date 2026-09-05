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

import com.alibaba.compileflow.engine.ProcessDataMapper;
import com.alibaba.compileflow.engine.core.java.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.core.source.loader.ProcessDefinitionLoader;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.util.Objects;

/**
 * Engine-scoped dependencies resolved once during process engine construction.
 *
 * @author yusu
 */
public record EngineDependencies(ProcessDefinitionLoader definitionLoader, ProcessComponentResolver componentResolver,
        ScriptExecutorRegistry scriptExecutors, JavaCompiler javaCompiler, ProcessDataMapper dataMapper,
        LocalRoutingState localRoutingState, AliasAdmission aliasAdmission) {
    public EngineDependencies {
        Objects.requireNonNull(definitionLoader, "definitionLoader");
        Objects.requireNonNull(componentResolver, "componentResolver");
        Objects.requireNonNull(scriptExecutors, "scriptExecutors");
        Objects.requireNonNull(javaCompiler, "javaCompiler");
        Objects.requireNonNull(dataMapper, "dataMapper");
        Objects.requireNonNull(localRoutingState, "localRoutingState");
        Objects.requireNonNull(aliasAdmission, "aliasAdmission");
    }
}
