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
package com.alibaba.compileflow.engine.config;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessDataMapper;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.ProcessEnginePlugin;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import com.alibaba.compileflow.engine.spi.observability.TraceIdProvider;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Configuration for the ProcessEngine, constructed via a layered builder API.
 * <p>
 * Extension capabilities reach the engine through three ordered layers, all frozen
 * into this immutable snapshot at {@link Builder#build()} time:
 * <ol>
 *   <li>Classpath discovery: {@link ProcessEnginePlugin} implementations declared under
 *       {@code META-INF/services} are discovered by {@link ServiceLoader} and applied
 *       only when explicitly enabled (see {@link Builder#discoverPlugins(boolean)}).</li>
 *   <li>Explicit plugins registered through {@link Builder#plugin(ProcessEnginePlugin)}.</li>
 *   <li>Direct capability registrations, including those made by framework integrations.</li>
 * </ol>
 * Plugins contribute listeners and named capabilities only. Named semantic capabilities must be
 * unique within the plugin layer and fail closed on duplicate registration. Direct retry and
 * failure registrations may explicitly replace plugin contributions.
 * Configurations are immutable and reusable. Supplied SPI instances may therefore be shared by
 * multiple engines created from the same configuration and must satisfy their thread-safety
 * contracts.
 *
 * @author yusu
 */
public final class ProcessEngineConfig {
    private static final int DEFAULT_MAX_CALL_DEPTH = 32;
    private static final int MAX_CALL_DEPTH = 256;
    private static final int DEFAULT_MAX_RESIDENT_RUNTIMES = 2048;
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);
    private final ProcessModelType modelType;
    private final ProcessRuntimeMode runtimeMode;
    private final int maxCallDepth;
    private final ProcessExecutorConfig executorConfig;
    private final int maxResidentRuntimes;
    private final Duration shutdownTimeout;
    private final Duration runtimeLoadTimeout;
    private final JavaDiagnosticsConfig javaDiagnostics;
    private final ProcessDefinitionConfig definitionConfig;
    private final ProcessObservabilityConfig observabilityConfig;
    private final ClassLoader classLoader;
    private final ProcessDataMapper dataMapper;
    private final List<ProcessEventListener> eventListeners;
    private final TraceIdProvider traceIdProvider;
    private final ProcessComponentResolver componentResolver;
    private final ProcessAliasRouteSource aliasRouteSource;
    private final Map<String, ProcessAliasTargetingPolicy> aliasTargetingPolicies;
    private final List<ScriptExecutor> scriptExecutors;
    private final Map<String, RetryPolicy> retryPolicies;
    private final Map<String, FailureHandler> failureHandlers;
    private final ProcessContextPropagator contextPropagator;

    private ProcessEngineConfig(Builder builder, List<ProcessEventListener> eventListeners,
            TraceIdProvider traceIdProvider, ProcessComponentResolver componentResolver,
            Map<String, ProcessAliasTargetingPolicy> aliasTargetingPolicies, List<ScriptExecutor> scriptExecutors,
            Map<String, RetryPolicy> retryPolicies, Map<String, FailureHandler> failureHandlers) {
        this.modelType = builder.modelType;
        this.runtimeMode = builder.runtimeMode;
        this.maxCallDepth = builder.maxCallDepth;
        this.executorConfig = builder.executorConfig;
        this.maxResidentRuntimes = builder.maxResidentRuntimes;
        this.shutdownTimeout = builder.shutdownTimeout;
        this.runtimeLoadTimeout = builder.runtimeLoadTimeout;
        this.javaDiagnostics = builder.javaDiagnostics;
        this.definitionConfig = builder.definitionConfig;
        this.observabilityConfig = builder.observabilityConfig;
        this.classLoader = builder.classLoader;
        this.dataMapper = builder.dataMapper;
        this.eventListeners = List.copyOf(eventListeners);
        this.traceIdProvider = traceIdProvider;
        this.componentResolver = componentResolver;
        this.aliasRouteSource = builder.aliasRouteSource;
        this.aliasTargetingPolicies = Map.copyOf(aliasTargetingPolicies);
        this.scriptExecutors = List.copyOf(scriptExecutors);
        this.retryPolicies = Map.copyOf(retryPolicies);
        this.failureHandlers = Map.copyOf(failureHandlers);
        this.contextPropagator = builder.contextPropagator;
    }

    /**
     * Creates a default configuration for TBBPM.
     *
     * @return default TBBPM engine configuration
     */
    public static ProcessEngineConfig tbbpm() {
        return new Builder(ProcessModelType.TBBPM).build();
    }

    /**
     * Creates a default configuration for BPMN.
     *
     * @return default BPMN engine configuration
     */
    public static ProcessEngineConfig bpmn() {
        return new Builder(ProcessModelType.BPMN).build();
    }

    /**
     * Returns a new builder for creating a TBBPM configuration.
     *
     * @return builder initialized for the TBBPM model type
     */
    public static Builder tbbpmBuilder() {
        return new Builder(ProcessModelType.TBBPM);
    }

    /**
     * Returns a new builder for creating a BPMN configuration.
     *
     * @return builder initialized for the BPMN model type
     */
    public static Builder bpmnBuilder() {
        return new Builder(ProcessModelType.BPMN);
    }

    /**
     * Returns a new builder for the specified model type.
     *
     * @param modelType process model type handled by the created engine
     * @return builder initialized for the specified model type
     */
    public static Builder builder(ProcessModelType modelType) {
        return new Builder(modelType);
    }

    /**
     * Returns process model type owned by this engine.
     *
     * @return process model type owned by this engine
     */
    public ProcessModelType getModelType() {
        return modelType;
    }

    /**
     * Returns the selected realization of ProcessEngine runtimes.
     *
     * @return selected ProcessEngine runtime realization
     */
    public ProcessRuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    /**
     * Returns the root-inclusive synchronous process-call depth limit.
     *
     * @return value from one through 256
     */
    public int getMaxCallDepth() {
        return maxCallDepth;
    }

    /**
     * Returns immutable engine-owned executor configuration.
     *
     * @return immutable engine-owned executor configuration
     */
    public ProcessExecutorConfig getExecutorConfig() {
        return executorConfig;
    }

    /**
     * Returns the hard upper bound on process runtimes resident in this engine.
     *
     * <p>The bound includes runtimes retained by explicit owners such as deployment aliases.
     * Loading a new runtime fails when every resident runtime is retained and capacity cannot be
     * reclaimed.
     *
     * @return positive resident runtime limit
     */
    public int getMaxResidentRuntimes() {
        return maxResidentRuntimes;
    }

    /**
     * Returns the total budget for operation draining and engine resource shutdown.
     *
     * @return positive engine shutdown timeout
     */
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * Returns how long a synchronous caller waits for local runtime loading.
     *
     * <p>A timeout does not cancel a load that was already admitted to the engine's shared
     * single-flight work. That load may still complete and populate the local runtime cache.
     *
     * @return positive synchronous runtime-load wait timeout
     */
    public Duration getRuntimeLoadTimeout() {
        return runtimeLoadTimeout;
    }

    /**
     * Returns generated-Java diagnostic and opt-in artifact-export settings.
     *
     * @return immutable Java diagnostics configuration
     */
    public JavaDiagnosticsConfig getJavaDiagnostics() {
        return javaDiagnostics;
    }

    /**
     * Returns process-definition size limits.
     *
     * @return immutable process-definition configuration
     */
    public ProcessDefinitionConfig getDefinitionConfig() {
        return definitionConfig;
    }

    /**
     * Returns immutable event and observability configuration.
     *
     * @return immutable event and observability configuration
     */
    public ProcessObservabilityConfig getObservabilityConfig() {
        return observabilityConfig;
    }

    /**
     * Returns ordered event listeners frozen into this configuration.
     *
     * @return immutable listener list, possibly empty
     */
    public List<ProcessEventListener> getEventListeners() {
        return eventListeners;
    }

    /**
     * Returns the optional trace-id provider used for new executions.
     *
     * @return configured provider, or {@code null} to use the engine default
     */
    public TraceIdProvider getTraceIdProvider() {
        return traceIdProvider;
    }

    /**
     * Returns parent class loader used by generated process classes.
     *
     * @return parent class loader used by generated process classes
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Returns the optional application data mapper.
     *
     * @return configured mapper, or {@code null} to use the core default
     */
    public ProcessDataMapper getDataMapper() {
        return dataMapper;
    }

    /**
     * Returns the component resolver used by generated process code.
     *
     * @return non-null component resolver
     */
    public ProcessComponentResolver getComponentResolver() {
        return componentResolver;
    }

    /**
     * Returns the explicitly configured Alias serving-route authority.
     *
     * @return configured thread-safe source, or {@code null} to use engine-local routing state
     */
    public ProcessAliasRouteSource getAliasRouteSource() {
        return aliasRouteSource;
    }

    /**
     * Returns named Alias targeting policies available to explicit route configuration.
     *
     * @return immutable policy map keyed by stable semantic name
     */
    public Map<String, ProcessAliasTargetingPolicy> getAliasTargetingPolicies() {
        return aliasTargetingPolicies;
    }

    /**
     * Returns configured script executors with globally unique semantic language names.
     *
     * <p>Neither plugin priority nor direct-registration precedence overrides an executor with the
     * same canonical language name. Duplicate configured languages fail during configuration;
     * conflicts with a core-provided language fail when the engine assembles its registry.
     * Core-provided language names are reserved and cannot be replaced by an application.
     *
     * @return immutable script executor list
     */
    public List<ScriptExecutor> getScriptExecutors() {
        return scriptExecutors;
    }

    /**
     * Returns named custom retry policies referenced by process action policies.
     *
     * @return immutable retry-policy map keyed by exact policy name
     */
    public Map<String, RetryPolicy> getRetryPolicies() {
        return retryPolicies;
    }

    /**
     * Returns named custom terminal failure handlers referenced by process action policies.
     *
     * @return immutable failure-handler map keyed by exact handler name
     */
    public Map<String, FailureHandler> getFailureHandlers() {
        return failureHandlers;
    }

    /**
     * Returns the application context propagator used at ProcessEngine-owned thread boundaries.
     *
     * @return non-null context propagator
     */
    public ProcessContextPropagator getContextPropagator() {
        return contextPropagator;
    }

    ValidationResult validate() {
        ValidationResult result = ValidationResult.success();
        result = result.merge(ProcessConfigValidator.validatePositive(maxCallDepth, "maxCallDepth"));
        if (maxCallDepth > MAX_CALL_DEPTH) {
            result = result.addError("maxCallDepth must not exceed " + MAX_CALL_DEPTH);
        }
        result = result.merge(executorConfig.validate());
        result = result.merge(javaDiagnostics.validate());
        result = result.merge(ProcessConfigValidator.validatePositiveDurationMillis(runtimeLoadTimeout,
                "runtimeLoadTimeout"));
        result = result.merge(definitionConfig.validate());
        result = result.merge(ProcessConfigValidator.validatePositive(maxResidentRuntimes, "maxResidentRuntimes"));
        result = result.merge(ProcessConfigValidator.validatePositiveDurationMillis(shutdownTimeout, "shutdownTimeout"));
        result = result.merge(observabilityConfig.validate());
        return result;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "ProcessEngineConfig{type=%s, runtimeMode=%s, maxCallDepth=%d, executors=%s, "
                + "maxResidentRuntimes=%d, shutdownTimeout=%s, runtimeLoadTimeout=%s, javaDiagnostics=%s, "
                + "definition=%s, observability=%s, eventListeners=%d, aliasTargetingPolicies=%d, "
                + "scriptExecutors=%d, retryPolicies=%d, failureHandlers=%d}", modelType, runtimeMode, maxCallDepth,
                executorConfig, maxResidentRuntimes, shutdownTimeout, runtimeLoadTimeout, javaDiagnostics,
                definitionConfig, observabilityConfig, eventListeners.size(), aliasTargetingPolicies.size(),
                scriptExecutors.size(), retryPolicies.size(), failureHandlers.size());
    }

    /**
     * Builds an immutable configuration snapshot reusable by one or more process engines.
     * <p>
     * Typed extension contributions are merged in three layers at {@link #build()} time:
     * discovered {@link ProcessEnginePlugin}s form the first layer, explicitly registered
     * Plugins are ordered by priority and stable ID within their source layer. Priority determines
     * listener order only; duplicate named capabilities fail closed.
     */
    public static final class Builder {
        private final ProcessModelType modelType;
        private final List<ProcessEventListener> eventListeners = new ArrayList<>();
        private final Map<String, ProcessAliasTargetingPolicy> aliasTargetingPolicies = new LinkedHashMap<>();
        private final Map<String, ScriptExecutor> scriptExecutors = new LinkedHashMap<>();
        private final Map<String, RetryPolicy> retryPolicies = new LinkedHashMap<>();
        private final Map<String, FailureHandler> failureHandlers = new LinkedHashMap<>();
        private final List<ProcessEnginePlugin> plugins = new ArrayList<>();
        private ProcessRuntimeMode runtimeMode = ProcessRuntimeMode.COMPILED;
        private int maxCallDepth = DEFAULT_MAX_CALL_DEPTH;
        private ProcessExecutorConfig executorConfig = ProcessExecutorConfig.defaults();
        private int maxResidentRuntimes = DEFAULT_MAX_RESIDENT_RUNTIMES;
        private Duration shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
        private Duration runtimeLoadTimeout = Duration.ofSeconds(10);
        private JavaDiagnosticsConfig javaDiagnostics = JavaDiagnosticsConfig.defaults();
        private ProcessDefinitionConfig definitionConfig = ProcessDefinitionConfig.defaults();
        private ProcessObservabilityConfig observabilityConfig = ProcessObservabilityConfig.defaults();
        private ClassLoader classLoader = defaultClassLoader();
        private ProcessDataMapper dataMapper;
        private TraceIdProvider traceIdProvider;
        private ProcessComponentResolver componentResolver;
        private ProcessAliasRouteSource aliasRouteSource;
        private ProcessContextPropagator contextPropagator = ProcessContextPropagator.none();
        private boolean discoverPlugins;

        private Builder(ProcessModelType modelType) {
            this.modelType = Objects.requireNonNull(modelType, "modelType");
        }

        /**
         * Selects the ProcessEngine runtime realization.
         *
         * @param runtimeMode ProcessEngine runtime realization
         * @return this builder
         */
        public Builder runtimeMode(ProcessRuntimeMode runtimeMode) {
            this.runtimeMode = Objects.requireNonNull(runtimeMode, "runtimeMode");
            return this;
        }

        private static ClassLoader defaultClassLoader() {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            return contextClassLoader != null ? contextClassLoader : ProcessEngineConfig.class.getClassLoader();
        }

        /**
         * Sets the root-inclusive synchronous process-call depth limit.
         *
         * @param value value from one through 256
         * @return this builder
         */
        public Builder maxCallDepth(int value) {
            this.maxCallDepth = value;
            return this;
        }

        /**
         * Sets the bounded executor configuration owned by the engine.
         *
         * @param config executor configuration
         * @return this builder
         */
        public Builder executors(ProcessExecutorConfig config) {
            this.executorConfig = Objects.requireNonNull(config, "executorConfig");
            return this;
        }

        /**
         * Sets the hard upper bound on runtimes resident in this engine.
         *
         * @param value positive resident runtime limit
         * @return this builder
         */
        public Builder maxResidentRuntimes(int value) {
            this.maxResidentRuntimes = value;
            return this;
        }

        /**
         * Sets the total budget for operation draining and engine resource shutdown.
         *
         * @param value duration of at least one millisecond
         * @return this builder
         */
        public Builder shutdownTimeout(Duration value) {
            this.shutdownTimeout = Objects.requireNonNull(value, "shutdownTimeout");
            return this;
        }

        /**
         * Sets how long synchronous callers wait for local runtime loading.
         *
         * @param timeout positive runtime-load wait timeout
         * @return this builder
         */
        public Builder runtimeLoadTimeout(Duration timeout) {
            this.runtimeLoadTimeout = Objects.requireNonNull(timeout, "runtimeLoadTimeout");
            return this;
        }

        /**
         * Sets generated-Java diagnostic and artifact-export settings.
         *
         * @param diagnostics Java diagnostics configuration
         * @return this builder
         */
        public Builder javaDiagnostics(JavaDiagnosticsConfig diagnostics) {
            this.javaDiagnostics = Objects.requireNonNull(diagnostics, "javaDiagnostics");
            return this;
        }

        /**
         * Sets process-definition size limits.
         *
         * @param config process-definition configuration to use
         * @return this builder
         */
        public Builder definitions(ProcessDefinitionConfig config) {
            this.definitionConfig = Objects.requireNonNull(config, "definitionConfig");
            return this;
        }

        /**
         * Sets the mapper used by typed execution convenience methods.
         *
         * @param mapper thread-safe application data mapper
         * @return this builder
         */
        public Builder dataMapper(ProcessDataMapper mapper) {
            this.dataMapper = Objects.requireNonNull(mapper, "dataMapper");
            return this;
        }

        /**
         * Replaces event-dispatch and context-propagation settings.
         * <p>
         * Event listeners and the trace-id provider are registered directly on this builder; they
         * are deliberately not carried by this scalar configuration object.
         *
         * @param config observability configuration
         * @return this builder
         */
        public Builder observability(ProcessObservabilityConfig config) {
            this.observabilityConfig = Objects.requireNonNull(config, "observabilityConfig");
            return this;
        }

        /**
         * Appends one process event listener.
         *
         * @param listener non-null listener
         * @return this builder
         */
        public Builder eventListener(ProcessEventListener listener) {
            this.eventListeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        /**
         * Sets the trace-id provider used for new executions.
         *
         * @param provider non-null trace-id provider
         * @return this builder
         */
        public Builder traceIdProvider(TraceIdProvider provider) {
            this.traceIdProvider = Objects.requireNonNull(provider, "traceIdProvider");
            return this;
        }

        /**
         * Sets the single authoritative Alias serving-route source.
         *
         * <p>This authority is always configured directly and is never contributed by plugin
         * discovery.
         *
         * @param source thread-safe serving-route authority
         * @return this builder
         */
        public Builder aliasRouteSource(ProcessAliasRouteSource source) {
            this.aliasRouteSource = Objects.requireNonNull(source, "aliasRouteSource");
            return this;
        }

        /**
         * Registers one named Alias targeting policy.
         *
         * <p>The policy is inert unless an authoritative Alias route explicitly references its
         * name. Duplicate names fail because incompatible semantics must use a new name.
         *
         * @param policy thread-safe targeting policy
         * @return this builder
         */
        public Builder aliasTargetingPolicy(ProcessAliasTargetingPolicy policy) {
            ProcessAliasTargetingPolicy candidate = Objects.requireNonNull(policy, "aliasTargetingPolicy");
            String name = ProcessAliasTargetingPolicy.requireCanonicalName(candidate.name());
            ProcessAliasTargetingPolicy existing = aliasTargetingPolicies.putIfAbsent(name, candidate);
            if (existing != null) {
                throw duplicateAliasTargetingPolicy(name, existing, candidate);
            }
            return this;
        }

        private static CompileFlowException.ConfigurationException duplicateAliasTargetingPolicy(String name,
                ProcessAliasTargetingPolicy existing, ProcessAliasTargetingPolicy duplicate) {
            return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Duplicate Alias targeting policies for name '" + name + "': " + existing.getClass().getName()
                    + " and " + duplicate.getClass().getName());
        }

        /**
         * Sets the application component resolver used by generated process code.
         *
         * @param resolver non-null component resolver
         * @return this builder
         */
        public Builder componentResolver(ProcessComponentResolver resolver) {
            this.componentResolver = Objects.requireNonNull(resolver, "componentResolver");
            return this;
        }

        /**
         * Sets the application ambient-context propagator used across engine-owned threads.
         *
         * <p>The application retains lifecycle ownership. Durable execution never persists or
         * restores this context.
         *
         * @param propagator thread-safe, bounded context propagator
         * @return this builder
         */
        public Builder contextPropagator(ProcessContextPropagator propagator) {
            this.contextPropagator = Objects.requireNonNull(propagator, "contextPropagator");
            return this;
        }

        /**
         * Registers one script executor. Semantic language names must be unique;
         * incompatible language revisions use a new name.
         *
         * @param executor non-null script executor
         * @return this builder
         */
        public Builder scriptExecutor(ScriptExecutor executor) {
            Objects.requireNonNull(executor, "scriptExecutor");
            String language = ScriptExecutor.requireCanonicalName(executor.name());
            ScriptExecutor existing = this.scriptExecutors.putIfAbsent(language, executor);
            if (existing != null) {
                throw duplicateScriptExecutor(language, existing, executor);
            }
            return this;
        }

        private static CompileFlowException.ConfigurationException duplicateScriptExecutor(String language,
                ScriptExecutor existing, ScriptExecutor duplicate) {
            return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Duplicate script executors for language '" + language + "': " + existing.getClass().getName()
                    + " and " + duplicate.getClass().getName());
        }

        /**
         * Registers a custom retry policy by the exact name used in process definitions.
         * Later direct registrations with the same name replace earlier direct registrations.
         * Built-in names {@code never}, {@code transient}, and {@code always} are reserved.
         *
         * @param name   stable non-blank policy name
         * @param policy thread-safe retry policy
         * @return this builder
         */
        public Builder retryPolicy(String name, RetryPolicy policy) {
            String key = ProcessExtensionNames.retryPolicy(name);
            this.retryPolicies.put(key, Objects.requireNonNull(policy, "retryPolicy"));
            return this;
        }

        /**
         * Registers a custom terminal failure handler by the exact name used in process
         * definitions. Later direct registrations with the same name replace earlier direct
         * registrations. Built-in names {@code propagate} and {@code continue} are reserved.
         *
         * @param name    stable non-blank handler name
         * @param handler thread-safe terminal failure handler
         * @return this builder
         */
        public Builder failureHandler(String name, FailureHandler handler) {
            String key = ProcessExtensionNames.failureHandler(name);
            this.failureHandlers.put(key, Objects.requireNonNull(handler, "failureHandler"));
            return this;
        }

        /**
         * Registers an aggregated plugin in the explicit plugin layer applied during
         * {@link #build()}. This layer follows every discovered plugin regardless of numeric
         * plugin priority and precedes direct capability contributions.
         *
         * @param plugin non-null plugin
         * @return this builder
         */
        public Builder plugin(ProcessEnginePlugin plugin) {
            this.plugins.add(Objects.requireNonNull(plugin, "plugin"));
            return this;
        }

        /**
         * Enables or disables ServiceLoader discovery of {@link ProcessEnginePlugin}.
         * Discovery is disabled by default because a classpath change must not implicitly alter
         * ProcessEngine execution semantics. Applications may explicitly enable discovery when
         * their dependency graph is an approved extension boundary.
         *
         * @param enabled whether classpath plugins are discovered during {@link #build()}
         * @return this builder
         */
        public Builder discoverPlugins(boolean enabled) {
            this.discoverPlugins = enabled;
            return this;
        }

        /**
         * Sets the application class loader used for plugin and provider discovery and as the
         * parent of generated process classes.
         *
         * @param classLoader non-null application class loader
         * @return this builder
         */
        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
            return this;
        }

        /**
         * Builds and validates the configuration.
         *
         * @return A validated, immutable ProcessEngineConfig instance.
         */
        public ProcessEngineConfig build() {
            ProcessEnginePluginResolver.Contributions pluginLayer =
                    ProcessEnginePluginResolver.resolve(modelType, classLoader, discoverPlugins, plugins);

            List<ProcessEventListener> mergedListeners = new ArrayList<>(pluginLayer.eventListeners());
            mergedListeners.addAll(eventListeners);

            Map<String, ScriptExecutor> mergedExecutors = new LinkedHashMap<>(pluginLayer.scriptExecutors());
            scriptExecutors.forEach((language, executor) -> {
                ScriptExecutor existing = mergedExecutors.putIfAbsent(language, executor);
                if (existing != null) {
                    throw duplicateScriptExecutor(language, existing, executor);
                }
            });
            Map<String, ProcessAliasTargetingPolicy> mergedTargetingPolicies =
                    new LinkedHashMap<>(pluginLayer.aliasTargetingPolicies());
            aliasTargetingPolicies.forEach((name, policy) -> {
                ProcessAliasTargetingPolicy existing = mergedTargetingPolicies.putIfAbsent(name, policy);
                if (existing != null) {
                    throw duplicateAliasTargetingPolicy(name, existing, policy);
                }
            });
            Map<String, RetryPolicy> mergedRetryPolicies = new LinkedHashMap<>(pluginLayer.retryPolicies());
            mergedRetryPolicies.putAll(retryPolicies);
            Map<String, FailureHandler> mergedFailureHandlers = new LinkedHashMap<>(pluginLayer.failureHandlers());
            mergedFailureHandlers.putAll(failureHandlers);

            ProcessEngineConfig config = new ProcessEngineConfig(this, mergedListeners, traceIdProvider,
                    componentResolver == null ? ProcessComponentResolver.disabled() : componentResolver,
                    mergedTargetingPolicies, new ArrayList<>(mergedExecutors.values()), mergedRetryPolicies,
                    mergedFailureHandlers);
            config.validate().throwIfInvalid();
            return config;
        }
    }
}
