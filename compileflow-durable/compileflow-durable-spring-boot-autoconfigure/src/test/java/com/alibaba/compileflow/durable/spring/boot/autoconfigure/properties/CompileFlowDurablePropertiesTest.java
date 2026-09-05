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
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisherOptions;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerOptions;
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
    void bindsSafeOwnerOrientedDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            CompileFlowDurableProperties properties = context.getBean(CompileFlowDurableProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getRuntimeMode()).isEqualTo(ProcessRuntimeMode.COMPILED);
            assertThat(context.getBean(CompileFlowDurablePostgresProperties.class).isMigrate()).isFalse();
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
    void keepsSpringAndRuntimeOwnedDefaultsInSemanticParity() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            CompileFlowDurableProperties properties = context.getBean(CompileFlowDurableProperties.class);

            DurableTurnWorkerOptions turn = DurableTurnWorkerOptions.defaults("turn-worker");
            assertThat(properties.getWorker().getLeaseDuration()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getWorker().getTurnFaultBackoff()).isEqualTo(turn.turnFaultBackoff());
            assertThat(properties.getWorker().getTurnMaxSteps()).isEqualTo(turn.turnMaxSteps());
            assertThat(properties.getWorker().getMaxActiveIterations()).isEqualTo(turn.maxActiveIterations());

            DurableOutboxPublisherOptions outbox = DurableOutboxPublisherOptions.defaults("outbox-worker");
            assertThat(properties.getOutbox().getRetry().getInitialDelay()).isEqualTo(outbox.initialRetryDelay());
            assertThat(properties.getOutbox().getRetry().getMaxDelay()).isEqualTo(outbox.maxRetryDelay());
            assertThat(properties.getOutbox().getRetry().getMaxAttempts()).isEqualTo(outbox.maxAttempts());
        });
    }

    @Test
    void bindsGroupedOperationalIntent() {
        runner
            .withPropertyValues("compileflow.durable.enabled=true", "compileflow.durable.runtime-mode=interpreted",
                    "compileflow.durable-postgres.migrate=true", "compileflow.durable.worker.id=runtime-a",
                    "compileflow.durable.worker.turn-concurrency=7", "compileflow.durable.worker.turn-max-steps=1234",
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
                assertThat(context.getBean(CompileFlowDurablePostgresProperties.class).isMigrate()).isTrue();
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
    }

    @Test
    void rejectsEveryInvalidNestedGroupThroughBeanValidation() {
        assertValidationFailure("compileflow.durable.worker.turn-max-steps",
                "compileflow.durable.worker.turn-max-steps=0");
        assertValidationFailure("compileflow.durable.worker.max-active-iterations",
                "compileflow.durable.worker.max-active-iterations=65");
        assertValidationFailure("compileflow.durable.worker.lease-duration",
                "compileflow.durable.worker.lease-duration=0ms");
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
    @EnableConfigurationProperties({CompileFlowDurableProperties.class, CompileFlowDurablePostgresProperties.class})
    static class PropertiesConfiguration {
    }
}
