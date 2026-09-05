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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.workbench.server.api.problem.ApiProblemException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PreviewExecutionControllerTest {
    @Test
    void executesExplicitDefinitionWithoutManagedRouting() {
        PreviewExecutionService service = mock(PreviewExecutionService.class);
        ProcessExecutionResponse expected = mock(ProcessExecutionResponse.class);
        when(service.execute("draft.payment", ProcessModelType.BPMN, "<definitions/>", "inv-1", Map.of("amount", 100)))
            .thenReturn(expected);
        PreviewExecutionController controller = new PreviewExecutionController(service);

        ProcessExecutionResponse actual = controller.executePreview(
                new PreviewExecutionRequest(" draft.payment ", ProcessModelType.BPMN, "<definitions/>", "inv-1",
                        Map.of("amount", 100)));

        assertThat(actual).isSameAs(expected);
        verify(service).execute("draft.payment", ProcessModelType.BPMN, "<definitions/>", "inv-1", Map.of("amount", 100));
    }

    @Test
    void rejectsNullRequestBeforeCallingService() {
        PreviewExecutionController controller = new PreviewExecutionController(mock(PreviewExecutionService.class));

        assertThatThrownBy(() -> controller.executePreview(null))
            .isInstanceOf(ApiProblemException.class)
            .hasMessageContaining("request body is required");
    }
}
