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
import jakarta.persistence.EntityManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProcessDraftServicePersistenceTest {
    @Autowired
    private ProcessDraftService flowDraftService;
    @Autowired
    private ProcessDraftRepository repository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsDraftSourceBeyondTheMysqlTextByteLimit() {
        String xml = "<bpm><!--" + "\u4e2d".repeat(30_000) + "--></bpm>";
        ProcessDraftService.ProcessRecord created = flowDraftService.createProcess(
                new ProcessDraftService.ProcessCreate("persistence.large-source", "Large", ProcessModelType.TBBPM, xml,
                        null, List.of()));
        repository.flush();
        entityManager.clear();

        assertThat(flowDraftService.getProcess(created.code()))
            .hasValueSatisfying(persisted -> assertThat(persisted.xml()).isEqualTo(xml));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicateCreateCannotOverwriteAnExistingUneditedDraft() {
        String code = "persistence.create-conflict";
        try {
            flowDraftService.createProcess(
                    new ProcessDraftService.ProcessCreate(code, "Original", ProcessModelType.BPMN, "<definitions/>",
                            "keep", List.of("original")));

            assertThatThrownBy(() -> flowDraftService.createProcess(
                    new ProcessDraftService.ProcessCreate(code, "Replacement", ProcessModelType.TBBPM, "<bpm/>",
                            "replaced", List.of())))
                .isInstanceOf(ProcessAlreadyExistsException.class);

            assertThat(flowDraftService.getProcess(code)).hasValueSatisfying(retained -> {
                assertThat(retained.name()).isEqualTo("Original");
                assertThat(retained.type()).isEqualTo(ProcessModelType.BPMN);
                assertThat(retained.xml()).isEqualTo("<definitions/>");
                assertThat(retained.tags()).containsExactly("original");
                assertThat(retained.revision()).isZero();
            });
        } finally {
            repository.deleteById(code);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicationCannotOverwriteAnExistingTargetDraft() {
        String source = "persistence.duplicate-source";
        String target = "persistence.duplicate-target";
        try {
            flowDraftService.createProcess(
                    new ProcessDraftService.ProcessCreate(source, "Source", ProcessModelType.BPMN, "<definitions/>",
                            null, List.of()));
            flowDraftService.createProcess(
                    new ProcessDraftService.ProcessCreate(target, "Target", ProcessModelType.TBBPM, "<bpm/>", null,
                            List.of()));

            assertThatThrownBy(() -> flowDraftService.duplicateProcess(source, target, "Overwrite"))
                .isInstanceOf(ProcessAlreadyExistsException.class);
            assertThat(flowDraftService.getProcess(target)).hasValueSatisfying(retained -> {
                assertThat(retained.name()).isEqualTo("Target");
                assertThat(retained.type()).isEqualTo(ProcessModelType.TBBPM);
                assertThat(retained.revision()).isZero();
            });
        } finally {
            repository.deleteById(source);
            repository.deleteById(target);
        }
    }

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
