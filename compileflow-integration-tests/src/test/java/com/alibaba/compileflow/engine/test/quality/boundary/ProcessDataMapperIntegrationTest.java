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
package com.alibaba.compileflow.engine.test.quality.boundary;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessDataMapper;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessObservabilityConfig;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProcessDataMapperIntegrationTest {
    @Test
    void inputMappingFailureDoesNotStartTheProcess() {
        List<ProcessEvent> events = new CopyOnWriteArrayList<>();
        ProcessDataMapper mapper = new ProcessDataMapper() {
            @Override
            public Map<String, Object> toVariables(Object value) {
                throw new IllegalArgumentException("secret input");
            }

            @Override
            public <T> T fromVariables(Map<String, Object> variables, Class<T> targetType) {
                throw new AssertionError("Output mapping must not run");
            }
        };
        ProcessEngineConfig config = ProcessEngineTestFactory
            .builder()
            .discoverPlugins(false)
            .dataMapper(mapper)
            .observability(ProcessObservabilityConfig.builder().eventsAsync(false).build())
            .eventListener(events::add)
            .build();
        ProcessDefinition ref = ProcessDefinition.classpath(ProcessModelType.TBBPM, "bpm.java-code.javaCodeSum",
                "bpm.java-code.javaCodeSum".replace(".", "/") + ".bpm");

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessResult<String> result =
                    engine.execute(ref, new Object(), String.class, ProcessExecutionOptions.defaults());

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_EXEC_010.getCode());
            assertThat(result.getError().getMessage()).doesNotContain("secret input");
            assertThat(result.getExecution().getProcessCode()).isEqualTo(ref.code());
            assertThat(result.getExecution().getProcessVersion()).isNull();
            assertThat(events).isEmpty();
        }
    }

    @Test
    void outputMappingFailureIsReportedAfterTheProcessCompletes() {
        List<ProcessEvent> events = new CopyOnWriteArrayList<>();
        ProcessDataMapper mapper = new ProcessDataMapper() {
            @Override
            public Map<String, Object> toVariables(Object value) {
                return Map.of("inputA", 15, "inputB", 25);
            }

            @Override
            public <T> T fromVariables(Map<String, Object> variables, Class<T> targetType) {
                throw new IllegalArgumentException("secret output");
            }
        };
        ProcessEngineConfig config = ProcessEngineTestFactory
            .builder()
            .discoverPlugins(false)
            .dataMapper(mapper)
            .observability(ProcessObservabilityConfig.builder().eventsAsync(false).build())
            .eventListener(events::add)
            .build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessResult<String> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            "bpm.java-code.javaCodeSum", "bpm.java-code.javaCodeSum".replace(".", "/") + ".bpm"),
                    new Object(), String.class, ProcessExecutionOptions.defaults());

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_EXEC_009.getCode());
            assertThat(result.getError().getMessage())
                .contains("side effects may already have occurred")
                .doesNotContain("secret output");
            assertThat(events)
                .extracting(ProcessEvent::getType)
                .contains(ProcessEvent.Type.EXECUTION_STARTED, ProcessEvent.Type.EXECUTION_COMPLETED)
                .doesNotContain(ProcessEvent.Type.EXECUTION_FAILED);
        }
    }

    @Test
    void typedTriggerUsesTheConfiguredMapperOnBothSidesOfTheMapPipeline() {
        AtomicInteger inputMappings = new AtomicInteger();
        AtomicInteger outputMappings = new AtomicInteger();
        ProcessDataMapper mapper = new ProcessDataMapper() {
            @Override
            public Map<String, Object> toVariables(Object value) {
                inputMappings.incrementAndGet();
                return Map.of("taskData", "task-c");
            }

            @Override
            public <T> T fromVariables(Map<String, Object> variables, Class<T> targetType) {
                outputMappings.incrementAndGet();
                return targetType.cast("mapped-output");
            }
        };
        ProcessEngineConfig config =
                ProcessEngineTestFactory.builder().discoverPlugins(false).dataMapper(mapper).build();
        ProcessRef.Version ref = ProcessRef.version("default", "bpm.stateful.waitTaskProcess", "v1");
        ProcessDefinition definition =
                ProcessDefinition.classpath(ProcessModelType.TBBPM, ref.code(), "bpm/stateful/waitTaskProcess.bpm");

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            engine.runtime().load(ref, definition);

            ProcessResult<String> result = engine.trigger(ref, ProcessTrigger.at("waitTask1"), new Object(),
                    String.class, ProcessExecutionOptions.defaults());

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).isEqualTo("mapped-output");
            assertThat(inputMappings).hasValue(1);
            assertThat(outputMappings).hasValue(1);
            assertThat(result.getExecution().getInvocationId()).isNotBlank();
        }
    }
}
