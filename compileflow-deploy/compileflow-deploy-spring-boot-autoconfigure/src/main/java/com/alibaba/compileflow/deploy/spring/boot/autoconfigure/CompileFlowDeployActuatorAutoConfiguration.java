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

import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxAdminService;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.health.DeploymentPipelineHealthIndicator;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEngineAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Boot Actuator health indicators for CompileFlow deployment components.
 *
 * @author yusu
 */
@AutoConfiguration(after = {CompileFlowEngineAutoConfiguration.class,
        CompileFlowDeployControlPlaneAutoConfiguration.class})
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
public class CompileFlowDeployActuatorAutoConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxAdminService")
    @ConditionalOnBean(RoutingOutboxAdminService.class)
    static class DeploymentPipelineHealthConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "deploymentPipelineHealthIndicator")
        public HealthIndicator deploymentPipelineHealthIndicator(RoutingOutboxAdminService routingOutboxAdminService) {
            return new DeploymentPipelineHealthIndicator(routingOutboxAdminService);
        }
    }
}
