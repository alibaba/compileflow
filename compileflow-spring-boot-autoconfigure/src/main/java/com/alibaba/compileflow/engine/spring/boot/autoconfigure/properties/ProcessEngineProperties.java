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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties;

import com.alibaba.compileflow.spring.boot.autoconfigure.properties.DurationPropertyConstraints;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Strict Spring binding adapter for one CompileFlow engine instance.
 *
 * @author yusu
 */
@ConfigurationProperties(prefix = "compileflow.engine", ignoreUnknownFields = false)
@Validated
public final class ProcessEngineProperties {
    /**
     * Whether to create a process engine through auto-configuration.
     */
    private final boolean enabled;
    /**
     * Runtime realization used by ProcessEngine executions.
     *
     * <p>Compiled execution remains the optimized default; interpreted execution is the direct
     * reference realization of the same Process semantics.
     */
    @NotNull
    private final ProcessRuntimeMode runtimeMode;
    /**
     * Maximum time a synchronous caller waits for local runtime loading.
     */
    @NotNull
    private final Duration runtimeLoadTimeout;
    /**
     * Hard upper bound on process runtimes resident in this engine.
     */
    @Min(1)
    private final int maxResidentRuntimes;
    /**
     * Bounded executors owned and closed by the engine.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final EngineExecutorProperties executor;
    /**
     * Total lifecycle budget for shutting down the engine.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final EngineShutdownProperties shutdown;
    /**
     * Limits for synchronous process-to-process calls.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final EngineCallProperties call;
    /**
     * Generated-Java diagnostic artifact policy.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final EngineJavaDiagnosticsProperties javaDiagnostics;
    /**
     * Process-definition size limit.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final EngineDefinitionProperties definition;
    /**
     * Exact application components exposed to generated process actions.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final EngineComponentProperties components;
    /**
     * Engine event and context propagation behavior.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final EngineObservabilityProperties observability;
    /**
     * Static engine plugin discovery behavior.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final EnginePluginProperties plugins;

    /**
     * Creates an immutable engine binding snapshot.
     *
     * @param enabled       whether engine auto-configuration is enabled
     * @param runtimeMode   ProcessEngine runtime realization
     * @param maxResidentRuntimes hard resident runtime limit
     * @param executor      engine-owned executor settings
     * @param shutdown      total engine shutdown budget
     * @param call          synchronous process-call limits
     * @param runtimeLoadTimeout maximum synchronous runtime-load wait
     * @param javaDiagnostics generated-Java diagnostic settings
     * @param definition    process-definition loading limit
     * @param components    application-component exposure policy
     * @param observability event and context propagation settings
     * @param plugins       static engine plugin discovery settings
     */
    public ProcessEngineProperties(@DefaultValue("true") boolean enabled,
            @DefaultValue("COMPILED") ProcessRuntimeMode runtimeMode, @DefaultValue("10s") Duration runtimeLoadTimeout,
            @DefaultValue("2048") int maxResidentRuntimes, @DefaultValue EngineExecutorProperties executor,
            @DefaultValue EngineShutdownProperties shutdown, @DefaultValue EngineCallProperties call,
            @DefaultValue EngineJavaDiagnosticsProperties javaDiagnostics,
            @DefaultValue EngineDefinitionProperties definition, @DefaultValue EngineComponentProperties components,
            @DefaultValue EngineObservabilityProperties observability, @DefaultValue EnginePluginProperties plugins) {
        this.enabled = enabled;
        this.runtimeMode = runtimeMode;
        this.runtimeLoadTimeout = runtimeLoadTimeout;
        this.maxResidentRuntimes = maxResidentRuntimes;
        this.executor = executor;
        this.shutdown = shutdown;
        this.call = call;
        this.javaDiagnostics = javaDiagnostics;
        this.definition = definition;
        this.components = components;
        this.observability = observability;
        this.plugins = plugins;
    }

    /**
     * Converts scalar properties into an engine configuration builder.
     *
     * <p>The returned builder deliberately excludes collaborators that must be resolved from the
     * Spring container, including listeners, component resolvers, data mappers, script executors,
     * retry policies, failure handlers, and plugin beans. Application code should obtain the
     * configured engine from Spring instead of building this partial snapshot directly.
     *
     * @return configuration builder seeded with bound scalar settings
     */
    public ProcessEngineConfig.Builder toProcessEngineConfigBuilder() {
        return ProcessEngineConfig
            .builder()
            .runtimeMode(runtimeMode)
            .runtimeLoadTimeout(runtimeLoadTimeout)
            .shutdownTimeout(shutdown.getTimeout())
            .maxCallDepth(call.getMaxDepth())
            .executors(executor.toConfig())
            .maxResidentRuntimes(maxResidentRuntimes)
            .javaDiagnostics(javaDiagnostics.toConfig())
            .definitions(definition.toConfig())
            .observability(observability.toConfig())
            .discoverPlugins(plugins.isDiscoveryEnabled());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ProcessRuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    @AssertTrue(message = "compileflow.engine.runtime-load-timeout must be a positive whole-millisecond duration "
            + "representable as a long")
    public boolean isRuntimeLoadTimeoutValid() {
        return DurationPropertyConstraints.isPositiveWholeMilliseconds(runtimeLoadTimeout);
    }

    public Duration getRuntimeLoadTimeout() {
        return runtimeLoadTimeout;
    }

    public int getMaxResidentRuntimes() {
        return maxResidentRuntimes;
    }

    public EngineExecutorProperties getExecutor() {
        return executor;
    }

    public EngineShutdownProperties getShutdown() {
        return shutdown;
    }

    public EngineCallProperties getCall() {
        return call;
    }

    public EngineJavaDiagnosticsProperties getJavaDiagnostics() {
        return javaDiagnostics;
    }

    public EngineDefinitionProperties getDefinition() {
        return definition;
    }

    public EngineComponentProperties getComponents() {
        return components;
    }

    public EngineObservabilityProperties getObservability() {
        return observability;
    }

    public EnginePluginProperties getPlugins() {
        return plugins;
    }
}
