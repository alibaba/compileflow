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

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Application facade for persisted whole-process asynchronous invocations.
 *
 * @author yusu
 */
@Service
public class AsyncInvocationService {
    public static final String STATUS_QUEUED = AsyncInvocationWorker.STATUS_QUEUED;
    public static final String STATUS_RUNNING = AsyncInvocationWorker.STATUS_RUNNING;
    public static final String STATUS_SUCCEEDED = AsyncInvocationWorker.STATUS_SUCCEEDED;
    public static final String STATUS_DEAD_LETTER = AsyncInvocationWorker.STATUS_DEAD_LETTER;
    static final int LIFECYCLE_PHASE = AsyncInvocationWorker.LIFECYCLE_PHASE;
    private static final int DEFAULT_MAX_ATTEMPTS = 1;
    private static final long DEFAULT_RETRY_DELAY_MS = 1000L;
    private static final int MAX_REQUEUE_LIMIT = 500;
    private static final int MAX_ATTEMPT_PAGE_SIZE = 100;
    private static final int MAX_ATTEMPTS = 100;
    private static final long MAX_RETRY_DELAY_MS = Duration.ofDays(7).toMillis();
    private static final Sort DISPATCH_ORDER =
            Sort.by(Sort.Order.asc("availableAt"), Sort.Order.asc("createdAt"), Sort.Order.asc("invocationId"));
    private static final Sort LIST_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("invocationId"));
    private final AsyncInvocationRepository repository;
    private final AsyncInvocationStore store;
    private final AsyncInvocationPayloadCodec payloadCodec;
    private final AsyncInvocationWorker worker;
    private final PublishedProcessExecutionService executionService;

    @Autowired
    public AsyncInvocationService(AsyncInvocationRepository repository, AsyncInvocationStore store,
            AsyncInvocationWorker worker, PublishedProcessExecutionService executionService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.store = Objects.requireNonNull(store, "store");
        this.payloadCodec = new AsyncInvocationPayloadCodec();
        this.worker = Objects.requireNonNull(worker, "worker");
        this.executionService = Objects.requireNonNull(executionService, "executionService");
    }

    AsyncInvocationService(AsyncInvocationRepository repository, AsyncInvocationStore store,
            PublishedProcessExecutionService executionService, VersionRuntimeManager versionRuntimeManager,
            Executor executor, int concurrency, Duration leaseDuration) {
        this(repository, store,
                new AsyncInvocationWorker(repository, store, executionService, versionRuntimeManager, executor,
                        concurrency, leaseDuration), executionService);
    }

    AsyncInvocationService(AsyncInvocationRepository repository, AsyncInvocationStore store,
            PublishedProcessExecutionService executionService, VersionRuntimeManager versionRuntimeManager,
            CompileFlowWorkbenchServerProperties properties,
            org.springframework.boot.autoconfigure.context.LifecycleProperties lifecycleProperties) {
        this(repository, store,
                new AsyncInvocationWorker(repository, store, executionService, versionRuntimeManager, properties,
                        lifecycleProperties), executionService);
    }

    public AsyncInvocationResponse submit(String processCode, AsyncInvocationSubmitRequest request) {
        if (request == null) {
            throw new InvalidAsyncInvocationRequestException("routing with exactly one of version or alias is required");
        }
        String requestedInvocationId = request.invocationId();
        String invocationId =
                StringUtils.isBlank(requestedInvocationId) ? "inv-" + UUID.randomUUID() : requestedInvocationId;
        PersistedInvocationRouting requestedRouting;
        int maxAttempts;
        long retryDelayMs;
        String paramsJson;
        String requestedRoutingJson;
        try {
            requestedRouting = requestedRouting(processCode, request.routing());
            maxAttempts = effectiveMaxAttempts(request.maxAttempts());
            retryDelayMs = effectiveRetryDelayMs(request.retryDelayMs());
            paramsJson = payloadCodec.write(request.params());
            requestedRoutingJson = payloadCodec.write(requestedRouting.asMap());
        } catch (IllegalArgumentException failure) {
            throw new InvalidAsyncInvocationRequestException(failure.getMessage(), failure);
        }
        AsyncInvocationEntity existing = repository.findById(invocationId).orElse(null);
        if (existing != null) {
            return existingSubmission(existing, processCode, maxAttempts, retryDelayMs, paramsJson, requestedRoutingJson);
        }
        PersistedInvocationRouting routing =
                pinAliasRouting(processCode, request.routing(), invocationId, requestedRouting);
        String routingJson = payloadCodec.write(routing.asMap());

        long now = store.currentTimeMillis();
        AsyncInvocationEntity invocation = new AsyncInvocationEntity();
        invocation.setInvocationId(invocationId);
        invocation.setProcessCode(processCode);
        invocation.setStatus(STATUS_QUEUED);
        invocation.setAttempts(0);
        invocation.setTotalAttempts(0L);
        invocation.setRedriveCount(0);
        invocation.setMaxAttempts(maxAttempts);
        invocation.setRetryDelayMs(retryDelayMs);
        invocation.setAvailableAt(now);
        invocation.setParamsJson(paramsJson);
        invocation.setRoutingJson(routingJson);
        invocation.setCreatedAt(now);
        invocation.setUpdatedAt(now);
        try {
            store.insert(invocation);
        } catch (DataIntegrityViolationException failure) {
            AsyncInvocationEntity racedExisting = repository.findById(invocationId).orElse(null);
            if (racedExisting != null) {
                return existingSubmission(racedExisting, processCode, maxAttempts, retryDelayMs, paramsJson, routingJson);
            }
            throw failure;
        }
        AsyncInvocationResponse accepted = payloadCodec.toResponse(invocation);
        worker.dispatch(invocationId);
        return accepted;
    }

    public Optional<AsyncInvocationResponse> get(String invocationId) {
        return repository.findById(invocationId).map(payloadCodec::toResponse);
    }

    public Optional<AsyncInvocationAttemptListResponse> listAttempts(String invocationId, long afterSequence, int limit) {
        if (afterSequence < 0L) {
            throw new IllegalArgumentException("afterSequence must be greater than or equal to 0");
        }
        if (limit < 1 || limit > MAX_ATTEMPT_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_ATTEMPT_PAGE_SIZE);
        }
        return store
            .listAttempts(invocationId, afterSequence, limit)
            .map(page -> new AsyncInvocationAttemptListResponse(page
                        .data()
                        .stream()
                        .map(AsyncInvocationService::toAttemptResponse)
                        .toList(), page.hasMore(), page.nextAfterSequence()));
    }

    public AsyncInvocationListResponse list(String status, String processCode, int page, int pageSize) {
        String normalizedStatus = StringUtils.trimToNull(status);
        String normalizedProcessCode = StringUtils.trimToNull(processCode);
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, LIST_ORDER);
        Page<AsyncInvocationEntity> invocations = findInvocations(normalizedProcessCode, normalizedStatus, pageRequest);
        List<AsyncInvocationResponse> data = invocations
            .getContent()
            .stream()
            .map(payloadCodec::toResponse)
            .toList();
        return new AsyncInvocationListResponse(data, invocations.getTotalElements(), pageRequest.getPageNumber() + 1,
                pageRequest.getPageSize());
    }

    private Page<AsyncInvocationEntity> findInvocations(String processCode, String status, PageRequest pageRequest) {
        if (processCode != null && status != null) {
            return repository.findByProcessCodeAndStatus(processCode, status, pageRequest);
        }
        if (processCode != null) {
            return repository.findByProcessCode(processCode, pageRequest);
        }
        if (status != null) {
            return repository.findByStatus(status, pageRequest);
        }
        return repository.findAll(pageRequest);
    }

    public Optional<AsyncInvocationResponse> requeue(String invocationId) {
        AsyncInvocationEntity invocation = repository.findById(invocationId).orElse(null);
        if (invocation == null) {
            return Optional.empty();
        }
        if (!STATUS_DEAD_LETTER.equals(invocation.getStatus())) {
            throw new AsyncInvocationConflictException("Only dead-letter invocations can be requeued: " + invocationId);
        }
        int requeued = store.requeueDeadLetter(invocationId);
        if (requeued == 0) {
            throw new AsyncInvocationConflictException(
                    "Async invocation state changed before it could be requeued: " + invocationId);
        }
        Optional<AsyncInvocationResponse> accepted = repository.findById(invocationId).map(payloadCodec::toResponse);
        worker.dispatch(invocationId);
        return accepted;
    }

    public AsyncInvocationHealthResponse health() {
        long now = store.currentTimeMillis();
        long queuedCount = repository.countByStatus(STATUS_QUEUED);
        Page<AsyncInvocationEntity> oldestReadyQueue =
                repository.findByStatusAndAvailableAtLessThanEqual(STATUS_QUEUED, now,
                        PageRequest.of(0, 1, DISPATCH_ORDER));
        long readyQueuedCount = oldestReadyQueue.getTotalElements();
        long oldestReadyAgeMs =
                oldestReadyQueue.isEmpty()
                ? 0L
                : Math.max(0L, now - oldestReadyQueue.getContent().get(0).getAvailableAt());
        long deadLetterCount = repository.countByStatus(STATUS_DEAD_LETTER);
        long expiredRunningCount = repository.countByStatusAndLeaseUntilLessThan(STATUS_RUNNING, now);
        return new AsyncInvocationHealthResponse(deadLetterCount > 0 || expiredRunningCount > 0 ? "degraded" : "healthy",
                queuedCount, readyQueuedCount, oldestReadyAgeMs, Math.max(0L, queuedCount - readyQueuedCount),
                repository.countByStatus(STATUS_RUNNING), repository.countByStatus(STATUS_SUCCEEDED), deadLetterCount,
                expiredRunningCount, worker.localRunningCount(), worker.dispatchedCount(), worker.workerId(),
                worker.leaseDurationMs(), worker.concurrency(), Instant.ofEpochMilli(now).toString());
    }

    public AsyncInvocationDeadLetterRequeueResponse requeueDeadLetters(String processCode, int limit) {
        String normalizedProcessCode = StringUtils.trimToNull(processCode);
        if (limit < 1 || limit > MAX_REQUEUE_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_REQUEUE_LIMIT);
        }
        PageRequest pageRequest = PageRequest.of(0, limit, LIST_ORDER);
        Page<AsyncInvocationEntity> invocations = normalizedProcessCode == null
                ? repository.findByStatus(STATUS_DEAD_LETTER, pageRequest)
                : repository.findByProcessCodeAndStatus(normalizedProcessCode, STATUS_DEAD_LETTER, pageRequest);
        long now = store.currentTimeMillis();
        List<String> invocationIds = new ArrayList<>();
        for (AsyncInvocationEntity entity : invocations.getContent()) {
            if (store.requeueDeadLetter(entity.getInvocationId()) > 0) {
                invocationIds.add(entity.getInvocationId());
            }
        }
        invocationIds.forEach(worker::dispatch);
        return new AsyncInvocationDeadLetterRequeueResponse(invocationIds.size(), Instant.ofEpochMilli(now).toString(),
                normalizedProcessCode, limit, invocationIds, health());
    }

    AsyncInvocationWorker worker() {
        return worker;
    }

    private AsyncInvocationResponse existingSubmission(AsyncInvocationEntity existing, String processCode,
            int maxAttempts, long retryDelayMs, String paramsJson, String routingJson) {
        PersistedInvocationRouting existingRouting =
                payloadCodec.readRouting("routingJson", existing.getRoutingJson()).withoutExecutionPin();
        PersistedInvocationRouting requestedRouting =
                payloadCodec.readRouting("routingJson", routingJson).withoutExecutionPin();
        boolean sameRequest = Objects.equals(existing.getProcessCode(), processCode)
                && existing.getMaxAttempts() == maxAttempts && existing.getRetryDelayMs() == retryDelayMs
                && Objects.equals(payloadCodec.readMap("paramsJson", existing.getParamsJson()),
                        payloadCodec.readMap("paramsJson", paramsJson))
                && Objects.equals(existingRouting.asMap(), requestedRouting.asMap());
        if (!sameRequest) {
            throw new AsyncInvocationConflictException(
                    "invocationId is already bound to a different async request: " + existing.getInvocationId());
        }
        return payloadCodec.toResponse(existing);
    }

    private static PersistedInvocationRouting requestedRouting(String processCode, ExecutionRoutingRequest routing) {
        if (routing == null) {
            throw new IllegalArgumentException("routing with exactly one of version or alias is required");
        }
        routing.validatePersistedInvocation();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(PersistedInvocationRouting.NAMESPACE, ProcessRef.DEFAULT_NAMESPACE);
        if (routing.version() != null) {
            ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, processCode, routing.version());
            values.put(PersistedInvocationRouting.VERSION, routing.version());
        } else {
            ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, processCode, routing.alias());
            values.put(PersistedInvocationRouting.ALIAS, routing.alias());
        }
        return PersistedInvocationRouting.of(values);
    }

    private PersistedInvocationRouting pinAliasRouting(String processCode, ExecutionRoutingRequest routing,
            String invocationId, PersistedInvocationRouting requestedRouting) {
        if (routing.alias() == null) {
            return requestedRouting;
        }
        AliasRoutingOptions options = new AliasRoutingOptions(routing.routingKey() == null
                ? invocationId
                : routing.routingKey(), routing.attributes());
        PublishedProcessExecutionService.AliasPin pin =
                executionService.resolveAliasPin(processCode, ProcessRef.DEFAULT_NAMESPACE, routing.alias(), options);
        return requestedRouting.pin(pin);
    }

    private static int effectiveMaxAttempts(Integer value) {
        if (value == null) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        if (value <= 0 || value > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and " + MAX_ATTEMPTS);
        }
        return value;
    }

    private static long effectiveRetryDelayMs(Long value) {
        if (value == null) {
            return DEFAULT_RETRY_DELAY_MS;
        }
        if (value < 0L || value > MAX_RETRY_DELAY_MS) {
            throw new IllegalArgumentException("retryDelayMs must be between 0 and " + MAX_RETRY_DELAY_MS);
        }
        return value;
    }

    private static AsyncInvocationAttemptResponse toAttemptResponse(AsyncInvocationAttemptEntity attempt) {
        return new AsyncInvocationAttemptResponse(attempt.getAttemptId(), attempt.getInvocationId(),
                attempt.getSequence(), attempt.getRedriveCount(), attempt.getAttemptNumber(), attempt.getWorkerId(),
                AsyncInvocationAttemptOutcome.fromValue(attempt.getOutcome()),
                attempt.getDisposition() == null ? null : AsyncInvocationAttemptDisposition.fromValue(attempt.getDisposition()),
                instant(attempt.getStartedAt()), instant(attempt.getFinishedAt()), instant(attempt.getNextAttemptAt()),
                StringUtils.trimToNull(attempt.getTraceId()), StringUtils.trimToNull(attempt.getErrorCode()),
                StringUtils.trimToNull(attempt.getErrorMessage()), attempt.getDurationMs());
    }

    private static String instant(Long epochMs) {
        return epochMs == null ? null : Instant.ofEpochMilli(epochMs).toString();
    }

    static final class AsyncInvocationConflictException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        AsyncInvocationConflictException(String message) {
            super(message);
        }
    }
}
