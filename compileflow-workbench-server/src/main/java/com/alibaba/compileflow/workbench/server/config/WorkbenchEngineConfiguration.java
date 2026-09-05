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
package com.alibaba.compileflow.workbench.server.config;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineConfigurationFactory;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineRegistry;
import java.util.EnumSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Composes the Server product's BPMN and TBBPM engines without changing core API semantics.
 *
 * @author yusu
 */
@Configuration(proxyBeanMethods = false)
public class WorkbenchEngineConfiguration {
    /**
     * Creates one immutable registry containing every format accepted by the Server API.
     *
     * @param configurationFactory shared Spring configuration boundary
     * @param primaryConfiguration configuration for the default injected engine
     * @param snapshotsProvider    deployment localRoutingState when deployment is enabled
     * @param aliasAdmissionProvider published Alias admission when deployment is enabled
     * @return lifecycle owner for both format-bound engines
     */
    @Bean(destroyMethod = "close")
    public ProcessEngineRegistry processEngineRegistry(ProcessEngineConfigurationFactory configurationFactory,
            ProcessEngineConfig primaryConfiguration, ObjectProvider<LocalRoutingState> snapshotsProvider,
            ObjectProvider<AliasAdmission> aliasAdmissionProvider) {
        LocalRoutingState localRoutingState = snapshotsProvider.getIfAvailable(LocalRoutingState::new);
        AliasAdmission aliasAdmission = aliasAdmissionProvider.getIfAvailable();
        return aliasAdmission == null
                ? new ProcessEngineRegistry(configurationFactory, primaryConfiguration, localRoutingState,
                        EnumSet.allOf(ProcessModelType.class))
                : new ProcessEngineRegistry(configurationFactory, primaryConfiguration, localRoutingState,
                        aliasAdmission, EnumSet.allOf(ProcessModelType.class));
    }

    /**
     * Exposes the configured primary format for conventional single-engine integrations.
     *
     * <p>The registry owns this engine's lifecycle.
     *
     * @param registry             Server engine registry
     * @param primaryConfiguration primary model selection
     * @return primary format-bound engine
     */
    @Bean(destroyMethod = "")
    @Primary
    public ProcessEngine processEngine(ProcessEngineRegistry registry, ProcessEngineConfig primaryConfiguration) {
        return registry.get(primaryConfiguration.getModelType());
    }

    /**
     * Installs published artifacts into the engine selected by artifact model type.
     *
     * @param registry        Server engine registry
     * @param metricsProvider shared deployment metrics
     * @return multi-format runtime loader
     */
    @Bean
    public ProcessArtifactRuntimeLoader processRuntimeLoader(ProcessEngineRegistry registry,
            ObjectProvider<ProcessDeploymentMetrics> metricsProvider) {
        return new ProcessArtifactRuntimeLoader(registry.getEngines(),
                metricsProvider.getIfAvailable(ProcessDeploymentMetrics::new));
    }
}
