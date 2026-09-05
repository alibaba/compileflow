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

import static com.alibaba.compileflow.workbench.server.api.problem.ApiProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessModelType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ExecutionLogControllerTest {
    @Test
    void listRejectsUnsupportedStatusBeforeServiceCall() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogController controller = new ExecutionLogController(logService);

        assertProblem(() -> controller.listExecutionLogs(null, "deployed", null, null, null, null, null, null, null,
                        null, null, null, null, null, null, 1, 20), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "status must be one of: success, failed, all");
        verify(logService, never()).search(any(ExecutionLogService.LogQuery.class), anyInt(), anyInt());
    }

    @Test
    void listRejectsInvalidPaginationBeforeServiceCall() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogController controller = new ExecutionLogController(logService);

        assertProblem(() -> controller.listExecutionLogs(null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, 0, 20), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "page must be greater than or equal to 1");
        verify(logService, never()).search(any(ExecutionLogService.LogQuery.class), anyInt(), anyInt());
    }

    @Test
    void listRejectsOversizedPageBeforeServiceCall() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogController controller = new ExecutionLogController(logService);

        assertProblem(() -> controller.listExecutionLogs(null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, 1, 101), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "pageSize must be less than or equal to 100");
        verify(logService, never()).search(any(ExecutionLogService.LogQuery.class), anyInt(), anyInt());
    }

    @Test
    void listRejectsNonPositiveRouteRevisionBeforeServiceCall() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogController controller = new ExecutionLogController(logService);

        assertProblem(() -> controller.listExecutionLogs(null, null, null, null, null, null, null, null, null, null,
                        null, null, null, "production", 0L, 1, 20), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "routeRevision must be greater than 0");
        verify(logService, never()).search(any(ExecutionLogService.LogQuery.class), anyInt(), anyInt());
    }

    @Test
    void listRejectsReversedTimeRangeBeforeServiceCall() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogController controller = new ExecutionLogController(logService);

        assertProblem(() -> controller.listExecutionLogs(null, null, null, "2026-07-26T00:00:01Z",
                        "2026-07-26T00:00:00Z", null, null, null, null, null, null, null, null, null, null, 1, 20),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "startTime must be before or equal to endTime");
        verify(logService, never()).search(any(ExecutionLogService.LogQuery.class), anyInt(), anyInt());
    }

    @Test
    void exportUsesEmptyFiltersForMissingBody() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogController controller = new ExecutionLogController(logService);
        when(logService.export(any(ExecutionLogService.LogQuery.class))).thenReturn(Collections.emptyList());

        ResponseEntity<byte[]> response = controller.exportExecutionLogs(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(byte[].class);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
            .startsWith(
                    "id,processCode,invocationId,parentInvocationId,callDepth,traceId," + "modelType,sourceDigest,status");
        verify(logService).export(any(ExecutionLogService.LogQuery.class));
    }

    @Test
    void exportNeutralizesSpreadsheetFormulaCells() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogEntity entity = new ExecutionLogEntity();
        entity.setId("log-1");
        entity.setProcessCode("payment.approve");
        entity.setInvocationId("inv-1");
        entity.setTraceId("trace-1");
        entity.setModelType(ProcessModelType.TBBPM);
        entity.setSourceDigest("a".repeat(64));
        entity.setNamespace("default");
        entity.setStatus("failed");
        entity.setDurationMs(12L);
        entity.setErrorCode("CF_EXEC_001");
        entity.setErrorMessage(" \t=HYPERLINK(\"https://example.invalid\")");
        entity.setLoggedAt(1_700_000_000_000L);
        when(logService.export(any(ExecutionLogService.LogQuery.class))).thenReturn(List.of(entity));
        ExecutionLogController controller = new ExecutionLogController(logService);

        ResponseEntity<byte[]> response = controller.exportExecutionLogs(null);

        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).contains("CF_EXEC_001").contains("\"' \t=HYPERLINK(\"\"https://example." + "invalid\"\")\"");
    }

    @Test
    void getByIdExposesTheStableErrorCode() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogEntity entity = new ExecutionLogEntity();
        entity.setId("log-1");
        entity.setProcessCode("payment.approve");
        entity.setInvocationId("inv-1");
        entity.setTraceId("trace-1");
        entity.setModelType(ProcessModelType.TBBPM);
        entity.setNamespace("default");
        entity.setStatus("failed");
        entity.setDurationMs(12L);
        entity.setErrorCode("CF_EXEC_001");
        entity.setErrorMessage("Rejected");
        entity.setLoggedAt(1_700_000_000_000L);
        when(logService.findById("log-1")).thenReturn(Optional.of(entity));
        ExecutionLogController controller = new ExecutionLogController(logService);

        ResponseEntity<ExecutionLogResponse> response = controller.getExecutionLog("log-1");

        assertThat(response.getBody().errorCode()).isEqualTo("CF_EXEC_001");
        assertThat(response.getBody().errorMessage()).isEqualTo("Rejected");
        assertThat(response.getBody().traceId()).isEqualTo("trace-1");
        assertThat(response.getBody().modelType()).isEqualTo(ProcessModelType.TBBPM);
    }

    @Test
    void purgeRejectsInvalidTimestampBeforeServiceCall() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogController controller = new ExecutionLogController(logService);

        assertProblem(() -> controller.purgeExecutionLogs(new PurgeExecutionLogsRequest("2026-07-05")),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "before must be an ISO-8601 timestamp");
        verify(logService, never()).purgeOlderThan(anyLong());
    }

    @Test
    void purgeReturnsBoundedProgress() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        when(logService.purgeOlderThan(2_000L)).thenReturn(new ExecutionLogService.PurgeResult(1, true));
        Instant now = Instant.parse("2026-07-26T01:02:03Z");
        ExecutionLogController controller = new ExecutionLogController(logService, Clock.fixed(now, ZoneOffset.UTC));

        ResponseEntity<PurgeExecutionLogsResponse> response =
                controller.purgeExecutionLogs(new PurgeExecutionLogsRequest("1970-01-01T00:00:02Z"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().deletedCount()).isEqualTo(1);
        assertThat(response.getBody().hasMore()).isTrue();
        assertThat(response.getBody().purgedAt()).isEqualTo(now.toString());
        verify(logService).purgeOlderThan(2_000L);
    }
}
