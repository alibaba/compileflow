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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import com.alibaba.compileflow.engine.ProcessDataMapper;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
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
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.resolution.SpringProcessComponentResolver;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties.ProcessEngineProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

/**
 * Creates immutable format-bound engine configurations from one Spring configuration boundary.
 *
 * @author yusu
 */
public final class ProcessEngineConfigurationFactory {
    private final ProcessEngineProperties properties;
    private final ObjectProvider<ProcessEventListener> eventListeners;
    private final ObjectProvider<TraceIdProvider> traceIdProviders;
    private final ObjectProvider<ProcessComponentResolver> componentResolvers;
    private final ObjectProvider<ProcessContextPropagator> contextPropagators;
    private final ObjectProvider<ProcessDataMapper> dataMappers;
    private final ObjectProvider<ProcessAliasRouteSource> aliasRouteSources;
    private final ObjectProvider<ScriptExecutor> scriptExecutors;
    private final ObjectProvider<ProcessEnginePlugin> plugins;
    private final ApplicationContext applicationContext;

    /**
     * Creates the Spring-backed configuration factory.
     *
     * @param properties         strict engine properties
     * @param eventListeners     ordered event listener beans
     * @param traceIdProviders   optional trace provider
     * @param componentResolvers optional component resolver
     * @param contextPropagators optional application context propagator
     * @param dataMappers        application data mapper
     * @param aliasRouteSources  optional Alias route authority
     * @param scriptExecutors    ordered script executors
     * @param plugins            explicit Spring plugin beans
     * @param applicationContext Spring bean authority
     */
    public ProcessEngineConfigurationFactory(ProcessEngineProperties properties,
            ObjectProvider<ProcessEventListener> eventListeners, ObjectProvider<TraceIdProvider> traceIdProviders,
            ObjectProvider<ProcessComponentResolver> componentResolvers, ObjectProvider<ProcessDataMapper> dataMappers,
            ObjectProvider<ProcessContextPropagator> contextPropagators,
            ObjectProvider<ProcessAliasRouteSource> aliasRouteSources, ObjectProvider<ScriptExecutor> scriptExecutors,
            ObjectProvider<ProcessEnginePlugin> plugins, ApplicationContext applicationContext) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.eventListeners = Objects.requireNonNull(eventListeners, "eventListeners");
        this.traceIdProviders = Objects.requireNonNull(traceIdProviders, "traceIdProviders");
        this.componentResolvers = Objects.requireNonNull(componentResolvers, "componentResolvers");
        this.contextPropagators = Objects.requireNonNull(contextPropagators, "contextPropagators");
        this.dataMappers = Objects.requireNonNull(dataMappers, "dataMappers");
        this.aliasRouteSources = Objects.requireNonNull(aliasRouteSources, "aliasRouteSources");
        this.scriptExecutors = Objects.requireNonNull(scriptExecutors, "scriptExecutors");
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
    }

    /**
     * Creates one immutable configuration using the same application capabilities.
     *
     * @param modelType format owned by the target engine
     * @return validated engine configuration
     */
    public ProcessEngineConfig create(ProcessModelType modelType) {
        ProcessEngineConfig.Builder builder =
                properties.toProcessEngineConfigBuilder(Objects.requireNonNull(modelType, "modelType"));
        eventListeners.orderedStream().forEach(builder::eventListener);
        TraceIdProvider traceIdProvider = traceIdProviders.getIfAvailable();
        if (traceIdProvider != null) {
            builder.traceIdProvider(traceIdProvider);
        }
        configureComponentResolver(builder);
        ProcessContextPropagator contextPropagator = contextPropagators.getIfAvailable();
        if (contextPropagator != null) {
            builder.contextPropagator(contextPropagator);
        }
        configureDataMapper(builder);
        ProcessAliasRouteSource aliasRouteSource = aliasRouteSources.getIfAvailable();
        if (aliasRouteSource != null) {
            builder.aliasRouteSource(aliasRouteSource);
        }
        configureScriptExecutors(builder);
        applicationContext.getBeansOfType(ProcessAliasTargetingPolicy.class).values().forEach(
                builder::aliasTargetingPolicy);
        applicationContext.getBeansOfType(RetryPolicy.class).forEach(builder::retryPolicy);
        applicationContext.getBeansOfType(FailureHandler.class).forEach(builder::failureHandler);
        plugins.stream().forEach(builder::plugin);
        return builder.build();
    }

    private void configureScriptExecutors(ProcessEngineConfig.Builder builder) {
        List<ScriptExecutor> orderedExecutors = scriptExecutors.orderedStream().toList();
        Map<String, ScriptExecutor> byLanguage = new LinkedHashMap<>();
        for (ScriptExecutor executor : orderedExecutors) {
            String language = ScriptExecutor.requireCanonicalName(executor.name());
            ScriptExecutor existing = byLanguage.putIfAbsent(language, executor);
            if (existing != null && existing != executor) {
                throw new IllegalStateException(
                        "Multiple ScriptExecutor beans claim language '" + language + "': "
                        + existing.getClass().getName() + " and " + executor.getClass().getName());
            }
        }
        byLanguage.values().forEach(builder::scriptExecutor);
    }

    private void configureComponentResolver(ProcessEngineConfig.Builder builder) {
        ProcessComponentResolver componentResolver = componentResolvers.getIfAvailable();
        if (componentResolver != null) {
            if (!properties.getComponents().getAllowedBeans().isEmpty()) {
                throw new IllegalStateException(
                        "compileflow.engine.components.allowed-beans cannot be combined with a custom ProcessComponentResolver bean");
            }
            builder.componentResolver(componentResolver);
        } else if (!properties.getComponents().getAllowedBeans().isEmpty()) {
            builder.componentResolver(
                    new SpringProcessComponentResolver(applicationContext, properties
                        .getComponents()
                        .getAllowedBeans()));
        }
    }

    private void configureDataMapper(ProcessEngineConfig.Builder builder) {
        ProcessDataMapper dataMapper = dataMappers.getIfAvailable();
        if (dataMapper == null) {
            throw new IllegalStateException("Spring configuration must provide exactly one ProcessDataMapper bean");
        }
        builder.dataMapper(dataMapper);
    }
}
