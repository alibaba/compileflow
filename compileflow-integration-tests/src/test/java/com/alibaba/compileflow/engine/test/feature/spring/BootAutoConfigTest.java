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

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEnginePropertiesAutoConfiguration;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEngineAutoConfiguration;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties.ProcessEngineProperties;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {ConfigurationPropertiesAutoConfiguration.class,
        CompileFlowEnginePropertiesAutoConfiguration.class,
        CompileFlowEngineAutoConfiguration.class, ProcessEngineTestConfiguration.class}, properties = {"compileflow."
        + "engine.executor.runtime-load.max-concurrency=4",
        "compileflow.engine.executor.action-timeout.max-concurrency=8",
        "compileflow.engine.components.allowed-beans[0]=ktvService"})
@ActiveProfiles("test")
public class BootAutoConfigTest {
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ProcessEngineProperties properties;
    @Autowired
    private ProcessEngine processEngine;
    @Autowired
    private ProcessEngineConfig processEngineConfig;

    @Test
    void shouldBindPropertiesAndCreateEngine() {
        assertThat(applicationContext).as("Application context should be initialized").isNotNull();
        assertThat(properties).as("Properties should be bound").isNotNull();

        assertThat(properties.getRuntimeMode()).isEqualTo(
                com.alibaba.compileflow.engine.config.ProcessRuntimeMode.COMPILED);
        assertThat(properties.getExecutor().getRuntimeLoad().getMaxConcurrency())
            .as("Runtime-load concurrency should be 4")
            .isEqualTo(4);
        assertThat(properties.getExecutor().getActionTimeout().getMaxConcurrency())
            .as("Timed-action concurrency should be 8")
            .isEqualTo(8);

        assertThat(processEngine).as("ProcessEngine should be auto-configured").isNotNull();

        assertThat(processEngineConfig).as("Config should be created from properties").isNotNull();
        assertThat(processEngineConfig.getRuntimeMode()).isEqualTo(properties.getRuntimeMode());
        assertThat(processEngineConfig.getExecutorConfig().getRuntimeLoadMaxConcurrency())
            .as("Config runtime-load concurrency should match")
            .isEqualTo(4);
        assertThat(processEngineConfig.getExecutorConfig().getActionTimeoutMaxConcurrency())
            .as("Config action-timeout concurrency should match")
            .isEqualTo(8);
    }

    @Test
    void shouldExecuteSimpleKtvProcess() {
        String code = "bpm.ktv.ktvExample";
        Map<String, Object> context = new HashMap<>();
        context.put("pList", Lists.newArrayList("yusu"));

        ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                        code, code.replace(".", "/") + ".bpm"), context);

        assertThat(result.getOutput()).as("Price should be calculated as 30").containsEntry("price", 30);
    }
}
