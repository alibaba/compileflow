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
package com.alibaba.compileflow.workbench.server.deployment;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.workbench.server.monitoring.ExecutionLogEntity;
import com.alibaba.compileflow.workbench.server.monitoring.ExecutionLogService;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Evaluates canary health from persisted execution statistics.
 *
 * @author yusu
 */
@Service
public class CanaryHealthService {
    static final String METRICS_SCOPE = "workbench_server";
    static final String METRICS_SOURCE = "execution_logs";
    static final long DEFAULT_LOOKBACK_MS = 10 * 60 * 1000L;
    static final int DEFAULT_MIN_CANARY_SAMPLES = 20;
    static final double DEFAULT_MAX_CANARY_ERROR_RATE = 0.05D;
    private final ExecutionLogService executionLogService;
    private final DeploymentService deploymentService;
    private final Clock clock;

    @Autowired
    public CanaryHealthService(ExecutionLogService executionLogService, DeploymentService deploymentService) {
        this(executionLogService, deploymentService, Clock.systemUTC());
    }

    CanaryHealthService(ExecutionLogService executionLogService, DeploymentService deploymentService, Clock clock) {
        this.executionLogService = executionLogService;
        this.deploymentService = deploymentService;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static String decide(VersionStats canary, CanaryHealthRequest request) {
        if (canary.samples < request.minCanarySamples()) {
            return "insufficient_data";
        }
        if (canary.errorRate() > request.maxCanaryErrorRate()) {
            return "unhealthy";
        }
        if (request.maxCanaryP95Ms() > 0 && canary.p95DurationMs() > request.maxCanaryP95Ms()) {
            return "unhealthy";
        }
        return "healthy";
    }

    private static String reason(String decision, VersionStats canary, CanaryHealthRequest request) {
        if ("insufficient_data".equals(decision)) {
            return "Canary sample count is below minCanarySamples.";
        }
        if ("healthy".equals(decision)) {
            return "Canary metrics are within configured thresholds.";
        }
        if (canary.errorRate() > request.maxCanaryErrorRate()) {
            return "Canary error rate breached maxCanaryErrorRate.";
        }
        return "Canary p95 latency breached maxCanaryP95Ms.";
    }

    private static void requireCanaryDeployment(DeploymentView deployment) {
        if (!"canary".equalsIgnoreCase(StringUtils.trimToEmpty(deployment.strategy))) {
            throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT,
                    "Canary health evaluation requires a canary deployment: " + deployment.id);
        }
        if (!"in_progress".equalsIgnoreCase(StringUtils.trimToEmpty(deployment.status))) {
            throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT,
                    "Canary health evaluation requires an active canary deployment: " + deployment.id);
        }
        if (StringUtils.isBlank(deployment.version) || StringUtils.isBlank(deployment.baselineVersion)) {
            throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT,
                    "Canary health evaluation requires target and baseline versions.");
        }
        if (StringUtils.isBlank(deployment.alias)) {
            throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT,
                    "Canary health evaluation requires a route alias.");
        }
    }

    private static long rolloutStartedAt(DeploymentView deployment) {
        String createdAt = StringUtils.trimToNull(deployment.createdAt);
        if (createdAt == null) {
            throw invalidCreationTimestamp(deployment, null);
        }
        try {
            return Instant.parse(createdAt).toEpochMilli();
        } catch (DateTimeException | ArithmeticException failure) {
            throw invalidCreationTimestamp(deployment, failure);
        }
    }

    private static DeploymentException invalidCreationTimestamp(DeploymentView deployment, Throwable cause) {
        String message = "Canary deployment has an invalid creation timestamp: " + deployment.id;
        return DeploymentException.of(DeploymentErrorCode.INTERNAL_ERROR, message, cause);
    }

    public Optional<CanaryHealthEvaluationResponse> evaluate(String deploymentId, CanaryHealthRequest request) {
        Optional<DeploymentView> found = deploymentService.getDeployment(deploymentId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        DeploymentView deployment = found.orElseThrow();
        requireCanaryDeployment(deployment);
        CanaryHealthRequest thresholds = Objects.requireNonNull(request, "request");

        long now = clock.millis();
        long cutoff = Math.max(now - thresholds.lookbackMs(), rolloutStartedAt(deployment));
        VersionStats canary = new VersionStats(deployment.version);
        VersionStats baseline = new VersionStats(deployment.baselineVersion);
        for (ExecutionLogEntity log :
                executionLogService.findForRouteSince(ProcessRef.DEFAULT_NAMESPACE, deployment.processCode,
                        deployment.alias, cutoff)) {
            if (Objects.equals(log.getEffectiveVersion(), deployment.version)) {
                canary.add(log);
            } else if (Objects.equals(log.getEffectiveVersion(), deployment.baselineVersion)) {
                baseline.add(log);
            }
        }

        String decision = decide(canary, thresholds);
        return Optional.of(
                new CanaryHealthEvaluationResponse(DeploymentResponseMapper.toResponse(deployment), METRICS_SCOPE,
                        METRICS_SOURCE, decision, reason(decision, canary, thresholds), canary.toResponse(),
                        baseline.toResponse(), thresholds.toResponse()));
    }

    /**
     * Immutable canary health evaluation thresholds.
     */
    public record CanaryHealthRequest(long lookbackMs, int minCanarySamples, double maxCanaryErrorRate,
            long maxCanaryP95Ms) {
        public CanaryHealthRequest {
            if (lookbackMs <= 0) {
                throw new IllegalArgumentException("lookbackMs must be greater than 0");
            }
            if (minCanarySamples <= 0) {
                throw new IllegalArgumentException("minCanarySamples must be greater than 0");
            }
            if (maxCanaryErrorRate < 0D || maxCanaryErrorRate > 1D) {
                throw new IllegalArgumentException("maxCanaryErrorRate must be between 0 and 1");
            }
            if (maxCanaryP95Ms < 0) {
                throw new IllegalArgumentException("maxCanaryP95Ms must be greater than or equal to 0");
            }
        }

        /**
         * Creates validated thresholds with defaults for absent fields.
         *
         * @param lookbackMs         optional lookback window
         * @param minCanarySamples   optional minimum sample count
         * @param maxCanaryErrorRate optional maximum error rate
         * @param maxCanaryP95Ms     optional p95 latency limit
         * @return immutable validated thresholds
         */
        public static CanaryHealthRequest of(Long lookbackMs, Integer minCanarySamples, Double maxCanaryErrorRate,
                Long maxCanaryP95Ms) {
            return new CanaryHealthRequest(lookbackMs == null ? DEFAULT_LOOKBACK_MS : lookbackMs,
                    minCanarySamples == null ? DEFAULT_MIN_CANARY_SAMPLES : minCanarySamples,
                    maxCanaryErrorRate == null ? DEFAULT_MAX_CANARY_ERROR_RATE : maxCanaryErrorRate,
                    maxCanaryP95Ms == null ? 0L : maxCanaryP95Ms);
        }

        private CanaryHealthThresholdsResponse toResponse() {
            return new CanaryHealthThresholdsResponse(lookbackMs(), minCanarySamples(), maxCanaryErrorRate(),
                    maxCanaryP95Ms());
        }
    }

    private static final class VersionStats {
        private final String version;
        private final List<Long> durations = new ArrayList<>();
        private int samples;
        private int failures;

        private VersionStats(String version) {
            this.version = version;
        }

        private void add(ExecutionLogEntity log) {
            samples++;
            durations.add(log.getDurationMs());
            if (!"success".equalsIgnoreCase(StringUtils.trimToEmpty(log.getStatus()))) {
                failures++;
            }
        }

        private double errorRate() {
            return samples == 0 ? 0D : (double) failures / samples;
        }

        private long p95DurationMs() {
            if (durations.isEmpty()) {
                return 0L;
            }
            List<Long> sorted = new ArrayList<>(durations);
            Collections.sort(sorted);
            int index = (int) Math.ceil(sorted.size() * 0.95D) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }

        private CanaryVersionStatsResponse toResponse() {
            return new CanaryVersionStatsResponse(version, samples, failures, errorRate(), p95DurationMs());
        }
    }
}
