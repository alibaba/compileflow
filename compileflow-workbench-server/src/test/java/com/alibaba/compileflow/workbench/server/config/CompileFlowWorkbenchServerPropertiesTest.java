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
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class CompileFlowWorkbenchServerPropertiesTest {
    private static final String VALID_API_KEY = "0123456789abcdef0123456789abcdef";
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues("compileflow.workbench.server.authentication.mode=DISABLED",
                "compileflow.workbench.server.authentication.service-principal=local-test");

    @Test
    void shouldBindImmutableDefaultsAndPreserveSecrets() {
        contextRunner
            .withPropertyValues("compileflow.workbench.server.authentication.mode=API_KEY",
                    "compileflow.workbench.server.authentication.api-key=" + VALID_API_KEY)
            .run(context -> {
                assertThat(context).hasNotFailed();
                CompileFlowWorkbenchServerProperties properties =
                        context.getBean(CompileFlowWorkbenchServerProperties.class);
                assertThat(properties.getAuthentication().getApiKey()).isEqualTo(VALID_API_KEY);
                assertThat(properties.getAuthentication().getServicePrincipal()).isEqualTo("local-test");
                assertThat(properties.getHttp().getMaxRequestSize().toMegabytes()).isEqualTo(10);
                assertThat(properties.getDatabase().isMigrate()).isFalse();
                assertThat(properties.getPreviewExecution().isEnabled()).isFalse();
                assertThat(properties.getExecutionLog().getMaxQueryRows()).isEqualTo(10_000);
                assertThat(properties.getAsyncInvocation().getLeaseDuration()).isEqualTo(Duration.ofSeconds(30));
            });
    }

    @Test
    void shouldAllowAnApplicationMigrationIdentityWithoutDisablingSchemaAdmission() {
        contextRunner
            .withPropertyValues("compileflow.workbench.server.database.migrate=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CompileFlowWorkbenchServerProperties.class).getDatabase().isMigrate()).isTrue();
            });
    }

    @Test
    void shouldEnableTrustedDraftExecutionOnlyWhenExplicitlyConfigured() {
        contextRunner
            .withPropertyValues("compileflow.workbench.server.preview-execution.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                CompileFlowWorkbenchServerProperties properties =
                        context.getBean(CompileFlowWorkbenchServerProperties.class);
                assertThat(properties.getPreviewExecution().isEnabled()).isTrue();
            });
    }

    @Test
    void shouldRejectWhitespaceAroundApiKeysInsteadOfRewritingSecrets() {
        contextRunner
            .withInitializer(context -> context
                .getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource("exactSecret",
                                Map.of("compileflow.workbench.server.authentication.mode", "API_KEY",
                                        "compileflow.workbench.server.authentication.api-key", VALID_API_KEY + " "))))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(
                            "authentication.api-key must contain 32..256 URL-safe" + " ASCII characters");
            });
    }

    @Test
    void shouldRejectWeakApiKeysWhenAuthenticationIsEnabled() {
        contextRunner
            .withPropertyValues("compileflow.workbench.server.authentication.mode=API_KEY",
                    "compileflow.workbench.server.authentication.api-key=short-key")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(
                            "authentication.api-key must contain 32..256 URL-safe" + " ASCII characters");
            });
    }

    @Test
    void shouldRejectApiKeyCharactersThatAreUnsafeAcrossProxyConfigurationBoundaries() {
        String prefix = VALID_API_KEY.substring(0, VALID_API_KEY.length() - 1);
        assertFailure("compileflow.workbench.server.authentication.mode=API_KEY",
                "compileflow.workbench.server.authentication.api-key=" + prefix + "\"",
                "authentication.api-key must contain 32..256 URL-safe ASCII characters");
        assertFailure("compileflow.workbench.server.authentication.mode=API_KEY",
                "compileflow.workbench.server.authentication.api-key=" + prefix + "$",
                "authentication.api-key must contain 32..256 URL-safe ASCII characters");
    }

    @Test
    void shouldFailClosedWhenAuthenticationConfigurationIsAbsent() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(
                        "authentication.api-key is required in API_KEY mode");
            });
    }

    @Test
    void shouldRejectAnApiKeyWhenAuthenticationIsDisabled() {
        assertFailure("compileflow.workbench.server.authentication.api-key=" + VALID_API_KEY,
                "authentication.api-key is required in API_KEY mode and must be absent in" + " DISABLED mode");
    }

    @Test
    void shouldRequireAnExplicitAuthenticationServicePrincipal() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("compileflow.workbench.server.authentication.mode=DISABLED")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(
                            "authentication.service-principal must contain" + " 1..128 visible" + " ASCII characters");
            });
    }

    @Test
    void shouldRejectSubMillisecondAsyncIntervals() {
        assertFailure("compileflow.workbench.server.async-invocation.dispatch-interval=1000001ns",
                "async-invocation intervals must be positive whole-millisecond durations");
    }

    @Test
    void shouldRequireLeaseDurationLongEnoughForDerivedRenewal() {
        assertFailure("compileflow.workbench.server.async-invocation.lease-duration=1ms",
                "lease-duration must be at least 2ms");
    }

    @Test
    void shouldRejectAsyncIntervalsThatOverflowMillisecondConsumers() {
        assertFailure("compileflow.workbench.server.async-invocation.lease-duration=P106751991168D",
                "async-invocation intervals must be positive whole-millisecond durations");
    }

    @Test
    void shouldRejectUnsafeHttpRequestBodyLimits() {
        assertFailure("compileflow.workbench.server.http.max-request-size=0B",
                "compileflow.workbench.server.http.max-request-size must be between 1 byte and 100MB");
        assertFailure("compileflow.workbench.server.http.max-request-size=101MB",
                "compileflow.workbench.server.http.max-request-size must be between 1 byte and 100MB");
    }

    @Test
    void shouldRejectUnsafeAsyncWorkerCapacity() {
        assertFailure("compileflow.workbench.server.async-invocation.concurrency=257",
                "concurrency must be between 1 and 256");
        assertFailure("compileflow.workbench.server.async-invocation.queue-capacity=10001",
                "queue-capacity must be between 1 and 10000");
        assertFailure("compileflow.workbench.server.async-invocation.dispatch-batch-size=1001",
                "dispatch-batch-size must be between 1 and 1000");
        contextRunner
            .withPropertyValues("compileflow.workbench.server.async-invocation.concurrency=2",
                    "compileflow.workbench.server.async-invocation.queue-capacity=3",
                    "compileflow.workbench.server.async-invocation.dispatch-batch-size=6")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("dispatch-batch-size must not exceed" + " concurrency plus queue-capacity");
            });
    }

    @Test
    void shouldBindAndValidateExecutionLogQueryLimit() {
        contextRunner
            .withPropertyValues("compileflow.workbench.server.execution-log.max-query-rows=25000")
            .run(context -> assertThat(context
                .getBean(CompileFlowWorkbenchServerProperties.class)
                .getExecutionLog()
                .getMaxQueryRows())
                .isEqualTo(25_000));
        assertFailure("compileflow.workbench.server.execution-log.max-query-rows=0",
                "execution-log.max-query-rows must be between 1 and 100000");
        assertFailure("compileflow.workbench.server.execution-log.max-query-rows=100001",
                "execution-log.max-query-rows must be between 1 and 100000");

        contextRunner
            .withPropertyValues("compileflow.workbench.server.execution-log.purge-batch-size=2500")
            .run(context -> assertThat(context
                .getBean(CompileFlowWorkbenchServerProperties.class)
                .getExecutionLog()
                .getPurgeBatchSize())
                .isEqualTo(2_500));
        assertFailure("compileflow.workbench.server.execution-log.purge-batch-size=0",
                "execution-log.purge-batch-size must be between 1 and 10000");
        assertFailure("compileflow.workbench.server.execution-log.purge-batch-size=10001",
                "execution-log.purge-batch-size must be between 1 and 10000");
    }

    @Test
    void shouldRejectUnknownServerProperty() {
        assertFailure("compileflow.workbench.server.async-invocation.lease-timeout=30s", "lease-timeout");
    }

    private void assertFailure(String property, String expectedMessage) {
        assertFailure(new String[] {property}, expectedMessage);
    }

    private void assertFailure(String firstProperty, String secondProperty, String expectedMessage) {
        assertFailure(new String[] {firstProperty, secondProperty}, expectedMessage);
    }

    private void assertFailure(String[] properties, String expectedMessage) {
        contextRunner
            .withPropertyValues(properties)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(expectedMessage);
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CompileFlowWorkbenchServerProperties.class)
    static class TestConfiguration {
    }
}
