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
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
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
import org.junit.jupiter.api.Test;

class ProcessTriggerPipelineIntegrationTest {
    @Test
    void exactVersionTriggerUsesTheNormalRuntimeResultAndEventPipeline() {
        List<ProcessEvent> events = new CopyOnWriteArrayList<>();
        ProcessEngineConfig config = ProcessEngineTestFactory
            .builder()
            .discoverPlugins(false)
            .observability(ProcessObservabilityConfig.builder().eventsAsync(false).build())
            .eventListener(events::add)
            .build();
        ProcessRef.Version ref = ProcessRef.version("default", "bpm.stateful.waitTaskProcess", "v1");
        ProcessDefinition definition =
                ProcessDefinition.classpath(ProcessModelType.TBBPM, ref.code(), "bpm/stateful/waitTaskProcess.bpm");

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            engine.runtime().load(ref, definition);

            ProcessResult<Map<String, Object>> result = engine.trigger(ref, ProcessTrigger.at("waitTask1"),
                    Map.of("taskData", "task-a", "processedData", "path-a"),
                    ProcessExecutionOptions.builder().invocationId("trigger-invocation-1").build());

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getExecution().getProcessVersion()).isEqualTo(ref);
            assertThat(result.getExecution().getInvocationId()).isEqualTo("trigger-invocation-1");
            assertThat(events)
                .extracting(ProcessEvent::getType)
                .contains(ProcessEvent.Type.TRIGGER_STARTED, ProcessEvent.Type.TRIGGER_COMPLETED)
                .doesNotContain(ProcessEvent.Type.EXECUTION_STARTED, ProcessEvent.Type.EXECUTION_COMPLETED,
                        ProcessEvent.Type.TRIGGER_FAILED);
        }
    }

    @Test
    void repeatedTriggersAreIndependentNewInvocations() {
        ProcessEngineConfig config = ProcessEngineTestFactory.builder().discoverPlugins(false).build();
        ProcessRef.Version ref = ProcessRef.version("default", "bpm.stateful.waitTaskProcess", "v1");
        ProcessDefinition definition =
                ProcessDefinition.classpath(ProcessModelType.TBBPM, ref.code(), "bpm/stateful/waitTaskProcess.bpm");

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            engine.runtime().load(ref, definition);

            ProcessResult<Map<String, Object>> first = engine.trigger(ref, ProcessTrigger.at("waitTask1"),
                    Map.of("taskData", "task-a"), ProcessExecutionOptions.defaults());
            ProcessResult<Map<String, Object>> second = engine.trigger(ref, ProcessTrigger.at("waitTask1"),
                    Map.of("taskData", "task-b"), ProcessExecutionOptions.defaults());

            assertThat(first.isSuccess()).as(String.valueOf(first.getError())).isTrue();
            assertThat(second.isSuccess()).as(String.valueOf(second.getError())).isTrue();
            assertThat(first.getExecution().getInvocationId()).isNotBlank();
            assertThat(second.getExecution().getInvocationId()).isNotBlank().isNotEqualTo(first
                .getExecution()
                .getInvocationId());
            assertThat(first.getExecution()).isNotSameAs(second.getExecution());
            assertThat(first.getOutput()).isNotSameAs(second.getOutput());
        }
    }
}
