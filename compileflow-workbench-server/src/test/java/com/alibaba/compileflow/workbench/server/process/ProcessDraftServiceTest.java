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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.workbench.server.security.ServerIdentity;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;

class ProcessDraftServiceTest {
    private static ProcessDraftEntity flowEntity(long revision) {
        ProcessDraftEntity entity = new ProcessDraftEntity();
        entity.setCode("order.approve");
        entity.setName("Order Approval");
        entity.setType(ProcessModelType.BPMN);
        entity.setXml("<definitions/>");
        entity.setTagsJson("[]");
        entity.setCreatedAt(Instant.parse("2026-07-25T01:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-07-25T01:00:00Z"));
        entity.setCreatedBy("tester");
        entity.setRevision(revision);
        return entity;
    }

    @Test
    void storageIntegrityFailuresAreNotMisreportedAsDuplicateCodes() {
        ProcessDraftRepository repository = mock(ProcessDraftRepository.class);
        when(repository.currentTimestamp()).thenReturn(Instant.parse("2026-07-25T03:00:00Z"));
        DataIntegrityViolationException failure = new DataIntegrityViolationException("Invalid stored value");
        when(repository.saveAndFlush(any(ProcessDraftEntity.class))).thenThrow(failure);
        ProcessDraftService service = new ProcessDraftService(repository, mock(ServerIdentity.class));

        assertThatThrownBy(() -> service.createProcess(
                new ProcessDraftService.ProcessCreate("order.approve", "Order", ProcessModelType.BPMN, "<definitions/>",
                        null, List.of())))
            .isSameAs(failure);
    }

    @Test
    void createUsesIdentityEstablishedByAuthenticationBoundary() {
        ProcessDraftRepository repository = mock(ProcessDraftRepository.class);
        ServerIdentity identity = mock(ServerIdentity.class);
        Instant databaseTime = Instant.parse("2026-07-25T03:00:00Z");
        when(repository.currentTimestamp()).thenReturn(databaseTime);
        when(identity.principal()).thenReturn("bridge-service");
        when(repository.saveAndFlush(any(ProcessDraftEntity.class))).thenAnswer(invocation -> {
            ProcessDraftEntity entity = invocation.getArgument(0);
            entity.setRevision(0L);
            return entity;
        });
        ProcessDraftService service = new ProcessDraftService(repository, identity);

        ProcessDraftService.ProcessRecord result = service.createProcess(
                new ProcessDraftService.ProcessCreate("order.approve", "Order Approval", ProcessModelType.BPMN,
                        "<definitions/>", null, Collections.emptyList()));

        ArgumentCaptor<ProcessDraftEntity> entity = ArgumentCaptor.forClass(ProcessDraftEntity.class);
        verify(repository).saveAndFlush(entity.capture());
        assertThat(entity.getValue().getCreatedBy()).isEqualTo("bridge-service");
        assertThat(entity.getValue().getTagsJson()).isEqualTo("[]");
        assertThat(entity.getValue().getCreatedAt()).isEqualTo(databaseTime);
        assertThat(entity.getValue().getUpdatedAt()).isEqualTo(databaseTime);
        assertThat(result.createdBy()).isEqualTo("bridge-service");
        assertThat(result.tags()).isEmpty();
        assertThat(result.description()).isNull();
    }

    @Test
    void listUsesDatabaseProjectionPaginationAndLiteralKeywordMatching() {
        ProcessDraftRepository repository = mock(ProcessDraftRepository.class);
        ProcessDraftSummaryProjection projection = mock(ProcessDraftSummaryProjection.class);
        when(projection.getCode()).thenReturn("order.approve");
        when(projection.getName()).thenReturn("Order Approval");
        when(projection.getType()).thenReturn(ProcessModelType.BPMN);
        when(projection.getCreatedAt()).thenReturn(Instant.parse("2026-07-25T01:00:00Z"));
        when(projection.getUpdatedAt()).thenReturn(Instant.parse("2026-07-25T02:00:00Z"));
        when(projection.getTagsJson()).thenReturn("[\"priority,high\"]");
        when(projection.getRevision()).thenReturn(4L);
        when(repository.findSummaries(eq(ProcessModelType.BPMN), eq("%50!%!_!!%"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(projection), org.springframework.data.domain.PageRequest.of(1, 25), 31));
        ProcessDraftService service = new ProcessDraftService(repository, mock(ServerIdentity.class));
        ProcessDraftService.ProcessListQuery query =
                new ProcessDraftService.ProcessListQuery(ProcessModelType.BPMN, " 50%_! ", "updatedAt", "desc", 2, 25);

        ProcessDraftService.ProcessListResult result = service.listProcesses(query);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findSummaries(eq(ProcessModelType.BPMN), eq("%50!%!_!!%"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("code").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(result.total()).isEqualTo(26);
        assertThat(result.data())
            .singleElement()
            .satisfies(flow -> {
                assertThat(flow.code()).isEqualTo("order.approve");
                assertThat(flow.tags()).containsExactly("priority,high");
                assertThat(flow.revision()).isEqualTo(4L);
                assertThat(flow.description()).isNull();
            });
    }

    @Test
    void updateRejectsAStaleDraftRevisionBeforeMutation() {
        ProcessDraftRepository repository = mock(ProcessDraftRepository.class);
        ProcessDraftEntity entity = flowEntity(3L);
        when(repository.findById("order.approve")).thenReturn(Optional.of(entity));
        ProcessDraftService service = new ProcessDraftService(repository, mock(ServerIdentity.class));
        ProcessDraftService.ProcessUpdate update =
                new ProcessDraftService.ProcessUpdate("Changed", "<definitions/>", null, Collections.emptyList(), 2L);

        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> service.updateProcess("order.approve", update))
            .isInstanceOf(ProcessRevisionConflictException.class)
            .hasMessage("Process revision mismatch: code=order.approve, expected=2, current=3");
        verify(repository, never()).saveAndFlush(any(ProcessDraftEntity.class));
    }

    @Test
    void updateAndDuplicateRepresentMissingProcessesExplicitly() {
        ProcessDraftRepository repository = mock(ProcessDraftRepository.class);
        when(repository.findById("missing")).thenReturn(Optional.empty());
        ProcessDraftService service = new ProcessDraftService(repository, mock(ServerIdentity.class));

        assertThat(service.updateProcess("missing", new ProcessDraftService.ProcessUpdate(null, null, null, null, 0L)))
            .isEmpty();
        assertThat(service.duplicateProcess("missing", "copy", "Copy")).isEmpty();
    }
}
