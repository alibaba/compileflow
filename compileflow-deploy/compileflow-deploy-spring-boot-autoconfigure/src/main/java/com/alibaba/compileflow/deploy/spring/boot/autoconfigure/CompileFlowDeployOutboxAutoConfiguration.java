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
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.control.outbox.ProjectionStoreRoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxDispatchPolicy;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxDispatcher;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxScheduler;
import com.alibaba.compileflow.deploy.control.outbox.RoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.spi.store.DeployStore;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxStore;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.RoutingOutboxProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures routing outbox persistence and dispatch for the control plane.
 *
 * @author yusu
 */
@AutoConfiguration(after = {CompileFlowEngineAutoConfiguration.class,
        CompileFlowEmbeddedDeploymentRuntimeAutoConfiguration.class}, afterName = {"com.alibaba.compileflow.deploy."
        + "spring.boot.autoconfigure.postgres.CompileFlowDeployPostgresAutoConfiguration",
        "com.alibaba.compileflow.deploy.spring.boot.autoconfigure.mysql.CompileFlowDeployMySqlAutoConfiguration"})
@ConditionalOnClass(RoutingOutboxDispatcher.class)
@ConditionalOnBean(DeployStore.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "control-plane-enabled", havingValue = "true", matchIfMissing = true)
public class CompileFlowDeployOutboxAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(RoutingStateDeliveryTarget.class)
    public RoutingStateDeliveryTarget distributedRoutingStateDeliveryTarget(DeploymentProjectionStore projectionStore,
            CompileFlowDeploymentProperties properties) {
        var routingProperties = properties.getRouting();
        return new ProjectionStoreRoutingStateDeliveryTarget(projectionStore, routingProperties.getOperationTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(RoutingOutboxDispatcher.class)
    public RoutingOutboxDispatcher routingOutboxDispatcher(RoutingOutboxStore outboxRepository,
            RoutingStateDeliveryTarget deliveryTarget, CompileFlowDeploymentProperties properties) {
        var outboxProperties = properties.getOutbox();
        RoutingOutboxProperties.Retry retry = outboxProperties.getRetry();
        return new RoutingOutboxDispatcher(outboxRepository, deliveryTarget,
                new RoutingOutboxDispatchPolicy(outboxProperties.getDispatchBatchSize(),
                        outboxProperties.getLeaseDuration(), retry.getMaxAttempts(), retry.getInitialDelay(),
                        retry.getMaxDelay()));
    }

    @Bean(name = "routingOutboxScheduler", destroyMethod = "close")
    @ConditionalOnMissingBean(RoutingOutboxScheduler.class)
    public RoutingOutboxScheduler routingOutboxScheduler(RoutingOutboxDispatcher dispatcher,
            CompileFlowDeploymentProperties properties, RoutingOutboxStore outboxRepository) {
        var outboxProps = properties.getOutbox();
        if (!outboxProps.getRetention().isZero()) {
            return new RoutingOutboxScheduler(dispatcher, outboxProps.getDispatchInterval(), outboxRepository,
                    outboxProps.getRetention());
        }
        return new RoutingOutboxScheduler(dispatcher, outboxProps.getDispatchInterval());
    }

    @Bean(name = "routingOutboxSchedulerLifecycle")
    @ConditionalOnMissingBean(name = "routingOutboxSchedulerLifecycle")
    public SmartLifecycle routingOutboxSchedulerLifecycle(RoutingOutboxScheduler scheduler) {
        return new ActionBackedLifecycle(scheduler::start, scheduler::stop,
                CompileFlowLifecyclePhases.DEPLOY_CONTROL_PLANE);
    }
}
