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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties.ProcessEngineProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProcessEngineConfigurationPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class));

    @Test
    void defaultsRejectQueuedTimedActionsWhileRetainingColdLoadBuffer() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ProcessEngineConfig config =
                    context.getBean(ProcessEngineProperties.class).toProcessEngineConfigBuilder().build();
            assertThat(config.getExecutorConfig().getActionTimeoutMaxPending()).isZero();
            assertThat(config.getExecutorConfig().getRuntimeLoadMaxPending()).isEqualTo(4);
            assertThat(config.getRuntimeMode()).isEqualTo(ProcessRuntimeMode.COMPILED);
        });
    }

    @Test
    void shouldRejectInvalidEngineEnableValueEvenWhenCoreIsDisabled() {
        contextRunner
            .withPropertyValues("compileflow.engine.enabled=maybe")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectNonPositiveExecutorShutdownBudgets() {
        contextRunner
            .withPropertyValues("compileflow.engine.shutdown.timeout=0ms")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldBindImmutableEngineTreeAndConcurrencyBudgets() {
        contextRunner
            .withPropertyValues("compileflow.engine.executor.action-timeout.max-concurrency=17",
                    "compileflow.engine.executor.action-timeout.max-pending=23", "compileflow.engine.call.max-depth=24",
                    "compileflow.engine.executor.action-timeout.cancellation-grace-period=600ms",
                    "compileflow.engine.executor.parallel.cancellation-grace-period=750ms",
                    "compileflow.engine.observability.events.max-concurrency=3",
                    "compileflow.engine.observability.events.max-pending=5", "compileflow.engine.shutdown.timeout=9s",
                    "compileflow.engine.max-resident-runtimes=73", "compileflow.engine.runtime-mode=INTERPRETED",
                    "compileflow.engine.runtime-load-timeout=12s", "compileflow.engine.definition.max-size=2MB",
                    "compileflow.engine.components.allowed-beans[0]=orderService")
            .run(context -> {
                assertThat(context).hasNotFailed();
                ProcessEngineProperties engine = context.getBean(ProcessEngineProperties.class);
                assertThat(engine.getExecutor().getActionTimeout().getMaxConcurrency()).isEqualTo(17);
                assertThat(engine.getExecutor().getActionTimeout().getMaxPending()).isEqualTo(23);
                assertThat(engine.getCall().getMaxDepth()).isEqualTo(24);
                assertThat(engine.getRuntimeMode()).isEqualTo(ProcessRuntimeMode.INTERPRETED);
                assertThat(engine.getExecutor().getActionTimeout().getCancellationGracePeriod()).isEqualTo(Duration.ofMillis(
                        600));
                assertThat(engine.getExecutor().getParallel().getCancellationGracePeriod()).isEqualTo(Duration.ofMillis(
                        750));
                assertThat(engine.getObservability().getEvents().getMaxConcurrency()).isEqualTo(3);
                assertThat(engine.getObservability().getEvents().getMaxPending()).isEqualTo(5);
                assertThat(engine.getShutdown().getTimeout()).isEqualTo(Duration.ofSeconds(9));
                ProcessEngineConfig engineConfig = engine.toProcessEngineConfigBuilder().build();
                assertThat(engineConfig.getMaxCallDepth()).isEqualTo(24);
                assertThat(engineConfig.getExecutorConfig().getActionTimeoutMaxConcurrency()).isEqualTo(17);
                assertThat(engineConfig.getExecutorConfig().getActionTimeoutMaxPending()).isEqualTo(23);
                assertThat(engineConfig.getRuntimeMode()).isEqualTo(ProcessRuntimeMode.INTERPRETED);
                assertThat(engineConfig.getExecutorConfig().getActionTimeoutCancellationGracePeriod())
                    .isEqualTo(Duration.ofMillis(600));
                assertThat(engineConfig.getExecutorConfig().getParallelCancellationGracePeriod()).isEqualTo(Duration.ofMillis(
                        750));
                assertThat(engineConfig.getShutdownTimeout()).isEqualTo(Duration.ofSeconds(9));
                assertThat(engineConfig.getMaxResidentRuntimes()).isEqualTo(73);
                assertThat(engineConfig.getRuntimeLoadTimeout()).isEqualTo(Duration.ofSeconds(12));
                assertThat(engineConfig.getDefinitionConfig().getMaxBytes()).isEqualTo(2 * 1024 * 1024);
                assertThat(engine.getComponents().getAllowedBeans()).containsExactly("orderService");
            });
    }

    @Test
    void shouldKeepSpringEngineDefaultsAlignedWithTheCoreConfiguration() {
        contextRunner.run(context -> {
            ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);
            ProcessEngineConfig springConfig = properties.toProcessEngineConfigBuilder().build();

            assertThat(springConfig).usingRecursiveComparison().isEqualTo(ProcessEngineConfig.defaults());
        });
    }

    @Test
    void shouldRejectUnknownEngineProperty() {
        contextRunner
            .withPropertyValues("compileflow.engine.java-diagnostics.debug-symbols=FULL")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldReportCanonicalJavaDiagnosticsPropertyNames() {
        contextRunner
            .withPropertyValues("compileflow.engine.java-diagnostics.debug.bytecode-enabled=true")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(
                            "compileflow.engine.java-diagnostics.debug.output-directory is required when"
                            + " compileflow.engine.java-diagnostics.debug.bytecode-enabled is true");
            });
    }

    @Test
    void shouldRejectInvalidDefinitionSize() {
        contextRunner
            .withPropertyValues("compileflow.engine.definition.max-size=0B")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.engine.definition.max-size=100MB")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(ProcessEngineProperties.class).getDefinition().toConfig().getMaxBytes())
                    .isEqualTo(ProcessDefinitionConfig.MAX_BYTES);
            });
        contextRunner
            .withPropertyValues("compileflow.engine.definition.max-size=104857601B")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectInvalidComponentAllowlist() {
        contextRunner
            .withPropertyValues("compileflow.engine.components.allowed-beans[0]=orderService",
                    "compileflow.engine.components.allowed-beans[1]=orderService")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.engine.components.allowed-beans[0]=")
            .run(context -> assertThat(context).hasFailed());
        contextRunner
            .withPropertyValues("compileflow.engine.components.allowed-beans[0]=&factoryBean")
            .run(context -> assertThat(context).hasFailed());
    }
}
