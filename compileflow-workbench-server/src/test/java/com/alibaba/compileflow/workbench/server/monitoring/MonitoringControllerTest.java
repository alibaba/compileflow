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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class MonitoringControllerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T01:02:03Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static MonitoringController controller(ExecutionLogService logService) {
        return new MonitoringController(mock(ProcessDeploymentService.class), logService, CLOCK);
    }

    @Test
    void metricsUsesTheSharedExecutionLogAndPublishedTimeWindow() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        when(logService.aggregate(anyLong(), anyLong()))
            .thenReturn(new ExecutionLogService.ExecutionAggregate(8L, 6L, 2L, 80L));
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        when(deploymentService.countProcessesWithAliases("default")).thenReturn(7L);
        MonitoringController controller = new MonitoringController(deploymentService, logService, CLOCK);

        ResponseEntity<MonitoringMetricsResponse> response = controller.getMonitoringMetrics("6h");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().totalExecutions()).isEqualTo(8L);
        assertThat(response.getBody().scope()).isEqualTo("workbench_server");
        assertThat(response.getBody().successExecutions()).isEqualTo(6L);
        assertThat(response.getBody().failedExecutions()).isEqualTo(2L);
        assertThat(response.getBody().avgExecutionTime()).isEqualTo(10L);
        assertThat(response.getBody().processesWithAliases()).isEqualTo(7L);
        assertThat(response.getBody().timeRange()).isEqualTo("6h");
        assertThat(response.getBody().windowEnd()).isEqualTo(NOW.toString());
        assertThat(response.getBody().timestamp()).isEqualTo(NOW.toString());
    }

    @Test
    void metricsRejectsUnknownTimeRangeBeforeQuerying() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        MonitoringController controller = controller(logService);

        assertProblem(() -> controller.getMonitoringMetrics("2h"), org.springframework.http.HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "timeRange must be one of: 1h, 6h, 24h, 7d, 30d");
        verify(logService, never()).aggregate(anyLong(), anyLong());
    }

    @Test
    void trendsMapsDatabaseBucketsIntoTheCompleteSeries() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        when(logService.aggregateTrends(anyLong(), anyLong(), anyLong()))
            .thenReturn(List.of(new ExecutionLogService.TrendAggregate(0L, 3L, 2L, 1L)));
        MonitoringController controller = controller(logService);

        ResponseEntity<List<ExecutionTrendResponse>> response = controller.getExecutionTrends("1h", "5m");

        List<ExecutionTrendResponse> body = response.getBody();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body).hasSize(12);
        assertThat(body.get(0).executions()).isEqualTo(3L);
        assertThat(body.get(0).success()).isEqualTo(2L);
        assertThat(body.get(0).failed()).isEqualTo(1L);
    }

    @Test
    void trendsRejectsUnknownInterval() {
        MonitoringController controller = controller(mock(ExecutionLogService.class));

        assertProblem(() -> controller.getExecutionTrends("24h", "10m"), org.springframework.http.HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "interval must be one of: 1m, 5m, 1h, 1d");
    }

    @Test
    void topProcessesRejectsInvalidLimit() {
        MonitoringController controller = controller(mock(ExecutionLogService.class));

        assertProblem(() -> controller.getTopProcesses("24h", 0), org.springframework.http.HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "limit must be between 1 and 500");
    }

    @Test
    void errorsReturnGroupedCountsAndOmitUnavailableAttribution() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        when(logService.aggregateErrors(anyLong(), anyLong(), eq(20)))
            .thenReturn(List.of(
                    new ExecutionLogService.ErrorAggregate("payment.approve", "CF_EXEC_001", "Rejected", null, null,
                            null, null, null, null, 3L, 1_700_000_000_000L)));
        MonitoringController controller = controller(logService);

        ResponseEntity<List<ErrorSummaryResponse>> response = controller.getMonitoringErrors("24h", 20);

        assertThat(response.getBody())
            .singleElement()
            .satisfies(error -> {
                assertThat(error.processCode()).isEqualTo("payment.approve");
                assertThat(error.errorCode()).isEqualTo("CF_EXEC_001");
                assertThat(error.errorMessage()).isEqualTo("Rejected");
                assertThat(error.count()).isEqualTo(3L);
                assertThat(error.namespace()).isNull();
                assertThat(error.requestedVersion()).isNull();
                assertThat(error.effectiveVersion()).isNull();
                assertThat(error.routingSource()).isNull();
                assertThat(error.routeAlias()).isNull();
                assertThat(error.routeRevision()).isNull();
            });
    }

    @Test
    void versionDistributionTrimsProcessCodeFilter() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        when(logService.aggregateVersions(eq("payment.approve"), anyLong(), anyLong(), eq(10))).thenReturn(List.of());
        MonitoringController controller = controller(logService);

        ResponseEntity<List<VersionDistributionResponse>> response =
                controller.getVersionDistribution(" payment.approve ", "24h", 10);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(logService).aggregateVersions(eq("payment.approve"), anyLong(), anyLong(), eq(10));
    }

    @Test
    void versionDistributionRejectsInvalidLimit() {
        MonitoringController controller = controller(mock(ExecutionLogService.class));

        assertProblem(() -> controller.getVersionDistribution(null, "24h", -1),
                org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "limit must be between 1 and 500");
    }
}
