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
package com.alibaba.compileflow.workbench.server.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import org.junit.jupiter.api.Test;

class WorkbenchStatusControllerTest {
    private static CompileFlowWorkbenchServerProperties properties(boolean previewExecutionEnabled) {
        CompileFlowWorkbenchServerProperties properties = mock(CompileFlowWorkbenchServerProperties.class);
        CompileFlowWorkbenchServerProperties.PreviewExecution previewExecution =
                mock(CompileFlowWorkbenchServerProperties.PreviewExecution.class);
        when(properties.getPreviewExecution()).thenReturn(previewExecution);
        when(previewExecution.isEnabled()).thenReturn(previewExecutionEnabled);
        return properties;
    }

    @Test
    void reportsTrustedDraftExecutionAsAvailableWhenEnabled() {
        WorkbenchStatusResponse response = new WorkbenchStatusController(properties(true)).getWorkbenchStatus();

        assertThat(response.engineAvailable()).isTrue();
        assertThat(response.message()).isEqualTo("Trusted draft execution is enabled");
    }

    @Test
    void reportsDraftExecutionAsUnavailableWhenDisabled() {
        WorkbenchStatusResponse response = new WorkbenchStatusController(properties(false)).getWorkbenchStatus();

        assertThat(response.engineAvailable()).isFalse();
        assertThat(response.message()).isEqualTo("Draft execution is disabled by server policy");
    }
}
