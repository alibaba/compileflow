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
package com.alibaba.compileflow.workbench.server.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineRegistry;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PreviewExecutionServiceTest {
    private static ProcessExecution execution(String invocationId) {
        Instant startedAt = Instant.parse("2026-07-26T00:00:00Z");
        return ProcessExecution
            .builder()
            .traceId("trace-preview")
            .invocationId(invocationId)
            .namespace("default")
            .processCode("draft.payment")
            .startedAt(startedAt)
            .completedAt(startedAt.plusMillis(5L))
            .build();
    }

    @Test
    void executesInlineDefinitionThroughTheFormatBoundEngine() {
        ProcessEngineRegistry registry = mock(ProcessEngineRegistry.class);
        ProcessEngine engine = mock(ProcessEngine.class);
        when(registry.get(ProcessModelType.BPMN)).thenReturn(engine);
        when(engine.execute(any(ProcessDefinition.class), anyMap(), any(ProcessExecutionOptions.class)))
            .thenAnswer(invocation -> {
                ProcessExecutionOptions options = invocation.getArgument(2);
                return ProcessResult.success(Map.of("approved", true), execution(options.getInvocationId()));
            });
        PreviewExecutionService service = new PreviewExecutionService(registry);

        ProcessExecutionResponse response =
                service.execute("draft.payment", ProcessModelType.BPMN, "<definitions/>", null, Map.of("amount", 100));

        assertThat(response.success()).isTrue();
        assertThat(response.result()).containsEntry("approved", true);
        assertThat(response.invocationId()).startsWith("inv-");
        assertThat(response.routing().namespace()).isEqualTo("default");
        assertThat(response.routing().effectiveVersion()).isNull();
        ArgumentCaptor<ProcessDefinition> definition = ArgumentCaptor.forClass(ProcessDefinition.class);
        ArgumentCaptor<ProcessExecutionOptions> options = ArgumentCaptor.forClass(ProcessExecutionOptions.class);
        verify(engine).execute(definition.capture(), anyMap(), options.capture());
        assertThat(definition.getValue()).isEqualTo(ProcessDefinition.inline("draft.payment", "<definitions/>"));
        assertThat(options.getValue().getInvocationId()).isEqualTo(response.invocationId());
        assertThat(options.getValue().getAliasRouting()).isEqualTo(AliasRoutingOptions.defaults());
    }
}
