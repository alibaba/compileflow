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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing.RoutingStateKeysBuilder;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentArtifactProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentRoutingProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.ReconciliationProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DeploymentConfigurationPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowDeployPropertiesAutoConfiguration.class));

    @Test
    void shouldBindTypedDeploymentConfiguration() {
        contextRunner
            .withPropertyValues("compileflow.deploy.artifact.mode=CHANNEL",
                    "compileflow.deploy.artifact.key-prefix=flows.artifact.",
                    "compileflow.deploy.artifact.operation-timeout=250ms",
                    "compileflow.deploy.runtime.failure-backoff=1s",
                    "compileflow.deploy.runtime.convergence-timeout=12s", "compileflow.deploy.runtime.concurrency=2",
                    "compileflow.deploy.routing.codes[0]=order.create",
                    "compileflow.deploy.routing.operation-timeout=3s", "compileflow.deploy.outbox.retention=0ms",
                    "compileflow.deploy.outbox.lease-duration=45s", "compileflow.deploy.outbox.retry.max-attempts=7",
                    "compileflow.deploy.outbox.retry.initial-delay=3s", "compileflow.deploy.outbox.retry.max-delay=30s",
                    "compileflow.deploy.reconciliation.mode=DETECT", "compileflow.deploy.reconciliation.interval=30s")
            .run(context -> {
                assertThat(context).hasNotFailed();
                CompileFlowDeploymentProperties deployment = context.getBean(CompileFlowDeploymentProperties.class);
                assertThat(deployment.isEnabled()).isFalse();
                assertThat(deployment.getTopology()).isEqualTo(CompileFlowDeploymentProperties.Topology.EMBEDDED);
                assertThat(deployment.isControlPlaneEnabled()).isTrue();
                assertThat(deployment.isRuntimeWorkerEnabled()).isFalse();

                DeploymentArtifactProperties artifact = deployment.getArtifact();
                assertThat(artifact.getMode()).isEqualTo(DeploymentArtifactProperties.Mode.CHANNEL);
                assertThat(artifact.getOperationTimeout()).isEqualTo(Duration.ofMillis(250));

                assertThat(deployment.getRuntime().getConcurrency()).isEqualTo(2);
                assertThat(deployment.getRuntime().getConvergenceTimeout()).isEqualTo(Duration.ofSeconds(12));
                assertThat(deployment.getRouting().hasSubscriptions()).isTrue();
                assertThat(deployment.getOutbox().getRetention()).isZero();
                assertThat(deployment.getOutbox().getLeaseDuration()).isEqualTo(Duration.ofSeconds(45));
                assertThat(deployment.getOutbox().getRetry().getMaxAttempts()).isEqualTo(7);
                assertThat(deployment.getOutbox().getRetry().getInitialDelay()).isEqualTo(Duration.ofSeconds(3));
                assertThat(deployment.getOutbox().getRetry().getMaxDelay()).isEqualTo(Duration.ofSeconds(30));
                assertThat(deployment.getReconciliation().getInterval()).isEqualTo(Duration.ofSeconds(30));
                assertThat(deployment.getReconciliation().getMode()).isEqualTo(ReconciliationProperties.Mode.DETECT);
            });
    }

    @Test
    void shouldRejectRemovedNacosTransportConfiguration() {
        contextRunner
            .withPropertyValues("compileflow.deploy.sync.nacos.enabled=true")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRemovedChannelReadTimeoutProperties() {
        contextRunner
            .withPropertyValues("compileflow.deploy.artifact.read-timeout=5s")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.routing.read-timeout=5s")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.reconciliation.read-timeout=5s")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectNonPortableDeploymentKeyPrefixes() {
        contextRunner
            .withPropertyValues("compileflow.deploy.artifact.key-prefix=flows/artifact/")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.routing.key-prefix=flows/routing/")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectNonCanonicalOrDuplicateRoutingIdentifiers() {
        contextRunner
            .withPropertyValues("compileflow.deploy.routing.codes[0]=order.create",
                    "compileflow.deploy.routing.codes[1]=order.create")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.routing.aliases[0]=production/blue")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRetiredProtocolRoutingKey() {
        contextRunner
            .withPropertyValues("compileflow.deploy.routing.keys[0]=compileflow.test.order.active")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRetiredReconciliationBooleans() {
        contextRunner
            .withPropertyValues("compileflow.deploy.reconciliation.enabled=true")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.reconciliation.repair-enabled=true")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRemovedValidationConfiguration() {
        contextRunner
            .withPropertyValues("compileflow.deploy.validation.mode=FAST")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRemovedDuplicateRuntimeCapacityConfiguration() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime.max-in-flight=1000")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectUnsafeDeploymentWorkBounds() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime.concurrency=257")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime.queue-capacity=10001")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.outbox.dispatch-batch-size=1001")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectUnknownDeploymentGroup() {
        contextRunner
            .withPropertyValues("compileflow.deploy.reconcile.interval=30s")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRemovedOutboxEnabledSwitch() {
        contextRunner
            .withPropertyValues("compileflow.deploy.outbox.enabled=false")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRemovedSkipLockedOutboxSwitch() {
        contextRunner
            .withPropertyValues("compileflow.deploy.outbox.use-skip-locked=false")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRetiredFlatOutboxLeaseAndRetryProperties() {
        contextRunner
            .withPropertyValues("compileflow.deploy.outbox.claim-lease=1m")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.outbox.max-attempts=10")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.outbox.initial-retry-delay=5s")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.deploy.outbox.max-retry-delay=5m")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectOutboxRetryMaximumBelowInitialDelay() {
        contextRunner
            .withPropertyValues("compileflow.deploy.outbox.retry.max-delay=1s")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectEmbeddedOutboxLeaseThatCannotFenceConvergence() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.outbox.lease-duration=30s",
                    "compileflow.deploy.runtime.convergence-timeout=30s")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectDistributedOutboxLeaseThatCannotFenceChannelDelivery() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.outbox.lease-duration=5s", "compileflow.deploy.routing.operation-timeout=5s")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRuntimeOutsideDistributedTopology() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=EMBEDDED",
                    "compileflow.deploy.runtime-worker-enabled=true")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectRuntimeWorkerWithoutRoutingSubscriptionsDuringBinding() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.control-plane-enabled=false", "compileflow.deploy.runtime-worker-enabled=true")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectEnabledDeploymentWithoutAProcessRole() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.control-plane-enabled=false", "compileflow.deploy.runtime-worker-enabled=false")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectEmbeddedTopologyWithoutTheControlPlane() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=EMBEDDED",
                    "compileflow.deploy.control-plane-enabled=false")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectChannelArtifactModeInEmbeddedTopology() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=EMBEDDED",
                    "compileflow.deploy.artifact.mode=CHANNEL")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectSubMillisecondPositiveDuration() {
        contextRunner
            .withPropertyValues("compileflow.deploy.routing.operation-timeout=1000001ns")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectDurationsThatOverflowMillisecondConsumers() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime.failure-backoff=P106751991168D")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectFailureBackoffThatCannotBackAMonotonicDeadline() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime.failure-backoff=9223372036855ms")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectNonPositiveRuntimeConvergenceTimeout() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime.convergence-timeout=0ms")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectNonPositiveRuntimeFailureBackoff() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime.failure-backoff=0ms")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectAnExplicitlyEmptyRoutingNamespaceList() {
        contextRunner
            .withPropertyValues("compileflow.deploy.routing.namespaces=")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldAllowCallersToDisableDerivedAliasSubscriptions() {
        DeploymentRoutingProperties routing = new DeploymentRoutingProperties("compileflow.deployment.",
                List.of("default"), List.of("order.create"), List.of(), Duration.ofSeconds(5));

        assertThat(RoutingStateKeysBuilder.build(routing)).isEmpty();
        assertThat(routing.hasSubscriptions()).isFalse();
    }

    @Test
    void shouldRejectExcessiveRoutingSubscriptionCardinality() {
        DeploymentRoutingProperties derived = new DeploymentRoutingProperties("compileflow.deployment.",
                java.util.stream.IntStream
                    .range(0, 101)
                    .mapToObj(index -> "namespace" + index)
                    .toList(), java.util.stream.IntStream
                    .range(0, 100)
                    .mapToObj(index -> "process" + index)
                    .toList(), List.of("production"), Duration.ofSeconds(5));

        assertThat(derived.isSubscriptionCardinalityValid()).isFalse();
    }
}
