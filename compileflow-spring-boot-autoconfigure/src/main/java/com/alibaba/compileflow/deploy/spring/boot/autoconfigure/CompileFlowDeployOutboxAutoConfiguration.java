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

import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowCoreAutoConfiguration;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import com.alibaba.compileflow.deploy.control.routing.ChannelRoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxDispatchPolicy;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxDispatcher;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxScheduler;
import com.alibaba.compileflow.deploy.control.routing.RoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.control.repository.JdbcRoutingOutboxRepository;
import com.alibaba.compileflow.deploy.control.repository.RoutingOutboxRepository;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentRoutingProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.RoutingOutboxProperties;
import javax.sql.DataSource;
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
@AutoConfiguration(after = {CompileFlowCoreAutoConfiguration.class,
        CompileFlowRepositoryAutoConfiguration.class, CompileFlowEmbeddedDataPlaneAutoConfiguration.class})
@ConditionalOnClass(RoutingOutboxDispatcher.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "control-plane-enabled", havingValue = "true", matchIfMissing = true)
public class CompileFlowDeployOutboxAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RoutingOutboxRepository.class)
    public RoutingOutboxRepository routingOutboxRepository(DataSource dataSource) {
        return new JdbcRoutingOutboxRepository(dataSource);
    }

    @Bean
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(RoutingStateDeliveryTarget.class)
    public RoutingStateDeliveryTarget distributedRoutingStateDeliveryTarget(DeploymentSyncChannel channel,
            DeploymentRoutingProperties routingProperties) {
        return new ChannelRoutingStateDeliveryTarget(channel, routingProperties.getOperationTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(RoutingOutboxDispatcher.class)
    public RoutingOutboxDispatcher routingOutboxDispatcher(RoutingOutboxRepository outboxRepository,
            RoutingStateDeliveryTarget deliveryTarget, RoutingOutboxProperties outboxProperties) {
        RoutingOutboxProperties.Retry retry = outboxProperties.getRetry();
        return new RoutingOutboxDispatcher(outboxRepository, deliveryTarget,
                new RoutingOutboxDispatchPolicy(outboxProperties.getDispatchBatchSize(),
                        outboxProperties.getLeaseDuration(), retry.getMaxAttempts(), retry.getInitialDelay(),
                        retry.getMaxDelay()));
    }

    @Bean(name = "routingOutboxScheduler", destroyMethod = "close")
    @ConditionalOnMissingBean(RoutingOutboxScheduler.class)
    public RoutingOutboxScheduler routingOutboxScheduler(RoutingOutboxDispatcher dispatcher,
            RoutingOutboxProperties outboxProps, RoutingOutboxRepository outboxRepository) {
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
