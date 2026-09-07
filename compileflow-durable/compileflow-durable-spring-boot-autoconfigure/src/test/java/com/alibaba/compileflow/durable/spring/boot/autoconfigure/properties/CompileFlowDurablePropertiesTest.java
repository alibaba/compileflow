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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.alibaba.compileflow.durable.runtime.DurableProcessEngineConfig;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CompileFlowDurablePropertiesTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void definitionSizeCannotExceedTheDurableStoreLimit() {
        runner
            .withPropertyValues("compileflow.durable.definition.max-size=" + DurableStore.MAX_DEFINITION_BYTES + "B")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context
                    .getBean(CompileFlowDurableProperties.class)
                    .getDefinition()
                    .toConfig()
                    .getMaxBytes())
                    .isEqualTo(DurableStore.MAX_DEFINITION_BYTES);
            });
        assertValidationFailure("compileflow.durable.definition.max-size",
                "compileflow.durable.definition.max-size=" + (DurableStore.MAX_DEFINITION_BYTES + 1) + "B");
    }

    @Test
    void maintenanceBatchSizeMatchesRuntimeDemandQueryBounds() {
        for (int batchSize : new int[] {1, 100, 1000}) {
            runner
                .withPropertyValues("compileflow.durable.maintenance.batch-size=" + batchSize)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(CompileFlowDurableProperties.class).getMaintenance();
                    var maintenance =
                            new DurableProcessEngineConfig.Maintenance(properties.getInterval(),
                                    properties.getBatchSize());
                    assertThat(maintenance.batchSize()).isEqualTo(batchSize);
                    assertThat(new DurableStore.ProcessRuntimeDemandQuery(null, maintenance.batchSize()).limit()).isEqualTo(
                            batchSize);
                });
        }
        for (int batchSize : new int[] {-1, 0, 1001, 10000, 10001, Integer.MAX_VALUE}) {
            assertValidationFailure("compileflow.durable.maintenance.batch-size must be between 1 and 1000",
                    "compileflow.durable.maintenance.batch-size=" + batchSize);
        }
    }

    @Test
    void bindsSafeOwnerOrientedDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            CompileFlowDurableProperties properties = context.getBean(CompileFlowDurableProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getRuntimeMode()).isEqualTo(ProcessRuntimeMode.COMPILED);
            assertThat(properties.getShutdown().getTimeout()).isEqualTo(Duration.ofSeconds(15));
            assertThat(properties.getDatabase().getProvider()).isNull();
            assertThat(properties.getWorker().isEnabled()).isTrue();
            assertThat(properties.getWorker().getIdlePollDelay()).isEqualTo(Duration.ofMillis(100));
            assertThat(properties.getWorker().getTurnMaxSteps()).isEqualTo(10_000);
            assertThat(properties.getWorker().getMaxActiveIterations()).isEqualTo(32);
            assertThat(properties.getWorker().getTurnConcurrency()).isEqualTo(2);
            assertThat(properties.getWorker().getEffectConcurrency()).isEqualTo(8);
            assertThat(properties.getOutbox().getConcurrency()).isEqualTo(2);
            assertThat(properties.getOutbox().getRetry().getInitialDelay()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.getOutbox().getRetry().getMaxDelay()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.getRetention().getTerminalRun()).isNull();
            assertThat(properties.getRetention().getUnusedProcess()).isNull();
            assertThat(properties.getRetention().getConsumedOccurrence()).isNull();
            assertThat(properties.getCache().getRuntimeMaxSize()).isEqualTo(256);
        });
    }

    @Test
    void bindsProviderAndRejectsUnknownProvider() {
        runner
            .withPropertyValues("compileflow.durable.database.provider=MYSQL")
            .run(context -> {
                assertThat(context).hasNotFailed();
                CompileFlowDurableProperties properties = context.getBean(CompileFlowDurableProperties.class);
                assertThat(properties.getDatabase().getProvider()).isEqualTo(
                        CompileFlowDurableProperties.Database.Provider.MYSQL);
            });
        runner
            .withPropertyValues("compileflow.durable.database.provider=ORACLE")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void keepsSpringAndRuntimeOwnedDefaultsInSemanticParity() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            CompileFlowDurableProperties properties = context.getBean(CompileFlowDurableProperties.class);
            DurableProcessEngineConfig defaults = DurableProcessEngineConfig.builder(mock(DurableStore.class)).build();

            assertThat(properties.getRuntimeMode()).isEqualTo(defaults.getRuntimeMode());
            assertThat(properties.getCall().getMaxDepth()).isEqualTo(defaults.getMaxCallDepth());
            assertThat(properties.getShutdown().getTimeout()).isEqualTo(defaults.getShutdownTimeout());
            assertThat(properties.getDefinition().toConfig()).usingRecursiveComparison().isEqualTo(defaults.getDefinitionConfig());
            assertThat(properties.getJavaDiagnostics().toConfig())
                .usingRecursiveComparison()
                .isEqualTo(defaults.getJavaDiagnostics());

            DurableProcessEngineConfig.Worker worker = defaults.getWorker();
            assertThat(properties.getWorker().isEnabled()).isEqualTo(worker.enabled());
            assertThat(properties.getWorker().getId()).isEqualTo(worker.id());
            assertThat(properties.getWorker().getLeaseDuration()).isEqualTo(worker.leaseDuration());
            assertThat(properties.getWorker().getIdlePollDelay()).isEqualTo(worker.idlePollDelay());
            assertThat(properties.getWorker().getTurnFaultBackoff()).isEqualTo(worker.turnFaultBackoff());
            assertThat(properties.getWorker().getTurnMaxSteps()).isEqualTo(worker.turnMaxSteps());
            assertThat(properties.getWorker().getMaxActiveIterations()).isEqualTo(worker.maxActiveIterations());
            assertThat(properties.getWorker().getTurnConcurrency()).isEqualTo(worker.turnConcurrency());
            assertThat(properties.getWorker().getEffectConcurrency()).isEqualTo(worker.effectConcurrency());

            DurableProcessEngineConfig.Outbox outbox = defaults.getOutbox();
            assertThat(properties.getOutbox().getConcurrency()).isEqualTo(outbox.concurrency());
            assertThat(properties.getOutbox().getRetry().getInitialDelay()).isEqualTo(outbox.initialDelay());
            assertThat(properties.getOutbox().getRetry().getMaxDelay()).isEqualTo(outbox.maxDelay());
            assertThat(properties.getOutbox().getRetry().getMaxAttempts()).isEqualTo(outbox.maxAttempts());

            DurableProcessEngineConfig.Maintenance maintenance = defaults.getMaintenance();
            assertThat(properties.getMaintenance().getInterval()).isEqualTo(maintenance.interval());
            assertThat(properties.getMaintenance().getBatchSize()).isEqualTo(maintenance.batchSize());

            DurableProcessEngineConfig.Retention retention = defaults.getRetention();
            assertThat(properties.getRetention().getTerminalRun()).isEqualTo(retention.terminalRun());
            assertThat(properties.getRetention().getUnusedProcess()).isEqualTo(retention.unusedProcess());
            assertThat(properties.getRetention().getConsumedOccurrence()).isEqualTo(retention.consumedOccurrence());
            assertThat(properties.getRetention().getInterval()).isEqualTo(retention.interval());
            assertThat(properties.getCache().getRuntimeMaxSize()).isEqualTo(defaults.getCacheMaxSize());
        });
    }

    @Test
    void bindsGroupedOperationalIntent() {
        runner
            .withPropertyValues("compileflow.durable.enabled=true", "compileflow.durable.runtime-mode=interpreted",
                    "compileflow.durable.call.max-depth=17", "compileflow.durable.shutdown.timeout=9s",
                    "compileflow.durable.worker.id=runtime-a", "compileflow.durable.worker.turn-concurrency=7",
                    "compileflow.durable.worker.turn-max-steps=1234",
                    "compileflow.durable.worker.max-active-iterations=12",
                    "compileflow.durable.worker.idle-poll-delay=250ms",
                    "compileflow.durable.outbox.retry.initial-delay=2s",
                    "compileflow.durable.outbox.retry.max-delay=30s", "compileflow.durable.maintenance.batch-size=42",
                    "compileflow.durable.retention.terminal-run=30d", "compileflow.durable.retention.unused-process=90d",
                    "compileflow.durable.retention.consumed-occurrence=7d")
            .run(context -> {
                assertThat(context).hasNotFailed();
                CompileFlowDurableProperties properties = context.getBean(CompileFlowDurableProperties.class);
                assertThat(properties.isEnabled()).isTrue();
                assertThat(properties.getRuntimeMode()).isEqualTo(ProcessRuntimeMode.INTERPRETED);
                assertThat(properties.getCall().getMaxDepth()).isEqualTo(17);
                assertThat(properties.getShutdown().getTimeout()).isEqualTo(Duration.ofSeconds(9));
                assertThat(properties.getWorker().getId()).isEqualTo("runtime-a");
                assertThat(properties.getWorker().getTurnConcurrency()).isEqualTo(7);
                assertThat(properties.getWorker().getTurnMaxSteps()).isEqualTo(1234);
                assertThat(properties.getWorker().getMaxActiveIterations()).isEqualTo(12);
                assertThat(properties.getWorker().getIdlePollDelay()).isEqualTo(Duration.ofMillis(250));
                assertThat(properties.getOutbox().getRetry().getMaxDelay()).isEqualTo(Duration.ofSeconds(30));
                assertThat(properties.getMaintenance().getBatchSize()).isEqualTo(42);
                assertThat(properties.getRetention().getTerminalRun()).isEqualTo(Duration.ofDays(30));
                assertThat(properties.getRetention().getUnusedProcess()).isEqualTo(Duration.ofDays(90));
                assertThat(properties.getRetention().getConsumedOccurrence()).isEqualTo(Duration.ofDays(7));
            });
    }

    @Test
    void rejectsUnknownRetiredConfiguration() {
        runner
            .withPropertyValues("compileflow.durable.worker-enabled=false")
            .run(context -> assertThat(context).hasFailed());
        runner
            .withPropertyValues("compileflow.durable.capability-backoff=1s")
            .run(context -> assertThat(context).hasFailed());
        runner
            .withPropertyValues("compileflow.durable.max-call-depth=17")
            .run(context -> assertThat(context).hasFailed());
        runner
            .withPropertyValues("compileflow.durable.shutdown-timeout=9s")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsEveryInvalidNestedGroupThroughBeanValidation() {
        assertValidationFailure("compileflow.durable.call.max-depth", "compileflow.durable.call.max-depth=0");
        assertValidationFailure("compileflow.durable.worker.turn-max-steps",
                "compileflow.durable.worker.turn-max-steps=0");
        assertValidationFailure("compileflow.durable.worker.max-active-iterations",
                "compileflow.durable.worker.max-active-iterations=65");
        assertValidationFailure("compileflow.durable.worker.lease-duration",
                "compileflow.durable.worker.lease-duration=0ms");
        assertValidationFailure("compileflow.durable.worker.lease-duration",
                "compileflow.durable.worker.lease-duration=1ms");
        assertValidationFailure("compileflow.durable.worker.id", "compileflow.durable.worker.id=" + "x".repeat(97));
        assertValidationFailure("compileflow.durable.outbox.concurrency", "compileflow.durable.outbox.concurrency=0");
        assertValidationFailure("compileflow.durable.outbox.retry.max-delay",
                "compileflow.durable.outbox.retry.initial-delay=10s", "compileflow.durable.outbox.retry.max-delay=1s");
        assertValidationFailure("compileflow.durable.maintenance.batch-size",
                "compileflow.durable.maintenance.batch-size=0");
        assertValidationFailure("compileflow.durable.retention.terminal-run",
                "compileflow.durable.retention.terminal-run=0ms");
        assertValidationFailure("compileflow.durable.cache.runtime-max-size",
                "compileflow.durable.cache.runtime-max-size=0");
    }

    private void assertValidationFailure(String expectedMessage, String... propertyValues) {
        runner
            .withPropertyValues(propertyValues)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(BindValidationException.class)
                    .hasStackTraceContaining(expectedMessage);
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CompileFlowDurableProperties.class)
    static class PropertiesConfiguration {
    }
}
