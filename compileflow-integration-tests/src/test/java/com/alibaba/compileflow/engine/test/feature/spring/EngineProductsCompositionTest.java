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
package com.alibaba.compileflow.engine.test.feature.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.alibaba.compileflow.durable.api.DurableProcessEngine;
import com.alibaba.compileflow.durable.runtime.DurableProcessEngineConfig;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.CompileFlowDurableAutoConfiguration;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.CompileFlowDurablePropertiesAutoConfiguration;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEngineAutoConfiguration;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEnginePropertiesAutoConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EngineProductsCompositionTest {
    @Test
    void processEngineCanBeConfiguredWithoutDurableEngine() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowEngineAutoConfiguration.class))
            .run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(ProcessEngine.class)
                .doesNotHaveBean(DurableProcessEngine.class));
    }

    @Test
    void durableEngineCanBeConfiguredWithoutProcessEngine() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowDurablePropertiesAutoConfiguration.class,
                    CompileFlowDurableAutoConfiguration.class))
            .withPropertyValues("compileflow.durable.enabled=true", "compileflow.durable.worker.enabled=false")
            .withBean(DurableStore.class, () -> mock(DurableStore.class))
            .run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(DurableProcessEngine.class)
                .doesNotHaveBean(ProcessEngine.class));
    }

    @Test
    void bothProductsRetainIndependentConfigurationAndLifecycle() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowEngineAutoConfiguration.class, CompileFlowDurablePropertiesAutoConfiguration.class,
                    CompileFlowDurableAutoConfiguration.class))
            .withPropertyValues("compileflow.engine.runtime-mode=COMPILED", "compileflow.durable.enabled=true",
                    "compileflow.durable.runtime-mode=INTERPRETED", "compileflow.durable.worker.enabled=false")
            .withBean(DurableStore.class, () -> mock(DurableStore.class))
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ProcessEngine.class).hasSingleBean(
                        DurableProcessEngine.class);
                assertThat(context.getBean(ProcessEngineConfig.class).getRuntimeMode()).isEqualTo(
                        ProcessRuntimeMode.COMPILED);
                assertThat(context.getBean(DurableProcessEngineConfig.class).getRuntimeMode())
                    .isEqualTo(ProcessRuntimeMode.INTERPRETED);
                context.getBean(DurableProcessEngine.class).close();
                var definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "independent",
                        """
                    <bpm code="independent" name="Independent">
                        <start id="start" name="Start" g="0,0,32,32"><transition to="end"/></start>
                        <end id="end" name="End" g="100,0,32,32"/>
                    </bpm>
                    """);
                assertThat(context.getBean(ProcessEngine.class).execute(definition, Map.of()).isSuccess()).isTrue();
            });
    }
}
