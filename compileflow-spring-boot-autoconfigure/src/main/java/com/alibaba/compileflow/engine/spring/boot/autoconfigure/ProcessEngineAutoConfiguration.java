/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.config.*;
import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.core.extension.ExtensionInvokerImpl;
import com.alibaba.compileflow.engine.core.infrastructure.bean.SpringApplicationContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;


/**
 * Spring Boot auto-configuration for the CompileFlow process engine.
 * <p>
 * This class is triggered when {@code compileflow-spring-boot-starter} is on the
 * classpath and a {@link ProcessEngine} class is present. It automatically configures
 * and creates a singleton {@link ProcessEngine} bean based on the application's
 * configuration properties, which are bound to the {@link ProcessEngineProperties} class.
 * <p>
 * It also sets up essential supporting beans:
 * <ul>
 *   <li>A {@link SpringApplicationContextProvider} to allow the engine to access Spring beans.</li>
 *   <li>A default {@link ExtensionInvoker} to enable extension points.</li>
 *   <li>A {@link ProcessEngineShutdownHook} to ensure graceful shutdown of the engine's thread pools.</li>
 * </ul>
 *
 * @author yusu
 */
@AutoConfiguration
@ConditionalOnClass(ProcessEngine.class)
@ConditionalOnProperty(prefix = "compileflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(ProcessEngineProperties.class)
public class ProcessEngineAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessEngineAutoConfiguration.class);

    private static void putIfNotNull(Map<String, String> map, String key, Integer value) {
        if (value != null) {
            map.put(key, String.valueOf(value));
        }
    }

    private static void putIfNotNull(Map<String, String> map, String key, Boolean value) {
        if (value != null) {
            map.put(key, String.valueOf(value));
        }
    }

    /**
     * Creates the singleton {@link ProcessEngine} bean if one is not already defined in the
     * application context. The engine is configured using the properties from
     * {@link ProcessEngineProperties}.
     * <p>
     * <b>PERFORMANCE OPTIMIZATION:</b> This method creates a singleton ProcessEngine instance
     * that will be reused throughout the entire application lifecycle. This is the <b>optimal
     * approach</b> for production environments as it:
     * <ul>
     *   <li>Creates thread pools only once (16-64 threads total)</li>
     *   <li>Eliminates repeated initialization overhead</li>
     *   <li>Enables flow compilation cache reuse</li>
     *   <li>Provides predictable memory usage</li>
     * </ul>
     * <p>
     * <b>Why Singleton Pattern:</b> ProcessEngine creation is expensive (100-500ms) and should
     * be done once per application, not per request or operation.
     *
     * @param properties The configuration properties, automatically injected by Spring Boot.
     * @return A fully configured {@link ProcessEngine} singleton instance.
     */
    @Bean
    @Primary
    @DependsOn({"applicationContextProvider"})
    @ConditionalOnMissingBean
    public ProcessEngine processEngine(ProcessEngineProperties properties, Environment environment) {
        try {
            LOGGER.info("Auto-configuring CompileFlow ProcessEngine singleton...");

            // Build a resolver chain: in-memory (from Boot strong-typed blocks) -> Environment -> System -> Defaults
            Map<String, String> overrideProperties = buildOverrideProperties(properties);
            PropertyResolver compositeResolver = new CompositePropertyResolver(
                    new MapPropertyResolver(overrideProperties),
                    new SpringEnvironmentPropertyResolver(environment),
                    new SystemPropertyResolver()
            );
            ProcessPropertyProvider.install(compositeResolver);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Config resolver chain installed: {}", compositeResolver);
            }

            ProcessEngineConfig config = properties.toProcessEngineConfig();
            ProcessEngine engine = ProcessEngineFactory.create(config);

            if (LOGGER.isDebugEnabled()) {
                logEffectiveConfiguration(config);
            }

            LOGGER.info("CompileFlow ProcessEngine auto-configured successfully.");
            return engine;
        } catch (Exception e) {
            LOGGER.error("Failed to create ProcessEngine with properties: {}", properties, e);
            throw new IllegalStateException("ProcessEngine creation failed. Please check your configuration.", e);
        }
    }

    private Map<String, String> buildOverrideProperties(ProcessEngineProperties properties) {
        Map<String, String> overrides = new HashMap<>();

        ProcessEngineProperties.CacheProperties cache = properties.getCache();
        if (cache != null) {
            putIfNotNull(overrides, ProcessPropertyKeys.Cache.RUNTIME_MAX_SIZE, cache.getRuntimeMaxSize());
            putIfNotNull(overrides, ProcessPropertyKeys.Cache.COMPILE_FUTURES_MAX_SIZE, cache.getCompileFuturesMaxSize());
            putIfNotNull(overrides, ProcessPropertyKeys.Cache.DYNAMIC_CLASS_MAX_SIZE, cache.getDynamicClassMaxSize());
            putIfNotNull(overrides, ProcessPropertyKeys.Cache.DYNAMIC_CLASS_EXPIRE_MINUTES, cache.getDynamicClassExpireMinutes());
        }

        ProcessEngineProperties.CompilationProperties compilation = properties.getCompilation();
        if (compilation != null) {
            putIfNotNull(overrides, ProcessPropertyKeys.Compilation.TIMEOUT_SECONDS, compilation.getTimeoutSeconds());
            putIfNotNull(overrides, ProcessPropertyKeys.Compilation.MAX_ATTEMPTS, compilation.getMaxAttempts());
        }

        ProcessEngineProperties.ClassLoaderProperties classLoader = properties.getClassLoader();
        if (classLoader != null) {
            putIfNotNull(overrides, ProcessPropertyKeys.ClassLoader.MAINTENANCE_INTERVAL_MINUTES, classLoader.getMaintenanceIntervalMinutes());
        }

        ProcessEngineProperties.ObservabilityProperties observability = properties.getObservability();
        if (observability != null) {
            putIfNotNull(overrides, ProcessPropertyKeys.Observability.ENABLED, observability.isEnabled());
            putIfNotNull(overrides, ProcessPropertyKeys.Observability.METRICS_ENABLED, observability.isMetricsEnabled());
            putIfNotNull(overrides, ProcessPropertyKeys.Observability.EVENTS_ASYNC, observability.isEventsAsync());
        }

        return overrides;
    }

    private void logEffectiveConfiguration(ProcessEngineConfig config) {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("CompileFlow effective configuration -> modelType={}, parallelCompilation={}, executors={}",
                        config.getModelType(),
                        config.isParallelCompilationEnabled(),
                        config.getExecutorConfig());

                LOGGER.debug("Executor cfg: compilation[keepAliveSeconds={}, queueSize={}], execution[keepAliveSeconds={}, queueSize={}, maxThreadsMultiplier={}], event[keepAliveSeconds={}, queueSize={}, maxThreadsMultiplier={}, threads={}], schedule[threads={}]",
                        ProcessPropertyProvider.Executor.getCompilationKeepAliveSeconds(),
                        ProcessPropertyProvider.Executor.getCompilationQueueSize(),
                        ProcessPropertyProvider.Executor.getExecutionKeepAliveSeconds(),
                        ProcessPropertyProvider.Executor.getExecutionQueueSize(),
                        ProcessPropertyProvider.Executor.getExecutionMaxThreadsMultiplier(),
                        ProcessPropertyProvider.Executor.getEventKeepAliveSeconds(),
                        ProcessPropertyProvider.Executor.getEventQueueSize(),
                        ProcessPropertyProvider.Executor.getEventMaxThreadsMultiplier(),
                        ProcessPropertyProvider.Executor.getEventThreads(),
                        config.getExecutorConfig().getScheduleThreads());

                LOGGER.debug("Cache cfg: runtimeMaxSize={}, compileFuturesMaxSize={}, dynamicClass[maxSize={}, expireMinutes={}], dynamicFuturesMaxSize={}, dynamicDigest[maxWeight={}, expireMinutes={}, skipThreshold={}], datatypeMaxSize={}",
                        ProcessPropertyProvider.Cache.getRuntimeMaxSize(),
                        ProcessPropertyProvider.Cache.getCompileFuturesMaxSize(),
                        ProcessPropertyProvider.Cache.getDynamicClassMaxSize(),
                        ProcessPropertyProvider.Cache.getDynamicClassExpireMinutes(),
                        ProcessPropertyProvider.Cache.getDynamicFuturesMaxSize(),
                        ProcessPropertyProvider.Cache.getDynamicDigestMaxWeight(),
                        ProcessPropertyProvider.Cache.getDynamicDigestExpireMinutes(),
                        ProcessPropertyProvider.Cache.getDynamicDigestSkipThreshold(),
                        ProcessPropertyProvider.Cache.getDataTypeMaxSize());

                LOGGER.debug("Compile cfg: timeoutSeconds={}, maxAttempts={}, initialBackoffMs={}, maxBackoffMs={}",
                        ProcessPropertyProvider.Compilation.getTimeoutSeconds(),
                        ProcessPropertyProvider.Compilation.getMaxAttempts(),
                        ProcessPropertyProvider.Compilation.getInitialBackoffMs(),
                        ProcessPropertyProvider.Compilation.getMaxBackoffMs());

                LOGGER.debug("ClassLoader cfg: maintenanceIntervalMinutes={}",
                        ProcessPropertyProvider.ClassLoader.getMaintenanceIntervalMinutes());
            }
        } catch (Throwable t) {
            LOGGER.debug("Failed to log effective configuration", t);
        }
    }

    /**
     * Creates a {@link DisposableBean} that ensures the {@link ProcessEngine#close()}
     * method is called during application shutdown. This is crucial for gracefully
     * terminating the engine's internal thread pools.
     *
     * @param processEngine The auto-configured process engine instance.
     * @return A shutdown hook bean.
     */
    @Bean
    @ConditionalOnMissingBean(ProcessEngineShutdownHook.class)
    public ProcessEngineShutdownHook processEngineShutdownHook(ProcessEngine processEngine) {
        return new ProcessEngineShutdownHook(processEngine);
    }

    /**
     * Creates the {@link SpringApplicationContextProvider} bean, which holds a static
     * reference to the {@link org.springframework.context.ApplicationContext}. This allows
     * CompileFlow components that are not Spring-managed (like dynamically compiled classes)
     * to look up and use Spring beans.
     *
     * <p><b>IMPORTANT:</b> This bean is marked as @Lazy to prevent early initialization
     * and potential circular dependency issues. The provider will be initialized when
     * first accessed by CompileFlow components.</p>
     *
     * @return The application context provider.
     */
    @Bean
    @ConditionalOnMissingBean(SpringApplicationContextProvider.class)
    @Lazy
    public SpringApplicationContextProvider applicationContextProvider() {
        return new SpringApplicationContextProvider();
    }

    /**
     * Creates a default {@link ExtensionInvoker} bean if one is not already present.
     * The ExtensionInvoker is the central component for discovering and executing
     * framework extensions. This bean ensures that extension points work out of the box
     * in a Spring Boot environment.
     *
     * @return The default extension invoker.
     */
    @Bean
    @ConditionalOnMissingBean(ExtensionInvoker.class)
    public ExtensionInvoker compileflowExtensionInvoker() {
        return new ExtensionInvokerImpl();
    }

    /**
     * A {@link DisposableBean} that triggers the graceful shutdown of the ProcessEngine.
     * This inner class is registered as a bean and its {@code destroy} method is
     * automatically called by the Spring container upon application context closure.
     */
    public static class ProcessEngineShutdownHook implements DisposableBean {

        private final ProcessEngine processEngine;

        public ProcessEngineShutdownHook(ProcessEngine processEngine) {
            this.processEngine = processEngine;
        }

        @Override
        public void destroy() throws Exception {
            if (processEngine != null) {
                LOGGER.info("Application shutdown detected: shutting down CompileFlow ProcessEngine and its thread pools...");
                processEngine.close();
            }
        }
    }

}
