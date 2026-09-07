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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure;

import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEngineAutoConfiguration;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasRouteConverger;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.core.routing.LocalReadyAliasRouteSource;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures shared node-local routing state and Alias admission.
 *
 * @author yusu
 */
@AutoConfiguration(after = {CompileFlowEngineAutoConfiguration.class, CompileFlowDeployStoreAutoConfiguration.class})
@ConditionalOnClass(name = "com.alibaba.compileflow.deploy.api.ProcessDeploymentService")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
public class CompileFlowDeployRoutingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(LocalRoutingState.class)
    public LocalRoutingState localRoutingState() {
        return LocalRoutingState.requiringLocalInstallation();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessAliasRouteSource.class)
    public ProcessAliasRouteSource processAliasRouteSource(ObjectProvider<AliasRouteConverger> convergerProvider,
            LocalRoutingState localRoutingState, CompileFlowDeploymentProperties deploymentProperties) {
        boolean embedded = deploymentProperties.getTopology() == CompileFlowDeploymentProperties.Topology.EMBEDDED;
        AliasRouteConverger routeConverger =
                embedded ? alias -> convergerProvider.getObject().converge(alias) : alias -> {};
        return new LocalReadyAliasRouteSource(localRoutingState.getAliasRouteState(), routeConverger);
    }

    @Bean
    @ConditionalOnMissingBean(AliasAdmission.class)
    public AliasAdmission aliasAdmission(ProcessAliasRouteSource aliasRouteSource, ProcessEngineConfig engineConfig) {
        return new AliasAdmission(aliasRouteSource, engineConfig.getAliasTargetingPolicies());
    }
}
