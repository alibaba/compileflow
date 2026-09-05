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
package com.alibaba.compileflow.workbench.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class ExecutionLogServiceTest {
    private static ExecutionLogService service(ExecutionLogRepository repository, int maxQueryRows) {
        return service(repository, maxQueryRows, 1_000);
    }

    private static ExecutionLogService service(ExecutionLogRepository repository, int maxQueryRows, int purgeBatchSize) {
        CompileFlowWorkbenchServerProperties properties = mock(CompileFlowWorkbenchServerProperties.class);
        CompileFlowWorkbenchServerProperties.ExecutionLog executionLog =
                mock(CompileFlowWorkbenchServerProperties.ExecutionLog.class);
        when(properties.getExecutionLog()).thenReturn(executionLog);
        when(executionLog.getMaxQueryRows()).thenReturn(maxQueryRows);
        when(executionLog.getPurgeBatchSize()).thenReturn(purgeBatchSize);
        return new ExecutionLogService(repository, properties);
    }

    @Test
    void persistKeepsSeparateRowsForRepeatedInvocationAttempts() {
        ExecutionLogRepository repository = mock(ExecutionLogRepository.class);
        ExecutionLogService service = service(repository, 10_000);
        long timestamp = System.currentTimeMillis();

        service.persist(
                new ExecutionLogRecord("payment.approve", "failed", 10L, "CF_EXEC_001", "boom", timestamp, "inv-retry-1",
                        null, 0, "trace-failed", "tenant-a", ProcessModelType.TBBPM, "a".repeat(64), "v2", "v2", "alias",
                        "production", 7L));
        service.persist(
                new ExecutionLogRecord("payment.approve", "success", 12L, null, null, timestamp, "inv-retry-1",
                        "inv-parent", 1, "trace-success", "tenant-a", ProcessModelType.TBBPM, "b".repeat(64), "v2", "v2",
                        "alias", "production", 7L));

        ArgumentCaptor<ExecutionLogEntity> captor = ArgumentCaptor.forClass(ExecutionLogEntity.class);
        verify(repository, times(2)).save(captor.capture());
        List<ExecutionLogEntity> rows = captor.getAllValues();
        assertThat(rows.get(0).getInvocationId()).isEqualTo("inv-retry-1");
        assertThat(rows.get(0).getTraceId()).isEqualTo("trace-failed");
        assertThat(rows.get(0).getModelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(rows.get(0).getSourceDigest()).isEqualTo("a".repeat(64));
        assertThat(rows.get(0).getErrorCode()).isEqualTo("CF_EXEC_001");
        assertThat(rows.get(1).getInvocationId()).isEqualTo("inv-retry-1");
        assertThat(rows.get(1).getParentInvocationId()).isEqualTo("inv-parent");
        assertThat(rows.get(1).getCallDepth()).isOne();
        assertThat(rows.get(1).getErrorCode()).isNull();
        assertThat(rows.get(1).getRouteAlias()).isEqualTo("production");
        assertThat(rows.get(1).getRouteRevision()).isEqualTo(7L);
        assertThat(rows.get(1).getId()).isNotEqualTo(rows.get(0).getId());
    }

    @Test
    void persistBoundsErrorMessagesToTheDatabaseContract() {
        ExecutionLogRepository repository = mock(ExecutionLogRepository.class);
        ExecutionLogService service = service(repository, 10_000);

        service.persist(
                new ExecutionLogRecord("payment.approve", "failed", 10L, "CF_EXEC_001", "x".repeat(4_097),
                        System.currentTimeMillis(), "inv-1", null, 0, "trace-1", "default", ProcessModelType.TBBPM,
                        "a".repeat(64), null, "v1", "alias", "production", 1L));

        ArgumentCaptor<ExecutionLogEntity> captor = ArgumentCaptor.forClass(ExecutionLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).hasSize(4_096);
    }

    @Test
    void exportFailsInsteadOfReturningACompleteLookingTruncatedResult() {
        ExecutionLogRepository repository = mock(ExecutionLogRepository.class);
        List<ExecutionLogEntity> rows =
                List.of(new ExecutionLogEntity(), new ExecutionLogEntity(), new ExecutionLogEntity());
        when(repository.findAllLimited(org.mockito.ArgumentMatchers.<Specification<ExecutionLogEntity>>any(),
                any(Sort.class), eq(3)))
            .thenReturn(rows);
        ExecutionLogService service = service(repository, 2);

        assertThatThrownBy(() -> service.export(ExecutionLogService.LogQuery.empty()))
            .isInstanceOf(ExecutionLogQueryLimitExceededException.class)
            .hasMessageContaining("configured limit of 2 rows");

        verify(repository)
            .findAllLimited(org.mockito.ArgumentMatchers.<Specification<ExecutionLogEntity>>any(), any(Sort.class),
                    eq(3));
    }

    @Test
    void canarySampleQueryUsesTheSameCompleteResultLimit() {
        ExecutionLogRepository repository = mock(ExecutionLogRepository.class);
        List<ExecutionLogEntity> rows =
                List.of(new ExecutionLogEntity(), new ExecutionLogEntity(), new ExecutionLogEntity());
        when(repository.findAliasExecutionsSince(eq("default"), eq("payment.approve"), eq("production"), eq(100L),
                any(Pageable.class)))
            .thenReturn(rows);
        ExecutionLogService service = service(repository, 2);

        assertThatThrownBy(() -> service.findForRouteSince("default", "payment.approve", "production", 100L))
            .isInstanceOf(ExecutionLogQueryLimitExceededException.class);
    }

    @Test
    void searchRejectsInvalidPaginationInsteadOfSilentlyNormalizingIt() {
        ExecutionLogService service = service(mock(ExecutionLogRepository.class), 10_000);

        assertThatThrownBy(() -> service.search(ExecutionLogService.LogQuery.empty(), 0, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("page must be greater than or equal to 1");
        assertThatThrownBy(() -> service.search(ExecutionLogService.LogQuery.empty(), 1, 501))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pageSize must be between 1 and 500");
    }

    @Test
    void retentionDeletionUsesAStableBoundedCandidatePage() {
        ExecutionLogRepository repository = mock(ExecutionLogRepository.class);
        when(repository.findIdsOlderThan(eq(1_000L), any(Pageable.class))).thenReturn(List.of("log-1", "log-2"));
        when(repository.deleteIds(List.of("log-1", "log-2"))).thenReturn(2);
        ExecutionLogService service = service(repository, 10_000, 2);

        assertThat(service.purgeOlderThan(1_000L)).isEqualTo(new ExecutionLogService.PurgeResult(2, false));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findIdsOlderThan(eq(1_000L), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(2);
        verify(repository).deleteIds(List.of("log-1", "log-2"));
        verify(repository).existsByLoggedAtLessThan(1_000L);
    }
}
