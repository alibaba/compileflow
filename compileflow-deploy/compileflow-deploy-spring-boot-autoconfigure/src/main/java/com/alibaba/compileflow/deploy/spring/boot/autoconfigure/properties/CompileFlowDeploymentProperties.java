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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Strict root for all deployment control-plane and runtime configuration.
 *
 * @author yusu
 */
@ConfigurationProperties(prefix = "compileflow.deploy", ignoreUnknownFields = false)
@Validated
public final class CompileFlowDeploymentProperties {
    /**
     * Whether the deployment subsystem is enabled.
     */
    private final boolean enabled;
    /**
     * Deployment process topology.
     */
    @NotNull
    private final Topology topology;
    /**
     * Whether this process hosts release commands and control-plane background work.
     */
    private final boolean controlPlaneEnabled;
    /**
     * Whether this distributed process hosts a subscribed runtime worker.
     */
    private final boolean runtimeWorkerEnabled;
    /**
     * Optional first-party persistence Provider selection. A selector is required only when more
     * than one Provider is available in the application composition.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Database database;
    /**
     * Immutable artifact transport and integrity policy.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final DeploymentArtifactProperties artifact;
    /**
     * Runtime installation pipeline lifecycle and capacity.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final DeploymentRuntimeProperties runtime;
    /**
     * Routing key space and runtime subscriptions.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final DeploymentRoutingProperties routing;
    /**
     * Transactional routing outbox dispatch policy.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final RoutingOutboxProperties outbox;
    /**
     * Periodic control-plane routing reconciliation policy.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final ReconciliationProperties reconciliation;

    /**
     * Creates an immutable deployment binding snapshot.
     *
     * @param enabled              whether deployment is enabled
     * @param topology             deployment process topology
     * @param controlPlaneEnabled  whether this process hosts the control plane
     * @param runtimeWorkerEnabled whether this distributed process hosts a runtime worker
     * @param database             optional first-party persistence Provider selection
     * @param artifact             artifact transport and integrity policy
     * @param runtime              runtime installation and convergence policy
     * @param routing              routing key space and subscriptions
     * @param outbox               transactional outbox policy
     * @param reconciliation       routing reconciliation policy
     */
    public CompileFlowDeploymentProperties(@DefaultValue("false") boolean enabled,
            @DefaultValue("EMBEDDED") Topology topology, @DefaultValue("true") boolean controlPlaneEnabled,
            @DefaultValue("false") boolean runtimeWorkerEnabled, @DefaultValue Database database,
            @DefaultValue DeploymentArtifactProperties artifact, @DefaultValue DeploymentRuntimeProperties runtime,
            @DefaultValue DeploymentRoutingProperties routing, @DefaultValue RoutingOutboxProperties outbox,
            @DefaultValue ReconciliationProperties reconciliation) {
        this.enabled = enabled;
        this.topology = topology;
        this.controlPlaneEnabled = controlPlaneEnabled;
        this.runtimeWorkerEnabled = runtimeWorkerEnabled;
        this.database = database;
        this.artifact = artifact;
        this.runtime = runtime;
        this.routing = routing;
        this.outbox = outbox;
        this.reconciliation = reconciliation;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Topology getTopology() {
        return topology;
    }

    @AssertTrue(message = "compileflow.deploy.enabled=true requires at least one of "
            + "compileflow.deploy.control-plane-enabled or compileflow.deploy.runtime-worker-enabled")
    public boolean isDeploymentPlaneEnabled() {
        return !enabled || controlPlaneEnabled || runtimeWorkerEnabled;
    }

    @AssertTrue(message = "compileflow.deploy.runtime-worker-enabled=true requires compileflow.deploy.enabled=true "
            + "and compileflow.deploy.topology=DISTRIBUTED")
    public boolean isRuntimeTopologyValid() {
        return !runtimeWorkerEnabled || enabled && topology == Topology.DISTRIBUTED;
    }

    @AssertTrue(message = "compileflow.deploy.runtime-worker-enabled=true requires at least one compileflow.deploy."
            + "routing.codes entry and alias")
    public boolean isRuntimeSubscriptionValid() {
        return !runtimeWorkerEnabled || routing != null && routing.hasSubscriptions();
    }

    @AssertTrue(message = "compileflow.deploy.topology=EMBEDDED requires control-plane-enabled=true, artifact."
            + "mode=SOURCE, and runtime-worker-enabled=false")
    public boolean isEmbeddedTopologyValid() {
        return !enabled || topology != Topology.EMBEDDED
                || controlPlaneEnabled && artifact.getMode() == DeploymentArtifactProperties.Mode.SOURCE
                && !runtimeWorkerEnabled;
    }

    @AssertTrue(message = "compileflow.deploy.outbox.lease-duration must be longer than compileflow.deploy.runtime."
            + "convergence-timeout in EMBEDDED topology")
    public boolean isEmbeddedOutboxLeaseValid() {
        return !enabled || !controlPlaneEnabled || topology != Topology.EMBEDDED
                || outbox.getLeaseDuration() != null && runtime.getConvergenceTimeout() != null
                && outbox.getLeaseDuration().compareTo(runtime.getConvergenceTimeout()) > 0;
    }

    @AssertTrue(message = "compileflow.deploy.outbox.lease-duration must be longer than compileflow.deploy.routing."
            + "operation-timeout in DISTRIBUTED topology")
    public boolean isDistributedOutboxLeaseValid() {
        return !enabled || !controlPlaneEnabled || topology != Topology.DISTRIBUTED
                || outbox.getLeaseDuration() != null && routing.getOperationTimeout() != null
                && outbox.getLeaseDuration().compareTo(routing.getOperationTimeout()) > 0;
    }

    public boolean isControlPlaneEnabled() {
        return controlPlaneEnabled;
    }

    public boolean isRuntimeWorkerEnabled() {
        return runtimeWorkerEnabled;
    }

    public Database getDatabase() {
        return database;
    }

    public DeploymentArtifactProperties getArtifact() {
        return artifact;
    }

    public DeploymentRuntimeProperties getRuntime() {
        return runtime;
    }

    public DeploymentRoutingProperties getRouting() {
        return routing;
    }

    public RoutingOutboxProperties getOutbox() {
        return outbox;
    }

    public ReconciliationProperties getReconciliation() {
        return reconciliation;
    }

    /**
     * Deployment-context-local persistence composition.
     */
    public static final class Database {
        private final Provider provider;
        private final boolean migrate;

        /**
         * Creates the deployment persistence selection.
         *
         * @param provider optional first-party Deploy Store Provider
         */
        public Database(Provider provider, @DefaultValue("false") boolean migrate) {
            this.provider = provider;
            this.migrate = migrate;
        }

        /**
         * Returns the explicitly selected first-party Deploy Store Provider.
         *
         * @return selected Provider, or {@code null} when only one composition is present
         */
        public Provider getProvider() {
            return provider;
        }

        public boolean isMigrate() {
            return migrate;
        }

        /**
         * First-party Deploy persistence Providers.
         */
        public enum Provider {
            POSTGRESQL,
            MYSQL
        }
    }

    /**
     * Supported deployment process topologies.
     */
    public enum Topology {
        /**
         * Control plane and execution engine share one process and database.
         */
        EMBEDDED,
        /**
         * Control plane and runtime nodes exchange routing state through a projection store.
         */
        DISTRIBUTED
    }
}
