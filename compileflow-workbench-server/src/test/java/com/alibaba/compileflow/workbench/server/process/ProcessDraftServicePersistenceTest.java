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

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProcessDraftServicePersistenceTest {
    @Autowired
    private ProcessDraftService flowDraftService;

    @Test
    void roundTripsStructuredTagsAndEnforcesDraftRevision() {
        ProcessDraftService.ProcessRecord created = flowDraftService.createProcess(
                new ProcessDraftService.ProcessCreate("persistence.order", "Order", ProcessModelType.BPMN,
                        "<definitions/>", "Original", List.of("priority,high", "finance")));

        assertThat(created.revision()).isZero();
        assertThat(created.tags()).containsExactly("priority,high", "finance");

        ProcessDraftService.ProcessUpdate update = new ProcessDraftService.ProcessUpdate("Updated Order",
                "<definitions id=\"updated\"/>", null, List.of("priority,high"), created.revision());
        ProcessDraftService.ProcessRecord updated = flowDraftService
            .updateProcess(created.code(), update)
            .orElseThrow();

        assertThat(updated.revision()).isEqualTo(1L);
        assertThat(updated.description()).isNull();
        assertThat(updated.tags()).containsExactly("priority,high");
        assertThat(flowDraftService.getProcess(created.code())).hasValueSatisfying(persisted -> {
            assertThat(persisted.revision()).isEqualTo(1L);
            assertThat(persisted.tags()).containsExactly("priority,high");
        });

        assertThatThrownBy(() -> flowDraftService.updateProcess(created.code(), update))
            .isInstanceOf(ProcessRevisionConflictException.class)
            .hasMessage("Process revision mismatch: code=persistence.order, " + "expected=0, current=1");
    }
}
