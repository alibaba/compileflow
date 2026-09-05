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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import com.alibaba.compileflow.engine.ProcessDataMapper;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.AssembledProcessEngineFactory;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.assembly.EngineDependencies;
import com.alibaba.compileflow.engine.core.mapping.JacksonProcessDataMapper;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.ProcessEnginePlugin;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import com.alibaba.compileflow.engine.spi.observability.TraceIdProvider;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties.ProcessEngineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Creates the core engine configuration and its single {@link ProcessEngine} bean.
 *
 * @author yusu
 */
@AutoConfiguration
@ConditionalOnClass(ProcessEngine.class)
public class CompileFlowCoreAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompileFlowCoreAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ProcessEngineConfigurationFactory processEngineConfigurationFactory(ProcessEngineProperties properties,
            ObjectProvider<ProcessEventListener> eventListeners, ObjectProvider<TraceIdProvider> traceIdProviders,
            ObjectProvider<ProcessComponentResolver> componentResolvers, ObjectProvider<ProcessDataMapper> dataMappers,
            ObjectProvider<ProcessContextPropagator> contextPropagators,
            ObjectProvider<ProcessAliasRouteSource> aliasRouteSources, ObjectProvider<ScriptExecutor> scriptExecutors,
            ObjectProvider<ProcessEnginePlugin> plugins, ApplicationContext applicationContext) {
        return new ProcessEngineConfigurationFactory(properties, eventListeners, traceIdProviders, componentResolvers,
                dataMappers, contextPropagators, aliasRouteSources, scriptExecutors, plugins, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDataMapper processDataMapper(ObjectProvider<ObjectMapper> objectMappers) {
        ObjectMapper objectMapper = objectMappers.getIfAvailable(() -> JsonMapper.builder().build());
        return new JacksonProcessDataMapper(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessEngineConfig processEngineConfig(ProcessEngineProperties properties,
            ProcessEngineConfigurationFactory configurationFactory) {
        return configurationFactory.create(properties.getModelType());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "compileflow.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ProcessEngine processEngine(ProcessEngineConfig config,
            ObjectProvider<LocalRoutingState> localRoutingStateProvider,
            ObjectProvider<AliasAdmission> aliasAdmissions) {
        LOGGER.info("Auto-configuring CompileFlow ProcessEngine...");

        LocalRoutingState sharedRoutingState = localRoutingStateProvider.getIfAvailable();
        LocalRoutingState localRoutingState = sharedRoutingState == null ? new LocalRoutingState() : sharedRoutingState;
        AliasAdmission admission = aliasAdmissions.getIfAvailable();
        EngineDependencies dependencies = admission == null
                ? EngineAssembly.assemble(config, localRoutingState)
                : EngineAssembly.assemble(config, localRoutingState, admission);
        ProcessEngine engine = AssembledProcessEngineFactory.create(config, dependencies);

        LOGGER.info("CompileFlow ProcessEngine ready. modelType={}, versionState={}", config.getModelType(),
                sharedRoutingState == null ? "engine-owned" : "deployment-shared");
        return engine;
    }
}
