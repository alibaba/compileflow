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

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.workbench.server.api.problem.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aggregates the shared persisted execution log for the Workbench monitoring view.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    private static final int MAX_MONITORING_LIMIT = 500;
    private static final String DEFAULT_TIME_RANGE = "24h";
    private static final String METRICS_SCOPE = "workbench_server";
    private static final Set<String> INTERVALS = Set.of("1m", "5m", "1h", "1d");
    private final ProcessDeploymentService deploymentService;
    private final ExecutionLogService logService;
    private final Clock clock;

    @Autowired
    public MonitoringController(ProcessDeploymentService deploymentService, ExecutionLogService logService) {
        this(deploymentService, logService, Clock.systemUTC());
    }

    MonitoringController(ProcessDeploymentService deploymentService, ExecutionLogService logService, Clock clock) {
        this.deploymentService = deploymentService;
        this.logService = logService;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static List<ExecutionTrendResponse> emptyTrend(long rangeStartMs, TrendBucketResolver.BucketLayout buckets) {
        List<ExecutionTrendResponse> result = new ArrayList<>(buckets.bucketCount());
        for (int i = 0; i < buckets.bucketCount(); i++) {
            long bucketStartMs = rangeStartMs + (long) i * buckets.bucketMs();
            result.add(new ExecutionTrendResponse(Instant.ofEpochMilli(bucketStartMs).toString(), 0L, 0L, 0L));
        }
        return result;
    }

    private static TopProcessStatsResponse toProcessResponse(ExecutionLogService.ProcessAggregate aggregate) {
        return new TopProcessStatsResponse(aggregate.processCode(), aggregate.executionCount(),
                aggregate.executionCount() == 0L ? 0L : aggregate.totalDurationMs() / aggregate.executionCount(),
                percentage(aggregate.successCount(), aggregate.executionCount()));
    }

    private static ErrorSummaryResponse toErrorResponse(ExecutionLogService.ErrorAggregate aggregate) {
        return new ErrorSummaryResponse(aggregate.processCode(), aggregate.errorCode(),
                valueOr(aggregate.errorMessage(), "Unknown"), textOrNull(aggregate.namespace()),
                textOrNull(aggregate.requestedVersion()), textOrNull(aggregate.effectiveVersion()),
                textOrNull(aggregate.routingSource()), textOrNull(aggregate.routeAlias()), aggregate.routeRevision(),
                aggregate.occurrenceCount(), Instant.ofEpochMilli(aggregate.lastOccurred()).toString());
    }

    private static VersionDistributionResponse toVersionResponse(ExecutionLogService.VersionAggregate aggregate) {
        return new VersionDistributionResponse(aggregate.processCode(), valueOr(aggregate.namespace(), "default"),
                valueOr(aggregate.effectiveVersion(), "unresolved"), valueOr(aggregate.routingSource(), "unknown"),
                valueOr(aggregate.routeAlias(), "unattributed"), aggregate.executionCount(), aggregate.successCount(),
                aggregate.failedCount(),
                aggregate.executionCount() == 0L ? 0L : aggregate.totalDurationMs() / aggregate.executionCount(),
                percentage(aggregate.successCount(), aggregate.executionCount()));
    }

    private static double percentage(long numerator, long denominator) {
        return denominator == 0L ? 0D : (double) numerator / denominator * 100D;
    }

    private static void requireInterval(String interval) {
        if (interval != null && !INTERVALS.contains(interval)) {
            throw ApiProblemException.invalidRequest("interval must be one of: 1m, 5m, 1h, 1d");
        }
    }

    private static int resolveLimit(int limit, String fieldName) {
        if (limit < 1 || limit > MAX_MONITORING_LIMIT) {
            throw ApiProblemException.invalidRequest(fieldName + " must be between 1 and " + MAX_MONITORING_LIMIT);
        }
        return limit;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String textOrNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /**
     * Returns aggregate execution metrics for one bounded time range.
     *
     * @param timeRange named monitoring time range
     * @return retained execution-log metrics and process count with Aliases
     */
    @GetMapping("/metrics")
    public ResponseEntity<MonitoringMetricsResponse> getMonitoringMetrics(
            @RequestParam(defaultValue = DEFAULT_TIME_RANGE) String timeRange) {
        TimeWindow window = resolveWindow(timeRange);
        ExecutionLogService.ExecutionAggregate aggregate = logService.aggregate(window.startMs(), window.endMs());
        String windowEnd = Instant.ofEpochMilli(window.endMs() - 1L).toString();
        return ResponseEntity.ok(
                new MonitoringMetricsResponse(METRICS_SCOPE, aggregate.totalExecutions(), aggregate.successExecutions(),
                        aggregate.failedExecutions(), aggregate.averageDurationMs(),
                        deploymentService.countProcessesWithAliases(ProcessRef.DEFAULT_NAMESPACE), window.name(),
                        Instant.ofEpochMilli(window.startMs()).toString(), windowEnd, windowEnd));
    }

    /**
     * Returns time-bucketed execution trends.
     *
     * @param timeRange named monitoring time range
     * @param interval  optional bucket interval
     * @return trend buckets, or {@code 400} when the range or interval is invalid
     */
    @GetMapping("/trends")
    public ResponseEntity<List<ExecutionTrendResponse>> getExecutionTrends(
            @RequestParam(defaultValue = DEFAULT_TIME_RANGE) String timeRange,
            @RequestParam(required = false) String interval) {
        String normalizedInterval = StringUtils.trimToNull(interval);
        TimeWindow window = resolveWindow(timeRange);
        requireInterval(normalizedInterval);
        TrendBucketResolver.BucketLayout buckets = TrendBucketResolver.resolve(window.hours(), normalizedInterval);
        List<ExecutionTrendResponse> result = emptyTrend(window.startMs(), buckets);
        for (ExecutionLogService.TrendAggregate aggregate :
                logService.aggregateTrends(window.startMs(), window.endMs(), buckets.bucketMs())) {
            if (aggregate.bucketIndex() < 0L || aggregate.bucketIndex() >= result.size()) {
                continue;
            }
            ExecutionTrendResponse point = result.get((int) aggregate.bucketIndex());
            result.set((int) aggregate.bucketIndex(),
                    new ExecutionTrendResponse(point.time(), aggregate.executionCount(), aggregate.successCount(),
                            aggregate.failedCount()));
        }
        return ResponseEntity.ok(List.copyOf(result));
    }

    /**
     * Returns processes ordered by execution count in one time range.
     *
     * @param timeRange named monitoring time range
     * @param limit     maximum number of processes to return
     * @return top process metrics, or {@code 400} for invalid arguments
     */
    @GetMapping("/top-processes")
    public ResponseEntity<List<TopProcessStatsResponse>> getTopProcesses(
            @RequestParam(defaultValue = DEFAULT_TIME_RANGE) String timeRange,
            @RequestParam(defaultValue = "10") int limit) {
        TimeWindow window = resolveWindow(timeRange);
        int pageLimit = resolveLimit(limit, "limit");
        List<TopProcessStatsResponse> result = logService
            .aggregateProcesses(window.startMs(), window.endMs(), pageLimit)
            .stream()
            .map(MonitoringController::toProcessResponse)
            .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Returns grouped execution failures in one time range.
     *
     * @param timeRange named monitoring time range
     * @param limit     maximum number of failure groups to return
     * @return recent failure groups, or {@code 400} for invalid arguments
     */
    @GetMapping("/errors")
    public ResponseEntity<List<ErrorSummaryResponse>> getMonitoringErrors(
            @RequestParam(defaultValue = DEFAULT_TIME_RANGE) String timeRange,
            @RequestParam(defaultValue = "20") int limit) {
        TimeWindow window = resolveWindow(timeRange);
        int pageLimit = resolveLimit(limit, "limit");
        List<ErrorSummaryResponse> result = logService
            .aggregateErrors(window.startMs(), window.endMs(), pageLimit)
            .stream()
            .map(MonitoringController::toErrorResponse)
            .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Returns execution distribution by effective version in one time range.
     *
     * @param processCode  optional process code filter
     * @param timeRange named monitoring time range
     * @param limit     maximum number of distribution rows to return
     * @return version distribution rows, or {@code 400} for invalid arguments
     */
    @GetMapping("/version-distribution")
    public ResponseEntity<List<VersionDistributionResponse>> getVersionDistribution(
            @RequestParam(required = false) String processCode,
            @RequestParam(defaultValue = DEFAULT_TIME_RANGE) String timeRange,
            @RequestParam(defaultValue = "20") int limit) {
        TimeWindow window = resolveWindow(timeRange);
        int pageLimit = resolveLimit(limit, "limit");
        List<VersionDistributionResponse> result = logService
            .aggregateVersions(StringUtils.trimToNull(processCode), window.startMs(), window.endMs(), pageLimit)
            .stream()
            .map(MonitoringController::toVersionResponse)
            .toList();
        return ResponseEntity.ok(result);
    }

    private TimeWindow resolveWindow(String timeRange) {
        String normalized = StringUtils.defaultIfBlank(timeRange, DEFAULT_TIME_RANGE).trim();
        int hours = switch (normalized) {
            case "1h" -> 1;
            case "6h" -> 6;
            case "24h" -> 24;
            case "7d" -> 168;
            case "30d" -> 720;
            default -> throw ApiProblemException.invalidRequest("timeRange must be one of: 1h, 6h, 24h, 7d, 30d");
        };
        long endMs = clock.millis() + 1L;
        return new TimeWindow(normalized, hours, endMs - TimeUnit.HOURS.toMillis(hours), endMs);
    }

    private record TimeWindow(String name, int hours, long startMs, long endMs) {}
}
