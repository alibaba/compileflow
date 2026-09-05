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
package com.alibaba.compileflow.workbench.server.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * Translates the server's stable environment-variable API into canonical Spring properties.
 *
 * <p>The adapter intentionally owns no defaults. Missing values remain absent so constructor-bound
 * configuration is the single source of defaults and validation rules.
 *
 * @author yusu
 */
public final class CompileFlowWorkbenchServerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String PROPERTY_SOURCE_NAME = "compileflowServerEnvironment";
    private static final String ENVIRONMENT_PREFIX = "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_";
    private static final Map<String, String> PROPERTY_BY_ENVIRONMENT_VARIABLE = Map.ofEntries(Map.entry("COMPILEFLOW_"
                    + "WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE", "compileflow.workbench.server.authentication.mode"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY",
                    "compileflow.workbench.server.authentication.api-key"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL",
                    "compileflow.workbench.server.authentication.service-principal"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_HTTP_MAX_REQUEST_SIZE",
                    "compileflow.workbench.server.http.max-request-size"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_MIGRATE",
                    "compileflow.workbench.server.database.migrate"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_PREVIEW_EXECUTION_ENABLED",
                    "compileflow.workbench.server.preview-execution.enabled"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_MAX_QUERY_ROWS",
                    "compileflow.workbench.server.execution-log.max-query-rows"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_PURGE_BATCH_SIZE",
                    "compileflow.workbench.server.execution-log.purge-batch-size"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_CONCURRENCY",
                    "compileflow.workbench.server.async-invocation.concurrency"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_QUEUE_CAPACITY",
                    "compileflow.workbench.server.async-invocation.queue-capacity"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_DISPATCH_BATCH_SIZE",
                    "compileflow.workbench.server.async-invocation.dispatch-batch-size"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_DISPATCH_INTERVAL",
                    "compileflow.workbench.server.async-invocation.dispatch-interval"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_LEASE_DURATION",
                    "compileflow.workbench.server.async-invocation.lease-duration"),
            Map.entry("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_LEASE_RECOVERY_INTERVAL",
                    "compileflow.workbench.server.async-invocation.lease-recovery-interval"));
    private final Supplier<? extends Map<String, String>> environmentSupplier;

    /**
     * Creates a processor that reads the operating-system environment once during startup.
     */
    public CompileFlowWorkbenchServerEnvironmentPostProcessor() {
        this(System::getenv);
    }

    CompileFlowWorkbenchServerEnvironmentPostProcessor(Supplier<? extends Map<String, String>> environmentSupplier) {
        this.environmentSupplier = Objects.requireNonNull(environmentSupplier, "environmentSupplier");
    }

    static Map<String, Object> translate(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        List<String> unknown = new ArrayList<>();
        Map<String, Object> translated = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String variable = entry.getKey();
            if (!variable.startsWith(ENVIRONMENT_PREFIX)) {
                continue;
            }
            String property = PROPERTY_BY_ENVIRONMENT_VARIABLE.get(variable);
            if (property == null) {
                unknown.add(variable);
            } else if (entry.getValue() != null) {
                translated.put(property, entry.getValue());
            }
        }
        if (!unknown.isEmpty()) {
            Collections.sort(unknown);
            throw new IllegalStateException(
                    "Unknown CompileFlow server environment variable(s): " + String.join(", ", unknown));
        }
        return Collections.unmodifiableMap(translated);
    }

    static Map<String, String> environmentVariableMappings() {
        return PROPERTY_BY_ENVIRONMENT_VARIABLE;
    }

    /**
     * Runs immediately after Spring Boot has loaded config data.
     *
     * @return the post-processor ordering value
     */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    /**
     * Adds validated aliases at operating-system environment precedence.
     *
     * @param environment application environment being prepared
     * @param application application being started
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> translated = translate(environmentSupplier.get());
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.remove(PROPERTY_SOURCE_NAME);
        if (translated.isEmpty()) {
            return;
        }

        MapPropertySource aliases = new MapPropertySource(PROPERTY_SOURCE_NAME, translated);
        if (propertySources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            propertySources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, aliases);
        } else {
            propertySources.addFirst(aliases);
        }
    }
}
