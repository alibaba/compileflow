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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CompileFlowWorkbenchServerEnvironmentPostProcessorTest {
    private static final String VALID_API_KEY = "0123456789abcdef0123456789abcdef";
    private static final String CONCURRENCY_PROPERTY = "compileflow.workbench.server.async-invocation.concurrency";
    private static final String CONFIGURATION_METADATA = "META-INF/spring-configuration-metadata.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldBeRegisteredAsAnEnvironmentPostProcessor() throws IOException {
        Set<String> registeredProcessors = new HashSet<>();
        Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/spring.factories");
        while (resources.hasMoreElements()) {
            Properties factories = PropertiesLoaderUtils.loadProperties(new UrlResource(resources.nextElement()));
            String names = factories.getProperty(EnvironmentPostProcessor.class.getName());
            if (names != null) {
                Arrays
                    .stream(names.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .forEach(registeredProcessors::add);
            }
        }

        assertThat(registeredProcessors)
            .contains(CompileFlowWorkbenchServerEnvironmentPostProcessor.class.getName(),
                    DevelopmentDataSourceEnvironmentPostProcessor.class.getName());
    }

    @Test
    void shouldTranslateOwnedVariablesWithoutInjectingDefaults() {
        Map<String, Object> translated = CompileFlowWorkbenchServerEnvironmentPostProcessor.translate(Map.of("COMPILE"
                + "FLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE", "API_KEY",
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY", "server-secret",
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL", "gateway-service",
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_MIGRATE", "false",
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_PREVIEW_EXECUTION_ENABLED", "true",
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_MAX_QUERY_ROWS", "25000",
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_PURGE_BATCH_SIZE", "750",
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_CONCURRENCY", "7", "UNRELATED_VARIABLE", "ignored"));

        assertThat(translated)
            .containsEntry("compileflow.workbench.server.authentication.mode", "API_KEY")
            .containsEntry("compileflow.workbench.server.authentication.api-key", "server-secret")
            .containsEntry("compileflow.workbench.server.authentication.service-principal", "gateway-service")
            .containsEntry("compileflow.workbench.server.database.migrate", "false")
            .containsEntry("compileflow.workbench.server.preview-execution.enabled", "true")
            .containsEntry("compileflow.workbench.server.execution-log.max-query-rows", "25000")
            .containsEntry("compileflow.workbench.server.execution-log.purge-batch-size", "750")
            .containsEntry(CONCURRENCY_PROPERTY, "7")
            .doesNotContainKey("UNRELATED_VARIABLE");
        assertThat(CompileFlowWorkbenchServerEnvironmentPostProcessor.translate(Map.of())).isEmpty();
    }

    @Test
    void shouldMapEveryPublishedServerPropertyToItsCanonicalEnvironmentVariable() throws IOException {
        Set<String> publishedProperties = new HashSet<>();
        Enumeration<URL> resources = getClass().getClassLoader().getResources(CONFIGURATION_METADATA);
        while (resources.hasMoreElements()) {
            try (var input = resources.nextElement().openStream()) {
                JsonNode metadata = OBJECT_MAPPER.readTree(input);
                for (JsonNode property : metadata.path("properties")) {
                    String name = property.path("name").stringValue();
                    if (name.startsWith("compileflow.workbench.server.")) {
                        publishedProperties.add(name);
                    }
                }
            }
        }

        Map<String, String> mappings = CompileFlowWorkbenchServerEnvironmentPostProcessor.environmentVariableMappings();
        assertThat(mappings.values()).containsExactlyInAnyOrderElementsOf(publishedProperties);
        mappings.forEach((variable, property) -> assertThat(variable)
            .isEqualTo(
                    "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_"
                    + property
                                .substring("compileflow.workbench.server.".length())
                                .toUpperCase()
                                .replace('.', '_')
                                .replace('-', '_')));
    }

    @Test
    void shouldRejectUnknownVariablesInTheOwnedNamespace() {
        assertThatThrownBy(() -> CompileFlowWorkbenchServerEnvironmentPostProcessor.translate(Map.of("COMPILEFLOW_"
                + "WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_WORKER_THEADS", "7")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_WORKER_THEADS");
    }

    @Test
    void shouldIgnoreKubernetesServiceLinkVariablesOutsideTheOwnedNamespace() {
        assertThat(CompileFlowWorkbenchServerEnvironmentPostProcessor.translate(Map.of("COMPILEFLOW_WORKBENCH_SERVER_"
                + "SERVICE_HOST", "10.0.0.10", "COMPILEFLOW_WORKBENCH_SERVER_SERVICE_PORT", "8080",
                "COMPILEFLOW_WORKBENCH_SERVER_PORT", "tcp://10.0.0.10:8080")))
            .isEmpty();
    }

    @Test
    void shouldBindOwnedAliasesAlongsideKubernetesServiceLinks() {
        Map<String, String> variables = Map.of("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE", "API_KEY",
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY", VALID_API_KEY,
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL", "gateway-service",
                "COMPILEFLOW_WORKBENCH_SERVER_SERVICE_HOST", "10.0.0.10", "COMPILEFLOW_WORKBENCH_SERVER_SERVICE_PORT",
                "8080");

        new ApplicationContextRunner()
            .withInitializer(context -> {
                var environment = context.getEnvironment();
                environment
                    .getPropertySources()
                    .replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                    new HashMap<>(variables)));
                new CompileFlowWorkbenchServerEnvironmentPostProcessor(() -> variables).postProcessEnvironment(environment,
                        null);
            })
            .withUserConfiguration(BindingConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                CompileFlowWorkbenchServerProperties properties =
                        context.getBean(CompileFlowWorkbenchServerProperties.class);
                assertThat(properties.getAuthentication().getApiKey()).isEqualTo(VALID_API_KEY);
                assertThat(properties.getAuthentication().getServicePrincipal()).isEqualTo("gateway-service");
            });
    }

    @Test
    void shouldApplyEnvironmentPrecedenceWithoutOverridingHigherPrioritySources() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(
                new MapPropertySource("applicationConfig", Map.of(CONCURRENCY_PROPERTY, 4)));

        new CompileFlowWorkbenchServerEnvironmentPostProcessor(() -> Map.of("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_"
                + "ASYNC_INVOCATION_CONCURRENCY", "7"))
            .postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(CONCURRENCY_PROPERTY)).isEqualTo("7");

        environment.getPropertySources().addFirst(
                new MapPropertySource("commandLineArgs", Map.of(CONCURRENCY_PROPERTY, 9)));
        assertThat(environment.getProperty(CONCURRENCY_PROPERTY)).isEqualTo("9");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CompileFlowWorkbenchServerProperties.class)
    static class BindingConfiguration {
    }
}
