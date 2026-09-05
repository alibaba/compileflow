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

import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeployRuntimeProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentArtifactProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentRoutingProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.ReconciliationProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.RoutingOutboxProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Binds and validates deployment configuration before deployment features are assembled.
 *
 * @author yusu
 */
@AutoConfiguration(before = CompileFlowRepositoryAutoConfiguration.class)
@EnableConfigurationProperties(CompileFlowDeploymentProperties.class)
public class CompileFlowDeployPropertiesAutoConfiguration {
    @Bean
    public DeploymentArtifactProperties deploymentArtifactProperties(CompileFlowDeploymentProperties properties) {
        return properties.getArtifact();
    }

    @Bean
    public DeployRuntimeProperties deployRuntimeProperties(CompileFlowDeploymentProperties properties) {
        return properties.getRuntime();
    }

    @Bean
    public DeploymentRoutingProperties deploymentRoutingProperties(CompileFlowDeploymentProperties properties) {
        return properties.getRouting();
    }

    @Bean
    public RoutingOutboxProperties routingOutboxProperties(CompileFlowDeploymentProperties properties) {
        return properties.getOutbox();
    }

    @Bean
    public ReconciliationProperties reconciliationProperties(CompileFlowDeploymentProperties properties) {
        return properties.getReconciliation();
    }
}
