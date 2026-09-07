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
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.java.compiler.JdkJavaCompiler;
import com.alibaba.compileflow.engine.core.source.loader.DefaultProcessDefinitionLoader;
import com.alibaba.compileflow.engine.core.mapping.JacksonProcessDataMapper;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.core.routing.LocalReadyAliasRouteSource;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import java.util.Objects;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.core.DefaultProcessEngine;

/**
 * Constructs the internal collaborators for one process engine instance.
 *
 * @author yusu
 */
public final class EngineAssembly {
    private EngineAssembly() {
    }

    /**
     * Creates an engine through the canonical resource assembly.
     * @param config immutable engine configuration
     * @return newly owned engine
     */
    public static ProcessEngine create(ProcessEngineConfig config) {
        return create(config, assemble(config));
    }

    /**
     * Creates an engine from dependencies sharing the host's admission state.
     * @param config immutable engine configuration
     * @param dependencies assembled dependencies transferred to the engine
     * @return newly owned engine
     */
    public static ProcessEngine create(ProcessEngineConfig config, EngineDependencies dependencies) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(dependencies, "dependencies");
        try {
            return new DefaultProcessEngine(config, dependencies);
        } catch (RuntimeException | Error failure) {
            try {
                dependencies.scriptExecutors().close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /**
     * Assembles all concrete dependencies for one process engine.
     * <p>
     * Typed capabilities carried by the configuration snapshot (component resolver,
     * user script executors) are consumed here; everything else uses built-in defaults.
     *
     * @param config validated process engine configuration
     * @return immutable dependency set for the engine instance
     */
    public static EngineDependencies assemble(ProcessEngineConfig config) {
        return assemble(config, new LocalRoutingState());
    }

    /**
     * Assembles an engine around an existing version-state owner.
     *
     * @param config            validated process engine configuration
     * @param localRoutingState node-local state shared with the deployment runtime
     * @return immutable dependency set for the engine instance
     */
    public static EngineDependencies assemble(ProcessEngineConfig config, LocalRoutingState localRoutingState) {
        ProcessEngineConfig engineConfig = Objects.requireNonNull(config, "config");
        Objects.requireNonNull(localRoutingState, "localRoutingState");
        ProcessAliasRouteSource aliasRouteSource = engineConfig.getAliasRouteSource();
        AliasAdmission admission = new AliasAdmission(aliasRouteSource == null
                ? LocalReadyAliasRouteSource.from(localRoutingState.getAliasRouteState())
                : aliasRouteSource, engineConfig.getAliasTargetingPolicies());
        return assemble(engineConfig, localRoutingState, admission);
    }

    /**
     * Assembles an engine with shared local-ready Alias admission.
     *
     * @param config            validated process engine configuration
     * @param localRoutingState node-local state shared with the deployment runtime
     * @param aliasAdmission    shared local-ready Alias admission
     * @return immutable dependency set for the engine instance
     */
    public static EngineDependencies assemble(ProcessEngineConfig config, LocalRoutingState localRoutingState,
            AliasAdmission aliasAdmission) {
        ProcessEngineConfig engineConfig = Objects.requireNonNull(config, "config");
        Objects.requireNonNull(localRoutingState, "localRoutingState");
        AliasAdmission admission = Objects.requireNonNull(aliasAdmission, "aliasAdmission");
        DefaultProcessDefinitionLoader definitionLoader =
                new DefaultProcessDefinitionLoader(engineConfig.getDefinitionConfig());
        JdkJavaCompiler javaCompiler = new JdkJavaCompiler();
        ProcessDataMapper dataMapper = engineConfig.getDataMapper();
        if (dataMapper == null) {
            dataMapper = JacksonProcessDataMapper.createDefault();
        }
        ScriptExecutorRegistry scriptExecutors =
                ScriptExecutorRegistry.configured(engineConfig.getClassLoader(), engineConfig.getScriptExecutors());
        return new EngineDependencies(definitionLoader, engineConfig.getComponentResolver(), scriptExecutors,
                javaCompiler, dataMapper, localRoutingState, admission);
    }
}
