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
package com.alibaba.compileflow.workbench.server.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessToolingService;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessDefinitionPreflightServiceTest {
    @Test
    void rejectsInvalidInlineDefinitionBeforeCallingTheEngine() {
        ProcessEngine engine = mock(ProcessEngine.class);
        ProcessDefinitionPreflightService service = new ProcessDefinitionPreflightService(engine);

        assertThatThrownBy(() -> service.preflight("invalid code", ProcessModelType.BPMN, "<definitions/>"))
            .isInstanceOf(ProcessDefinitionPreflightService.InvalidProcessDefinitionException.class)
            .hasMessageContaining("code");
        verifyNoInteractions(engine);
    }

    @Test
    void preflightLintsAndCompilesTheExactDefinition() {
        ProcessEngine engine = mock(ProcessEngine.class);
        ProcessDefinitionPreflightService service = new ProcessDefinitionPreflightService(engine);

        ProcessToolingService tooling = mock(ProcessToolingService.class);
        when(engine.tooling()).thenReturn(tooling);
        service.preflight("payment.approve", ProcessModelType.BPMN, "<definitions/>");

        ArgumentCaptor<ProcessPreflightOptions> optionsCaptor = ArgumentCaptor.forClass(ProcessPreflightOptions.class);
        verify(tooling)
            .preflight(eq(ProcessDefinition.inline(ProcessModelType.BPMN, "payment.approve", "<definitions/>")),
                    optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().isLintEnabled()).isTrue();
        assertThat(optionsCaptor.getValue().isCompileEnabled()).isTrue();
    }
}
