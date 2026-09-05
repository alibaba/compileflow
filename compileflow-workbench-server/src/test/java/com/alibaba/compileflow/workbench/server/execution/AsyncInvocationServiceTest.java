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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstallationLease;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;

class AsyncInvocationServiceTest {
    private static final int TEST_DISPATCH_BATCH_SIZE = 50;
    private static final Duration TEST_LEASE_DURATION = Duration.ofSeconds(30);

    private static AsyncInvocationService service(AsyncInvocationRepository repository,
            PublishedProcessExecutionService executionService) {
        return service(repository, executionService, readyRuntimeInstaller());
    }

    private static AsyncInvocationService service(AsyncInvocationRepository repository,
            PublishedProcessExecutionService executionService, RuntimeInstaller runtimeInstaller) {
        stubAliasResolution(executionService);
        return new AsyncInvocationService(repository, stateMachine(repository), executionService, runtimeInstaller,
                new ObjectMapper(), Runnable::run, TEST_DISPATCH_BATCH_SIZE, TEST_LEASE_DURATION);
    }

    private static AsyncInvocationService serviceWithoutDispatch(AsyncInvocationRepository repository,
            PublishedProcessExecutionService executionService) {
        stubAliasResolution(executionService);
        return new AsyncInvocationService(repository, stateMachine(repository), executionService,
                readyRuntimeInstaller(), new ObjectMapper(), runnable -> {}, TEST_DISPATCH_BATCH_SIZE,
                TEST_LEASE_DURATION);
    }

    private static RuntimeInstaller readyRuntimeInstaller() {
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        when(installer.acquireInstallation(any(ProcessRef.Version.class)))
            .thenAnswer(invocation -> CompletableFuture.completedFuture(mock(RuntimeInstallationLease.class)));
        return installer;
    }

    private static void stubAliasResolution(PublishedProcessExecutionService executionService) {
        lenient()
            .when(executionService.resolveAliasPin(anyString(), anyString(), anyString(), any(AliasRoutingOptions.class)))
            .thenAnswer(invocation -> aliasPin(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), "v1", 1L));
    }

    private static PublishedProcessExecutionService.AliasPin aliasPin(String code, String namespace, String alias,
            String version, long routeRevision) {
        return aliasPin(code, namespace, alias, version, routeRevision, ProcessAliasTarget.STABLE);
    }

    private static PublishedProcessExecutionService.AliasPin aliasPin(String code, String namespace, String alias,
            String version, long routeRevision, ProcessAliasTarget target) {
        return new PublishedProcessExecutionService.AliasPin(ProcessRef.alias(namespace, code, alias), version,
                routeRevision, target);
    }

    private static PublishedProcessExecutionService.AliasPin pinFor(String code) {
        return argThat(pin -> code.equals(pin.alias().code()));
    }

    private static AsyncInvocationSubmitRequest request(String invocationId, int maxAttempts, long retryDelayMs) {
        return new AsyncInvocationSubmitRequest(invocationId, Map.of("amount", 100), aliasRouting("production", null),
                maxAttempts, retryDelayMs);
    }

    private static ExecutionRoutingRequest aliasRouting(String alias, String routingKey) {
        return new ExecutionRoutingRequest(null, alias, routingKey, Map.of());
    }

    private static ProcessExecutionResponse response(boolean success, String traceId, String effectiveVersion) {
        return success
                ? new ProcessExecutionResponse(true, "Process executed successfully", traceId, "inv-test",
                        "order.process", 12L,
                        new ExecutionRoutingResponse("default", null, null, effectiveVersion, null, null, null),
                        Map.of(), null, null)
                : failureResponse(traceId, effectiveVersion, "Process execution failed");
    }

    private static ProcessExecutionResponse failureResponse(String traceId, String effectiveVersion, String error) {
        return new ProcessExecutionResponse(false, "Process execution failed", traceId, "inv-test", "order.process", 12L,
                new ExecutionRoutingResponse("default", null, null, effectiveVersion, null, null, null), null,
                "CF_EXEC_004", error);
    }

    private static AsyncInvocationEntity deadLetter(String invocationId, String processCode) {
        AsyncInvocationEntity entity = new AsyncInvocationEntity();
        entity.setInvocationId(invocationId);
        entity.setProcessCode(processCode);
        entity.setStatus(AsyncInvocationService.STATUS_DEAD_LETTER);
        entity.setAttempts(3);
        entity.setTotalAttempts(3L);
        entity.setRedriveCount(0);
        entity.setMaxAttempts(3);
        entity.setRetryDelayMs(0L);
        entity.setAvailableAt(System.currentTimeMillis());
        entity.setParamsJson("{}");
        entity.setRoutingJson("{\"namespace\":\"default\",\"alias\":\"production\"}");
        entity.setErrorCode("CF_EXEC_004");
        entity.setErrorMessage("boom");
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());
        return entity;
    }

    private static AsyncInvocationEntity queued(String invocationId, String processCode) {
        AsyncInvocationEntity entity = new AsyncInvocationEntity();
        entity.setInvocationId(invocationId);
        entity.setProcessCode(processCode);
        entity.setStatus(AsyncInvocationService.STATUS_QUEUED);
        entity.setAttempts(0);
        entity.setTotalAttempts(0L);
        entity.setRedriveCount(0);
        entity.setMaxAttempts(3);
        entity.setRetryDelayMs(0L);
        entity.setAvailableAt(System.currentTimeMillis());
        entity.setParamsJson("{}");
        entity.setRoutingJson("{\"namespace\":\"default\",\"alias\":\"production\"}");
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());
        return entity;
    }

    private static AsyncInvocationEntity succeeded(String invocationId, String processCode) {
        AsyncInvocationEntity entity = new AsyncInvocationEntity();
        entity.setInvocationId(invocationId);
        entity.setProcessCode(processCode);
        entity.setStatus(AsyncInvocationService.STATUS_SUCCEEDED);
        entity.setAttempts(1);
        entity.setTotalAttempts(1L);
        entity.setRedriveCount(0);
        entity.setMaxAttempts(3);
        entity.setRetryDelayMs(0L);
        entity.setAvailableAt(System.currentTimeMillis());
        entity.setParamsJson("{}");
        entity.setRoutingJson("{}");
        entity.setResultJson("{\"success\":true}");
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());
        entity.setCompletedAt(System.currentTimeMillis());
        return entity;
    }

    private static AsyncInvocationEntity running(String invocationId, String processCode, int attempts) {
        AsyncInvocationEntity entity = new AsyncInvocationEntity();
        entity.setInvocationId(invocationId);
        entity.setProcessCode(processCode);
        entity.setStatus(AsyncInvocationService.STATUS_RUNNING);
        entity.setAttempts(attempts);
        entity.setTotalAttempts(attempts);
        entity.setRedriveCount(0);
        entity.setMaxAttempts(3);
        entity.setRetryDelayMs(0L);
        entity.setAvailableAt(System.currentTimeMillis());
        entity.setParamsJson("{}");
        entity.setRoutingJson("{}");
        entity.setErrorCode("LEASE_EXPIRED");
        entity.setErrorMessage("previous attempt stalled");
        entity.setLeaseToken("worker-dead");
        entity.setLeaseUntil(System.currentTimeMillis() - 1000L);
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis() - 1000L);
        entity.setStartedAt(System.currentTimeMillis() - 2000L);
        return entity;
    }

    private static AsyncInvocationStateMachine stateMachine(AsyncInvocationRepository repository) {
        AsyncInvocationAttemptRepository attempts = mock(AsyncInvocationAttemptRepository.class);
        when(attempts.saveAndFlush(any(AsyncInvocationAttemptEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(attempts.finishOwned(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any(),
                anyLong(), any()))
            .thenReturn(1);
        when(attempts.finishExpired(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyLong(), anyLong(), any()))
            .thenReturn(1);
        return new AsyncInvocationStateMachine(repository, attempts);
    }

    private static AsyncInvocationRepository repository() {
        AsyncInvocationRepository repository = mock(AsyncInvocationRepository.class);
        Map<String, AsyncInvocationEntity> store = new ConcurrentHashMap<>();
        when(repository.currentTimeMillis()).thenAnswer(inv -> System.currentTimeMillis());
        when(repository.findById(any(String.class))).thenAnswer(inv -> {
            String invocationId = inv.getArgument(0);
            AsyncInvocationEntity entity = store.get(invocationId);
            return entity == null ? Optional.empty() : Optional.of(copy(entity));
        });
        when(repository.save(any(AsyncInvocationEntity.class))).thenAnswer(inv -> {
            AsyncInvocationEntity entity = copy(inv.getArgument(0));
            store.put(entity.getInvocationId(), entity);
            return copy(entity);
        });
        when(repository.claimQueued(any(String.class), any(String.class), any(String.class), anyLong(),
                any(String.class), anyLong()))
            .thenAnswer(inv -> {
                String invocationId = inv.getArgument(0);
                String queued = inv.getArgument(1);
                String running = inv.getArgument(2);
                Long now = inv.getArgument(3);
                String leaseToken = inv.getArgument(4);
                Long leaseUntil = inv.getArgument(5);
                AsyncInvocationEntity entity = store.get(invocationId);
                if (entity == null || !queued.equals(entity.getStatus()) || entity.getAvailableAt() > now) {
                    return 0;
                }
                entity.setStatus(running);
                entity.setAttempts(entity.getAttempts() + 1);
                entity.setTotalAttempts(entity.getTotalAttempts() + 1L);
                entity.setStartedAt(now);
                entity.setUpdatedAt(now);
                entity.setLeaseToken(leaseToken);
                entity.setLeaseUntil(leaseUntil);
                return 1;
            });
        when(repository.extendLease(any(String.class), any(String.class), any(String.class), anyLong(), anyLong()))
            .thenAnswer(inv -> {
                String invocationId = inv.getArgument(0);
                String running = inv.getArgument(1);
                String leaseToken = inv.getArgument(2);
                Long leaseUntil = inv.getArgument(3);
                Long now = inv.getArgument(4);
                AsyncInvocationEntity entity = store.get(invocationId);
                if (entity == null || !running.equals(entity.getStatus()) || !leaseToken.equals(entity.getLeaseToken())
                        || entity.getLeaseUntil() == null || entity.getLeaseUntil() < now) {
                    return 0;
                }
                entity.setLeaseUntil(leaseUntil);
                entity.setUpdatedAt(now);
                return 1;
            });
        when(repository.pinOwnedRouting(any(String.class), any(String.class), any(String.class), any(String.class),
                anyLong()))
            .thenAnswer(inv -> {
                AsyncInvocationEntity entity = owned(store, inv, 0, 1, 2, 4);
                if (entity == null) {
                    return 0;
                }
                entity.setRoutingJson(inv.getArgument(3));
                entity.setUpdatedAt(inv.getArgument(4));
                return 1;
            });
        when(repository.completeOwned(any(String.class), any(String.class), any(String.class), any(String.class),
                any(String.class), any(String.class), any(), any(), anyLong()))
            .thenAnswer(inv -> {
                AsyncInvocationEntity entity = owned(store, inv, 0, 1, 3, 8);
                if (entity == null) {
                    return 0;
                }
                long now = inv.getArgument(8);
                entity.setStatus(inv.getArgument(2));
                entity.setRoutingJson(inv.getArgument(4));
                entity.setResultJson(inv.getArgument(5));
                entity.setErrorCode(null);
                entity.setErrorMessage(null);
                entity.setTraceId(inv.getArgument(6));
                entity.setDurationMs(inv.getArgument(7));
                finishOwnership(entity, now, now);
                return 1;
            });
        when(repository.retryOwned(any(String.class), any(String.class), any(String.class), any(String.class),
                any(String.class), any(), any(String.class), any(String.class), any(), any(), anyLong(), anyLong()))
            .thenAnswer(inv -> {
                AsyncInvocationEntity entity = owned(store, inv, 0, 1, 3, 11);
                if (entity == null || entity.getAttempts() >= entity.getMaxAttempts()) {
                    return 0;
                }
                long now = inv.getArgument(11);
                entity.setStatus(inv.getArgument(2));
                entity.setRoutingJson(inv.getArgument(4));
                entity.setResultJson(inv.getArgument(5));
                entity.setErrorCode(inv.getArgument(6));
                entity.setErrorMessage(inv.getArgument(7));
                entity.setTraceId(inv.getArgument(8));
                entity.setDurationMs(inv.getArgument(9));
                finishOwnership(entity, inv.getArgument(10), now);
                entity.setCompletedAt(null);
                return 1;
            });
        when(repository.deadLetterOwned(any(String.class), any(String.class), any(String.class), any(String.class),
                any(String.class), any(), any(String.class), any(String.class), any(), any(), anyLong()))
            .thenAnswer(inv -> {
                AsyncInvocationEntity entity = owned(store, inv, 0, 1, 3, 10);
                if (entity == null) {
                    return 0;
                }
                long now = inv.getArgument(10);
                entity.setStatus(inv.getArgument(2));
                entity.setRoutingJson(inv.getArgument(4));
                entity.setResultJson(inv.getArgument(5));
                entity.setErrorCode(inv.getArgument(6));
                entity.setErrorMessage(inv.getArgument(7));
                entity.setTraceId(inv.getArgument(8));
                entity.setDurationMs(inv.getArgument(9));
                finishOwnership(entity, now, now);
                return 1;
            });
        when(repository.recoverExpiredForRetry(any(String.class), any(String.class), any(String.class),
                any(String.class), any(String.class), any(String.class), anyLong(), anyLong()))
            .thenAnswer(inv -> {
                AsyncInvocationEntity entity = expired(store, inv, 0, 1, 7);
                if (entity == null || entity.getAttempts() >= entity.getMaxAttempts()) {
                    return 0;
                }
                if (!inv.getArgument(3).equals(entity.getLeaseToken())) {
                    return 0;
                }
                entity.setStatus(inv.getArgument(2));
                entity.setErrorCode(inv.getArgument(4));
                entity.setErrorMessage(inv.getArgument(5));
                finishOwnership(entity, inv.getArgument(6), inv.getArgument(7));
                entity.setCompletedAt(null);
                return 1;
            });
        when(repository.recoverExpiredToDeadLetter(any(String.class), any(String.class), any(String.class),
                any(String.class), any(String.class), any(String.class), anyLong()))
            .thenAnswer(inv -> {
                AsyncInvocationEntity entity = expired(store, inv, 0, 1, 6);
                if (entity == null || entity.getAttempts() < entity.getMaxAttempts()) {
                    return 0;
                }
                if (!inv.getArgument(3).equals(entity.getLeaseToken())) {
                    return 0;
                }
                long now = inv.getArgument(6);
                entity.setStatus(inv.getArgument(2));
                entity.setErrorCode(inv.getArgument(4));
                entity.setErrorMessage(inv.getArgument(5));
                finishOwnership(entity, now, now);
                return 1;
            });
        when(repository.requeueDeadLetter(any(String.class), any(String.class), any(String.class), anyLong()))
            .thenAnswer(inv -> {
                AsyncInvocationEntity entity = store.get(inv.getArgument(0));
                if (entity == null || !inv.getArgument(1).equals(entity.getStatus())) {
                    return 0;
                }
                long now = inv.getArgument(3);
                entity.setStatus(inv.getArgument(2));
                entity.setAttempts(0);
                entity.setRedriveCount(entity.getRedriveCount() + 1);
                entity.setErrorCode(null);
                entity.setErrorMessage(null);
                entity.setResultJson(null);
                entity.setTraceId(null);
                entity.setDurationMs(null);
                entity.setStartedAt(null);
                entity.setCompletedAt(null);
                finishOwnership(entity, now, now);
                entity.setCompletedAt(null);
                return 1;
            });
        when(repository.findByStatusAndAvailableAtLessThanEqual(any(String.class), anyLong(), any(PageRequest.class)))
            .thenAnswer(inv -> {
                String status = inv.getArgument(0);
                long availableAt = inv.getArgument(1);
                PageRequest pageRequest = inv.getArgument(2);
                List<AsyncInvocationEntity> matches = new ArrayList<>();
                for (AsyncInvocationEntity entity : store.values()) {
                    if (status.equals(entity.getStatus()) && entity.getAvailableAt() <= availableAt) {
                        matches.add(copy(entity));
                    }
                }
                return new PageImpl<AsyncInvocationEntity>(matches, pageRequest, matches.size());
            });
        when(repository.findByStatus(any(String.class), any(PageRequest.class))).thenAnswer(inv -> {
            String status = inv.getArgument(0);
            PageRequest pageRequest = inv.getArgument(1);
            List<AsyncInvocationEntity> matches = new ArrayList<>();
            for (AsyncInvocationEntity entity : store.values()) {
                if (status.equals(entity.getStatus())) {
                    matches.add(copy(entity));
                }
            }
            return new PageImpl<AsyncInvocationEntity>(matches, pageRequest, matches.size());
        });
        when(repository.findAll(any(PageRequest.class))).thenAnswer(inv -> {
            PageRequest pageRequest = inv.getArgument(0);
            List<AsyncInvocationEntity> matches = copies(store.values());
            return new PageImpl<AsyncInvocationEntity>(matches, pageRequest, matches.size());
        });
        when(repository.findByProcessCode(any(String.class), any(PageRequest.class))).thenAnswer(inv -> {
            String processCode = inv.getArgument(0);
            PageRequest pageRequest = inv.getArgument(1);
            List<AsyncInvocationEntity> matches = new ArrayList<>();
            for (AsyncInvocationEntity entity : store.values()) {
                if (processCode.equals(entity.getProcessCode())) {
                    matches.add(copy(entity));
                }
            }
            return new PageImpl<AsyncInvocationEntity>(matches, pageRequest, matches.size());
        });
        when(repository.findByProcessCodeAndStatus(any(String.class), any(String.class), any(PageRequest.class)))
            .thenAnswer(inv -> {
                String processCode = inv.getArgument(0);
                String status = inv.getArgument(1);
                PageRequest pageRequest = inv.getArgument(2);
                List<AsyncInvocationEntity> matches = new ArrayList<>();
                for (AsyncInvocationEntity entity : store.values()) {
                    if (processCode.equals(entity.getProcessCode()) && status.equals(entity.getStatus())) {
                        matches.add(copy(entity));
                    }
                }
                return new PageImpl<AsyncInvocationEntity>(matches, pageRequest, matches.size());
            });
        when(repository.findByStatusAndLeaseUntilLessThan(any(String.class), anyLong(), any(PageRequest.class)))
            .thenAnswer(inv -> {
                String status = inv.getArgument(0);
                Long leaseUntil = inv.getArgument(1);
                PageRequest pageRequest = inv.getArgument(2);
                List<AsyncInvocationEntity> matches = new ArrayList<>();
                for (AsyncInvocationEntity entity : store.values()) {
                    if (status.equals(entity.getStatus()) && entity.getLeaseUntil() != null
                            && entity.getLeaseUntil() < leaseUntil) {
                        matches.add(copy(entity));
                    }
                }
                return new PageImpl<AsyncInvocationEntity>(matches, pageRequest, matches.size());
            });
        when(repository.countByStatus(any(String.class))).thenAnswer(inv -> {
            String status = inv.getArgument(0);
            long count = 0;
            for (AsyncInvocationEntity entity : store.values()) {
                if (status.equals(entity.getStatus())) {
                    count++;
                }
            }
            return count;
        });
        when(repository.countByStatusAndLeaseUntilLessThan(any(String.class), anyLong())).thenAnswer(inv -> {
            String status = inv.getArgument(0);
            Long leaseUntil = inv.getArgument(1);
            long count = 0;
            for (AsyncInvocationEntity entity : store.values()) {
                if (status.equals(entity.getStatus()) && entity.getLeaseUntil() != null
                        && entity.getLeaseUntil() < leaseUntil) {
                    count++;
                }
            }
            return count;
        });
        return repository;
    }

    private static AsyncInvocationEntity owned(Map<String, AsyncInvocationEntity> store,
            org.mockito.invocation.InvocationOnMock invocation, int invocationIdIndex, int statusIndex,
            int leaseTokenIndex, int nowIndex) {
        AsyncInvocationEntity entity = store.get(invocation.getArgument(invocationIdIndex));
        long now = invocation.getArgument(nowIndex);
        if (entity == null || !invocation.getArgument(statusIndex).equals(entity.getStatus())
                || !invocation.getArgument(leaseTokenIndex).equals(entity.getLeaseToken())
                || entity.getLeaseUntil() == null || entity.getLeaseUntil() < now) {
            return null;
        }
        return entity;
    }

    private static AsyncInvocationEntity expired(Map<String, AsyncInvocationEntity> store,
            org.mockito.invocation.InvocationOnMock invocation, int invocationIdIndex, int statusIndex, int nowIndex) {
        AsyncInvocationEntity entity = store.get(invocation.getArgument(invocationIdIndex));
        long now = invocation.getArgument(nowIndex);
        if (entity == null || !invocation.getArgument(statusIndex).equals(entity.getStatus())
                || entity.getLeaseUntil() == null || entity.getLeaseUntil() >= now) {
            return null;
        }
        return entity;
    }

    private static void finishOwnership(AsyncInvocationEntity entity, long availableAt, long now) {
        entity.setLeaseToken(null);
        entity.setLeaseUntil(null);
        entity.setAvailableAt(availableAt);
        entity.setUpdatedAt(now);
        entity.setCompletedAt(now);
    }

    private static List<AsyncInvocationEntity> copies(Iterable<AsyncInvocationEntity> entities) {
        List<AsyncInvocationEntity> result = new ArrayList<>();
        for (AsyncInvocationEntity entity : entities) {
            result.add(copy(entity));
        }
        return result;
    }

    private static AsyncInvocationEntity copy(AsyncInvocationEntity source) {
        AsyncInvocationEntity target = new AsyncInvocationEntity();
        target.setInvocationId(source.getInvocationId());
        target.setProcessCode(source.getProcessCode());
        target.setStatus(source.getStatus());
        target.setAttempts(source.getAttempts());
        target.setTotalAttempts(source.getTotalAttempts());
        target.setRedriveCount(source.getRedriveCount());
        target.setMaxAttempts(source.getMaxAttempts());
        target.setRetryDelayMs(source.getRetryDelayMs());
        target.setAvailableAt(source.getAvailableAt());
        target.setParamsJson(source.getParamsJson());
        target.setRoutingJson(source.getRoutingJson());
        target.setResultJson(source.getResultJson());
        target.setErrorCode(source.getErrorCode());
        target.setErrorMessage(source.getErrorMessage());
        target.setTraceId(source.getTraceId());
        target.setLeaseToken(source.getLeaseToken());
        target.setLeaseUntil(source.getLeaseUntil());
        target.setDurationMs(source.getDurationMs());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setStartedAt(source.getStartedAt());
        target.setCompletedAt(source.getCompletedAt());
        return target;
    }

    @Test
    void constructorRejectsInvalidWorkerConfigurationInsteadOfFallingBack() {
        CompileFlowWorkbenchServerProperties properties = mock(CompileFlowWorkbenchServerProperties.class);
        CompileFlowWorkbenchServerProperties.AsyncInvocation asyncInvocation =
                mock(CompileFlowWorkbenchServerProperties.AsyncInvocation.class);
        when(properties.getAsyncInvocation()).thenReturn(asyncInvocation);
        when(asyncInvocation.getConcurrency()).thenReturn(0);
        when(asyncInvocation.getQueueCapacity()).thenReturn(1);

        assertThatThrownBy(() -> new AsyncInvocationService(repository(), mock(AsyncInvocationStateMachine.class),
                mock(PublishedProcessExecutionService.class), readyRuntimeInstaller(), properties,
                new LifecycleProperties(), new ObjectMapper()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("concurrency must be greater than 0");
    }

    @Test
    void submitExecutesSuccessfullyAndPreservesInvocationId() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessExecutionResponse engineResponse = response(true, "trace-1", "v1");
        when(executionService.execute(pinFor("payment.approve"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(engineResponse);
        AsyncInvocationService service = service(repository, executionService);
        AsyncInvocationSubmitRequest request = request("inv-client-1", 3, 0L);

        AsyncInvocationResponse result = service.submit("payment.approve", request);
        AsyncInvocationResponse completed = service.get("inv-client-1").orElseThrow();

        assertThat(result.invocationId()).isEqualTo("inv-client-1");
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.QUEUED);
        assertThat(result.currentAttemptCount()).isZero();
        assertThat(completed.status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
        assertThat(completed.currentAttemptCount()).isEqualTo(1);
        assertThat(completed.traceId()).isEqualTo("trace-1");
        ArgumentCaptor<ProcessExecutionOptions> options = ArgumentCaptor.forClass(ProcessExecutionOptions.class);
        ArgumentCaptor<PublishedProcessExecutionService.AliasPin> pin =
                ArgumentCaptor.forClass(PublishedProcessExecutionService.AliasPin.class);
        verify(executionService).execute(pin.capture(), anyMap(), options.capture());
        assertThat(options.getValue().getInvocationId()).isEqualTo("inv-client-1");
        assertThat(pin.getValue().alias().code()).isEqualTo("payment.approve");
        assertThat(pin.getValue().version()).isEqualTo("v1");
        assertThat(pin.getValue().routeRevision()).isEqualTo(1L);
    }

    @Test
    void executionMaterializesThePinnedVersionAndReleasesItAfterward() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        when(executionService.execute(pinFor("payment.materialize"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(response(true, "trace-materialized", "v1"));
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        RuntimeInstallationLease installation = mock(RuntimeInstallationLease.class);
        when(installer.acquireInstallation(any(ProcessRef.Version.class)))
            .thenReturn(CompletableFuture.completedFuture(installation));
        AsyncInvocationService service = service(repository, executionService, installer);

        service.submit("payment.materialize", request("inv-materialized", 1, 0L));

        ArgumentCaptor<ProcessRef.Version> version = ArgumentCaptor.forClass(ProcessRef.Version.class);
        verify(installer).acquireInstallation(version.capture());
        assertThat(version.getValue()).isEqualTo(ProcessRef.version("default", "payment.materialize", "v1"));
        verify(installation).close();
        assertThat(service.get("inv-materialized").orElseThrow().status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
    }

    @Test
    void exactVersionExecutionReusesTheWorkerInstallationLease() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        RuntimeInstallationLease installation = mock(RuntimeInstallationLease.class);
        ProcessRef.Version ref = ProcessRef.version("default", "payment.exact", "v1");
        when(installer.acquireInstallation(ref)).thenReturn(CompletableFuture.completedFuture(installation));
        when(executionService.executeInstalled(eq(ref), eq(installation), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(response(true, "trace-exact", "v1"));
        AsyncInvocationService service = service(repository, executionService, installer);
        AsyncInvocationSubmitRequest request = new AsyncInvocationSubmitRequest("inv-exact", Map.of("amount", 100),
                new ExecutionRoutingRequest("v1", null, null, Map.of()), 1, 0L);

        service.submit("payment.exact", request);

        verify(installer, times(1)).acquireInstallation(ref);
        verify(executionService).executeInstalled(eq(ref), eq(installation), anyMap(),
                any(ProcessExecutionOptions.class));
        verify(executionService, never()).execute(any(ProcessRef.class), anyMap(), any(ProcessExecutionOptions.class));
        verify(installation).close();
        assertThat(service.get("inv-exact").orElseThrow().status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
    }

    @Test
    void runtimeLoadFailureIsRetryableAndDoesNotPersistInfrastructureDetails() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        when(installer.acquireInstallation(any(ProcessRef.Version.class)))
            .thenReturn(CompletableFuture.failedFuture(
                    new IllegalStateException("artifact credential must stay private")));
        AsyncInvocationService service = service(repository, executionService, installer);

        service.submit("payment.unavailable", request("inv-unavailable", 1, 0L));

        AsyncInvocationResponse failed = service.get("inv-unavailable").orElseThrow();
        assertThat(failed.status()).isEqualTo(AsyncInvocationStatus.DEAD_LETTER);
        assertThat(failed.error()).isEqualTo("Async invocation runtime could not be loaded");
        verify(executionService, never())
            .execute(any(PublishedProcessExecutionService.AliasPin.class), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void lostDatabaseLeaseAfterRuntimeLoadingPreventsProcessStart() {
        AsyncInvocationRepository repository = repository();
        when(repository.extendLease(anyString(), anyString(), anyString(), anyLong(), anyLong())).thenReturn(0);
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        RuntimeInstallationLease installation = mock(RuntimeInstallationLease.class);
        when(installer.acquireInstallation(any(ProcessRef.Version.class)))
            .thenReturn(CompletableFuture.completedFuture(installation));
        AsyncInvocationService service = service(repository, executionService, installer);

        service.submit("payment.fenced", request("inv-fenced", 2, 0L));

        assertThat(service.get("inv-fenced").orElseThrow().status()).isEqualTo(AsyncInvocationStatus.RUNNING);
        verify(executionService, never())
            .execute(any(PublishedProcessExecutionService.AliasPin.class), anyMap(), any(ProcessExecutionOptions.class));
        verify(installation).close();
    }

    @Test
    void submitRetriesUnknownFailuresWithoutPersistingTheirMessages() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        String sensitiveMessage = "credential=do-not-persist";
        when(executionService.execute(pinFor("payment.fail"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenThrow(new IllegalStateException(sensitiveMessage));
        AsyncInvocationService service = service(repository, executionService);

        AsyncInvocationResponse accepted = service.submit("payment.fail", request("inv-fail-1", 2, 0L));
        AsyncInvocationResponse retry = service.get("inv-fail-1").orElseThrow();

        service.worker().dispatchQueuedInvocations();

        AsyncInvocationResponse result = service.get("inv-fail-1").orElseThrow();

        assertThat(accepted.status()).isEqualTo(AsyncInvocationStatus.QUEUED);
        assertThat(retry.status()).isEqualTo(AsyncInvocationStatus.QUEUED);
        assertThat(retry.currentAttemptCount()).isEqualTo(1);
        assertThat(retry.routing().effectiveVersion()).isEqualTo("v1");
        assertThat(retry.routing().alias()).isEqualTo("production");
        assertThat(retry.routing().routeRevision()).isEqualTo(1L);
        assertThat(retry.routing().target()).isEqualTo(ProcessAliasTarget.STABLE);
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.DEAD_LETTER);
        assertThat(result.currentAttemptCount()).isEqualTo(2);
        assertThat(result.error()).isEqualTo("Async invocation failed unexpectedly");
        assertThat(retry.toString()).doesNotContain(sensitiveMessage);
        assertThat(result.toString()).doesNotContain(sensitiveMessage);
        assertThat(service.get("inv-fail-1").isPresent()).isTrue();
    }

    @Test
    void submitDoesNotRetryUnlessCallerOptsIn() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        when(executionService.execute(pinFor("payment.once"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenThrow(new IllegalStateException("uncertain outcome"));
        AsyncInvocationService service = service(repository, executionService);
        AsyncInvocationSubmitRequest request = new AsyncInvocationSubmitRequest("inv-once-1", Collections.emptyMap(),
                aliasRouting("production", null), null, null);

        AsyncInvocationResponse accepted = service.submit("payment.once", request);
        service.worker().dispatchQueuedInvocations();
        AsyncInvocationResponse result = service.get("inv-once-1").orElseThrow();

        assertThat(accepted.maxAttempts()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.DEAD_LETTER);
        assertThat(result.currentAttemptCount()).isEqualTo(1);
        verify(executionService).execute(pinFor("payment.once"), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void asyncRepresentationPromotesStructuredFailureCode() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessExecutionResponse engineResponse = response(false, "trace-failure", "v2");
        when(executionService.execute(pinFor("payment.structured"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(engineResponse);
        AsyncInvocationService service = service(repository, executionService);

        service.submit("payment.structured", request("inv-structured-1", 1, 0L));
        AsyncInvocationResponse result = service.get("inv-structured-1").orElseThrow();

        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.DEAD_LETTER);
        assertThat(result.errorCode()).isEqualTo("CF_EXEC_004");
        assertThat(result.error()).isEqualTo("Process execution failed");
    }

    @Test
    void retryDelayIsDurableAndDoesNotSleepOrImmediatelyRedispatch() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        when(executionService.execute(pinFor("payment.delayed"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenThrow(new IllegalStateException("retry later"));
        AsyncInvocationService service = service(repository, executionService);
        long beforeSubmit = System.currentTimeMillis();

        service.submit("payment.delayed", request("inv-delayed-1", 2, 60000L));
        service.worker().dispatchQueuedInvocations();

        AsyncInvocationResponse retry = service.get("inv-delayed-1").orElseThrow();
        assertThat(retry.status()).isEqualTo(AsyncInvocationStatus.QUEUED);
        assertThat(retry.currentAttemptCount()).isEqualTo(1);
        assertThat(Instant.parse(retry.nextAttemptAt()).toEpochMilli()).isGreaterThanOrEqualTo(beforeSubmit + 60000L);
        verify(executionService).execute(pinFor("payment.delayed"), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void submitRejectsUnboundedRetryPolicy() {
        AsyncInvocationService service =
                serviceWithoutDispatch(repository(), mock(PublishedProcessExecutionService.class));
        AsyncInvocationSubmitRequest tooManyAttempts = request("inv-too-many", 101, 0L);
        AsyncInvocationSubmitRequest excessiveDelay = request("inv-too-late", 3, TimeUnit.DAYS.toMillis(7) + 1L);

        assertThatThrownBy(() -> service.submit("payment.invalid", tooManyAttempts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("maxAttempts must be between 1 and 100");
        assertThatThrownBy(() -> service.submit("payment.invalid", excessiveDelay))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("retryDelayMs must be between 0 and 604800000");
    }

    @Test
    void submitUsesRoutingInputsForAdmissionWithoutPersistingRawValues() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        when(executionService.resolveAliasPin(eq("payment.sensitive"), eq(ProcessRef.DEFAULT_NAMESPACE),
                eq("production"), any(AliasRoutingOptions.class)))
            .thenReturn(aliasPin("payment.sensitive", ProcessRef.DEFAULT_NAMESPACE, "production", "v2", 9L));
        AsyncInvocationService service = serviceWithoutDispatch(repository, executionService);
        AsyncInvocationSubmitRequest request = new AsyncInvocationSubmitRequest("inv-sensitive-route",
                Map.of("amount", 100),
                new ExecutionRoutingRequest(null, "production", "sensitive-user-key", Map.of("region", "private-region")),
                1, 0L);

        service.submit("payment.sensitive", request);

        ArgumentCaptor<AliasRoutingOptions> routing = ArgumentCaptor.forClass(AliasRoutingOptions.class);
        verify(executionService)
            .resolveAliasPin(eq("payment.sensitive"), eq(ProcessRef.DEFAULT_NAMESPACE), eq("production"),
                    routing.capture());
        assertThat(routing.getValue().routingKey()).isEqualTo("sensitive-user-key");
        assertThat(routing.getValue().attributes()).containsExactlyEntriesOf(Map.of("region", "private-region"));
        assertThat(repository.findById("inv-sensitive-route").orElseThrow().getRoutingJson())
            .doesNotContain("sensitive-user-key", "private-region", "attributes", "routingKey")
            .contains("effectiveVersion", "routeRevision", "target");
    }

    @Test
    void submitUsesThePersistedInvocationIdAsTheDefaultCohortKey() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        when(executionService.resolveAliasPin(eq("payment.default-cohort"), eq(ProcessRef.DEFAULT_NAMESPACE),
                eq("production"), any(AliasRoutingOptions.class)))
            .thenReturn(aliasPin("payment.default-cohort", ProcessRef.DEFAULT_NAMESPACE, "production", "v2", 10L));
        AsyncInvocationService service = serviceWithoutDispatch(repository, executionService);
        AsyncInvocationSubmitRequest request = new AsyncInvocationSubmitRequest("inv-default-cohort", Map.of(),
                new ExecutionRoutingRequest(null, "production", null, Map.of("tier", "private")), 1, 0L);

        service.submit("payment.default-cohort", request);

        ArgumentCaptor<AliasRoutingOptions> routing = ArgumentCaptor.forClass(AliasRoutingOptions.class);
        verify(executionService)
            .resolveAliasPin(eq("payment.default-cohort"), eq(ProcessRef.DEFAULT_NAMESPACE), eq("production"),
                    routing.capture());
        assertThat(routing.getValue().routingKey()).isEqualTo("inv-default-cohort");
        assertThat(routing.getValue().attributes()).containsExactlyEntriesOf(Map.of("tier", "private"));
        assertThat(repository.findById("inv-default-cohort").orElseThrow().getRoutingJson())
            .doesNotContain("private", "attributes", "routingKey")
            .contains("effectiveVersion", "routeRevision", "target");
    }

    @Test
    void submitRejectsAliasRoutingOptionsForExactVersionBeforePersisting() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));
        AsyncInvocationSubmitRequest request = new AsyncInvocationSubmitRequest("inv-version-route",
                Map.of("amount", 100), new ExecutionRoutingRequest("v1", null, "user-42", Map.of()), 1, 0L);

        assertThatThrownBy(() -> service.submit("payment.versioned", request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("routingKey and attributes require an alias route");
        assertThat(repository.findById("inv-version-route")).isEmpty();
    }

    @Test
    void submitRejectsMissingRoutingBeforePersistingTheDurableRequest() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));
        AsyncInvocationSubmitRequest request =
                new AsyncInvocationSubmitRequest("inv-missing-route", null, null, null, null);

        assertThatThrownBy(() -> service.submit("payment.missing-route", request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("routing with exactly one of version or alias is required");
        assertThat(repository.findById("inv-missing-route")).isEmpty();
    }

    @Test
    void submitRejectsAnOmittedRequestWithoutCreatingASentinelPayload() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));

        assertThatThrownBy(() -> service.submit("payment.missing-body", null))
            .isInstanceOf(InvalidAsyncInvocationRequestException.class)
            .hasMessage("routing with exactly one of version or alias is required");
        verify(repository, never()).save(any(AsyncInvocationEntity.class));
    }

    @Test
    void submitCanonicalizesOmittedNamespaceBeforePersistence() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));

        AsyncInvocationResponse accepted = service.submit("payment.default-scope", request("inv-default-scope", 1, 0L));

        assertThat(accepted.routing().namespace()).isEqualTo(ProcessRef.DEFAULT_NAMESPACE);
        assertThat(accepted.routing().requestedAlias()).isEqualTo("production");
        assertThat(repository.findById("inv-default-scope"))
            .get()
            .extracting(AsyncInvocationEntity::getRoutingJson)
            .asString()
            .contains("\"namespace\":\"default\"");
    }

    @Test
    void repeatedSubmissionRequiresTheSameRequestForAnInvocationId() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));
        AsyncInvocationSubmitRequest first = request("inv-idempotent", 1, 0L);
        AsyncInvocationSubmitRequest same = request("inv-idempotent", 1, 0L);
        AsyncInvocationSubmitRequest different = new AsyncInvocationSubmitRequest("inv-idempotent",
                Map.of("amount", 200), aliasRouting("production", null), 1, 0L);

        AsyncInvocationResponse created = service.submit("payment.approve", first);
        AsyncInvocationResponse duplicate = service.submit("payment.approve", same);

        assertThat(duplicate).isEqualTo(created);
        assertThatThrownBy(() -> service.submit("payment.approve", different))
            .isInstanceOf(AsyncInvocationService.AsyncInvocationConflictException.class)
            .hasMessage("invocationId is already bound to a different async request: " + "inv-idempotent");
    }

    @Test
    void invalidParamsPayloadIsDeadLetteredWithoutCallingEngine() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        AsyncInvocationEntity queued = queued("inv-invalid-params", "payment.bad-payload");
        queued.setParamsJson("{");
        repository.save(queued);
        AsyncInvocationService service = service(repository, executionService);

        service.worker().dispatchQueuedInvocations();

        AsyncInvocationResponse result = service.get("inv-invalid-params").orElseThrow();
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.DEAD_LETTER);
        assertThat(result.currentAttemptCount()).isEqualTo(1);
        assertThat(result.error()).isEqualTo("Invalid async invocation paramsJson JSON payload");
        verify(executionService, never())
            .execute(any(PublishedProcessExecutionService.AliasPin.class), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void invalidRoutingPayloadIsDeadLetteredWithoutCallingEngine() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        AsyncInvocationEntity queued = queued("inv-invalid-routing", "payment.bad-route");
        queued.setRoutingJson("[");
        repository.save(queued);
        AsyncInvocationService service = service(repository, executionService);

        service.worker().dispatchQueuedInvocations();

        AsyncInvocationResponse result = service.get("inv-invalid-routing").orElseThrow();
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.DEAD_LETTER);
        assertThat(result.currentAttemptCount()).isEqualTo(1);
        assertThat(result.error()).isEqualTo("Invalid async invocation routingJson JSON payload");
        verify(executionService, never())
            .execute(any(PublishedProcessExecutionService.AliasPin.class), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void retryUsesVersionPinnedBeforeTheFirstEngineCallThrows() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessExecutionResponse secondResponse = response(true, "trace-second", "v2");
        when(executionService.execute(pinFor("payment.canary"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenThrow(new IllegalStateException("connection interrupted"))
            .thenReturn(secondResponse);
        AsyncInvocationService service = service(repository, executionService);
        when(executionService.resolveAliasPin("payment.canary", "default", "production",
                new AliasRoutingOptions("inv-canary-1")))
            .thenReturn(aliasPin("payment.canary", "default", "production", "v2", 7L, ProcessAliasTarget.CANDIDATE));

        service.submit("payment.canary", request("inv-canary-1", 2, 0L));
        service.worker().dispatchQueuedInvocations();
        AsyncInvocationResponse result = service.get("inv-canary-1").orElseThrow();
        AsyncInvocationResponse replay = service.submit("payment.canary", request("inv-canary-1", 2, 0L));

        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
        assertThat(replay).isEqualTo(result);
        assertThat(result.routing().effectiveVersion()).isEqualTo("v2");
        assertThat(result.routing().requestedAlias()).isEqualTo("production");
        assertThat(result.routing().requestedVersion()).isNull();
        assertThat(result.routing().alias()).isEqualTo("production");
        assertThat(result.routing().routeRevision()).isEqualTo(7L);
        assertThat(result.routing().target()).isEqualTo(ProcessAliasTarget.CANDIDATE);
        ArgumentCaptor<PublishedProcessExecutionService.AliasPin> pins =
                ArgumentCaptor.forClass(PublishedProcessExecutionService.AliasPin.class);
        verify(executionService, times(2)).execute(pins.capture(), anyMap(), any(ProcessExecutionOptions.class));
        assertThat(pins.getAllValues()).allSatisfy(pin -> {
            assertThat(pin.alias().code()).isEqualTo("payment.canary");
            assertThat(pin.version()).isEqualTo("v2");
            assertThat(pin.routeRevision()).isEqualTo(7L);
            assertThat(pin.target()).isEqualTo(ProcessAliasTarget.CANDIDATE);
        });
        verify(executionService)
            .resolveAliasPin("payment.canary", "default", "production", new AliasRoutingOptions("inv-canary-1"));
    }

    @Test
    void asyncRepresentationDoesNotExposeInternalRetryReference() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessExecutionResponse engineResponse = response(true, "trace-safe-route", "v2")
            .withRouting(new ExecutionRoutingResponse("default", "v2", null, "v2", null, null, null));
        when(executionService.execute(pinFor("payment.safe-route"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(engineResponse);
        AsyncInvocationService service = service(repository, executionService);
        AsyncInvocationSubmitRequest request = new AsyncInvocationSubmitRequest("inv-safe-route", Map.of("amount", 100),
                aliasRouting("production", null), 1, 0L);

        service.submit("payment.safe-route", request);
        AsyncInvocationResponse result = service.get("inv-safe-route").orElseThrow();

        ExecutionRoutingResponse publicRouting = result.routing();
        assertThat(publicRouting.requestedAlias()).isEqualTo("production");
        assertThat(publicRouting.effectiveVersion()).isEqualTo("v2");
        assertThat(publicRouting.requestedVersion()).isNull();
        assertThat(result.response().routing()).isEqualTo(publicRouting);
        assertThat(result.toString()).doesNotContain("must-not-escape");
    }

    @Test
    void requeueRunsDeadLetterInvocationAgain() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessExecutionResponse engineResponse = response(true, "trace-2", "v1");
        when(executionService.execute(pinFor("payment.retry"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(engineResponse);
        AsyncInvocationEntity deadLetter = deadLetter("inv-retry-1", "payment.retry");
        repository.save(deadLetter);
        AsyncInvocationService service = service(repository, executionService);

        AsyncInvocationResponse accepted = service.requeue("inv-retry-1").orElseThrow();
        AsyncInvocationResponse result = service.get("inv-retry-1").orElseThrow();

        assertThat(accepted.status()).isEqualTo(AsyncInvocationStatus.QUEUED);
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
        assertThat(result.currentAttemptCount()).isEqualTo(1);
        assertThat(result.traceId()).isEqualTo("trace-2");
    }

    @Test
    void listFiltersByProcessCodeAndStatus() {
        AsyncInvocationRepository repository = mock(AsyncInvocationRepository.class);
        AsyncInvocationEntity deadLetter = deadLetter("inv-list-1", "payment.fail");
        List<AsyncInvocationEntity> content = Collections.singletonList(deadLetter);
        when(repository.findByProcessCodeAndStatus(eq("payment.fail"), eq(AsyncInvocationService.STATUS_DEAD_LETTER),
                any(PageRequest.class)))
            .thenReturn(new PageImpl<AsyncInvocationEntity>(content, PageRequest.of(1, 5), 6));
        AsyncInvocationService service = service(repository, mock(PublishedProcessExecutionService.class));

        AsyncInvocationListResponse response =
                service.list(AsyncInvocationService.STATUS_DEAD_LETTER, "payment.fail", 2, 5);

        assertThat(response.total()).isEqualTo(6L);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(5);
        verify(repository)
            .findByProcessCodeAndStatus(eq("payment.fail"), eq(AsyncInvocationService.STATUS_DEAD_LETTER),
                    eq(PageRequest.of(1, 5, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("invocationId")))));
    }

    @Test
    void dispatchAndRecoveryUseDeterministicDatabaseOrdering() {
        AsyncInvocationRepository repository = mock(AsyncInvocationRepository.class);
        when(repository.currentTimeMillis()).thenReturn(100L);
        when(repository.findByStatusAndAvailableAtLessThanEqual(any(String.class), anyLong(), any(PageRequest.class)))
            .thenReturn(new PageImpl<AsyncInvocationEntity>(List.of()));
        when(repository.findByStatusAndLeaseUntilLessThan(any(String.class), anyLong(), any(PageRequest.class)))
            .thenReturn(new PageImpl<AsyncInvocationEntity>(List.of()));
        AsyncInvocationService service = service(repository, mock(PublishedProcessExecutionService.class));

        service.worker().dispatchQueuedInvocations();
        service.worker().recoverExpiredRunningInvocations();

        ArgumentCaptor<PageRequest> dispatchPage = ArgumentCaptor.forClass(PageRequest.class);
        ArgumentCaptor<PageRequest> recoveryPage = ArgumentCaptor.forClass(PageRequest.class);
        verify(repository)
            .findByStatusAndAvailableAtLessThanEqual(eq(AsyncInvocationService.STATUS_QUEUED), eq(100L),
                    dispatchPage.capture());
        verify(repository)
            .findByStatusAndLeaseUntilLessThan(eq(AsyncInvocationService.STATUS_RUNNING), eq(100L),
                    recoveryPage.capture());
        assertThat(dispatchPage.getValue().getSort())
            .containsExactly(Sort.Order.asc("availableAt"), Sort.Order.asc("createdAt"), Sort.Order.asc("invocationId"));
        assertThat(recoveryPage.getValue().getSort())
            .containsExactly(Sort.Order.asc("leaseUntil"), Sort.Order.asc("createdAt"), Sort.Order.asc("invocationId"));
    }

    @Test
    void getAndListExposePayloadErrorsWithoutFailingRepresentation() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationEntity entity = succeeded("inv-corrupt-result", "payment.done");
        entity.setRoutingJson("{");
        entity.setResultJson("{");
        repository.save(entity);
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));

        AsyncInvocationResponse result = service.get("inv-corrupt-result").orElseThrow();
        Map<String, String> payloadErrors = result.payloadErrors();
        AsyncInvocationListResponse page = service.list(null, null, 1, 20);
        Map<String, String> listedErrors = page.data().get(0).payloadErrors();

        assertThat(result.response()).isNull();
        assertThat(payloadErrors)
            .containsEntry("routingJson", "Invalid async invocation routingJson JSON payload")
            .containsEntry("resultJson", "Invalid async invocation resultJson JSON payload");
        assertThat(listedErrors).isEqualTo(payloadErrors);
    }

    @Test
    void getExposesSemanticallyInvalidRoutingWithoutCreatingAPartialSnapshot() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationEntity entity = succeeded("inv-routing-without-namespace", "payment.done");
        entity.setRoutingJson("{\"alias\":\"production\"}");
        entity.setResultJson(null);
        repository.save(entity);
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));

        AsyncInvocationResponse result = service.get("inv-routing-without-namespace").orElseThrow();

        assertThat(result.routing()).isNull();
        assertThat(result.payloadErrors())
            .containsEntry("routingJson", "Invalid async invocation routingJson payload: namespace is required");
    }

    @Test
    void healthReportsQueueCountsAndExpiredRunningTasks() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationEntity ready = queued("inv-health-queued", "payment.health");
        ready.setAvailableAt(System.currentTimeMillis() - 5000L);
        repository.save(ready);
        AsyncInvocationEntity delayed = queued("inv-health-delayed", "payment.health");
        delayed.setAvailableAt(System.currentTimeMillis() + 60000L);
        repository.save(delayed);
        repository.save(running("inv-health-running", "payment.health", 1));
        repository.save(deadLetter("inv-health-dead", "payment.health"));
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));

        AsyncInvocationHealthResponse health = service.health();

        assertThat(health.status()).isEqualTo("degraded");
        assertThat(health.queuedCount()).isEqualTo(2L);
        assertThat(health.readyQueuedCount()).isEqualTo(1L);
        assertThat(health.oldestReadyAgeMs()).isGreaterThanOrEqualTo(4000L);
        assertThat(health.delayedQueuedCount()).isEqualTo(1L);
        assertThat(health.runningCount()).isEqualTo(1L);
        assertThat(health.deadLetterCount()).isEqualTo(1L);
        assertThat(health.expiredRunningCount()).isEqualTo(1L);
    }

    @Test
    void healthDegradesForExpiredLeaseWithoutDeadLetters() {
        AsyncInvocationRepository repository = repository();
        repository.save(running("inv-health-expired", "payment.health", 1));
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));

        AsyncInvocationHealthResponse health = service.health();

        assertThat(health.status()).isEqualTo("degraded");
        assertThat(health.deadLetterCount()).isZero();
        assertThat(health.expiredRunningCount()).isEqualTo(1L);
    }

    @Test
    void requeueDeadLettersResetsBatchAndDispatchesAgain() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessExecutionResponse engineResponse = response(true, "trace-batch-requeue", "v1");
        when(executionService.execute(pinFor("payment.batch"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(engineResponse);
        repository.save(deadLetter("inv-batch-1", "payment.batch"));
        repository.save(deadLetter("inv-batch-2", "payment.batch"));
        repository.save(deadLetter("inv-other-flow", "payment.other"));
        AsyncInvocationService service = service(repository, executionService);

        AsyncInvocationDeadLetterRequeueResponse response = service.requeueDeadLetters("payment.batch", 10);

        assertThat(response.requeued()).isEqualTo(2);
        assertThat(service.get("inv-batch-1").orElseThrow().status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
        assertThat(service.get("inv-batch-2").orElseThrow().status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
        assertThat(service.get("inv-other-flow").orElseThrow().status()).isEqualTo(AsyncInvocationStatus.DEAD_LETTER);
        verify(executionService, times(2)).execute(pinFor("payment.batch"), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void requeueDeadLettersRejectsInvalidLimitInsteadOfSilentlyNormalizingIt() {
        AsyncInvocationService service =
                serviceWithoutDispatch(repository(), mock(PublishedProcessExecutionService.class));

        assertThatThrownBy(() -> service.requeueDeadLetters(null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be between 1 and 500");
        assertThatThrownBy(() -> service.requeueDeadLetters(null, 501))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be between 1 and 500");
    }

    @Test
    void dispatcherRunsQueuedInvocationsFromRepository() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessExecutionResponse engineResponse = response(true, "trace-dispatch", "v1");
        when(executionService.execute(pinFor("payment.dispatch"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(engineResponse);
        AsyncInvocationEntity queued = queued("inv-dispatch-1", "payment.dispatch");
        repository.save(queued);
        AsyncInvocationService service = service(repository, executionService);

        service.worker().dispatchQueuedInvocations();

        AsyncInvocationResponse result = service.get("inv-dispatch-1").orElseThrow();
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
        assertThat(result.traceId()).isEqualTo("trace-dispatch");
    }

    @Test
    void claimedTaskLoadFailureDoesNotKeepRenewingAnOrphanedLease() {
        AsyncInvocationRepository repository = repository();
        repository.save(queued("inv-load-failure", "payment.load-failure"));
        when(repository.findById("inv-load-failure")).thenThrow(new IllegalStateException("repository unavailable"));
        AsyncInvocationService service = service(repository, mock(PublishedProcessExecutionService.class));

        service.worker().dispatchQueuedInvocations();
        service.worker().renewLocalRunningLeases();

        verify(repository, never())
            .extendLease(eq("inv-load-failure"), eq(AsyncInvocationService.STATUS_RUNNING), any(String.class), anyLong(),
                    anyLong());
    }

    @Test
    void startupDispatchesQueuedInvocations() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessExecutionResponse engineResponse = response(true, "trace-startup", "v1");
        when(executionService.execute(pinFor("payment.startup"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(engineResponse);
        repository.save(queued("inv-startup-queued", "payment.startup"));
        AsyncInvocationService service = service(repository, executionService);

        service.worker().start();

        AsyncInvocationResponse result = service.get("inv-startup-queued").orElseThrow();
        assertThat(service.worker().isRunning()).isTrue();
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
        assertThat(result.traceId()).isEqualTo("trace-startup");
    }

    @Test
    void lifecycleClosesAdmissionOnContextCloseAndDrainsBeforeStopping() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        AsyncInvocationService service = service(repository, executionService);
        org.springframework.context.ApplicationContext context =
                mock(org.springframework.context.ApplicationContext.class);
        service.worker().setApplicationContext(context);

        service.worker().start();
        service.worker().start();

        assertThat(service.worker().isRunning()).isTrue();
        assertThat(service.worker().isAcceptingWork()).isTrue();
        assertThat(service.worker().getPhase())
            .isEqualTo(AsyncInvocationService.LIFECYCLE_PHASE)
            .isLessThan(org.springframework.context.SmartLifecycle.DEFAULT_PHASE - 1024);
        service.worker().onApplicationEvent(new org.springframework.context.event.ContextClosedEvent(context));
        assertThat(service.worker().isRunning()).isTrue();
        assertThat(service.worker().isAcceptingWork()).isFalse();
        AsyncInvocationResponse queued = service.submit("payment.shutdown", request("inv-after-admission-close", 1, 0L));
        assertThat(queued.status()).isEqualTo(AsyncInvocationStatus.QUEUED);
        verify(executionService, never())
            .execute(any(PublishedProcessExecutionService.AliasPin.class), anyMap(), any(ProcessExecutionOptions.class));
        service.worker().stop();
        assertThat(service.worker().isRunning()).isFalse();
        assertThat(service.worker().isAcceptingWork()).isFalse();
    }

    @Test
    void stopKeepsWorkerRunningDuringExecutorDrainSoLeasesCanRenew() throws Exception {
        CompileFlowWorkbenchServerProperties properties = mock(CompileFlowWorkbenchServerProperties.class);
        CompileFlowWorkbenchServerProperties.AsyncInvocation asyncInvocation =
                mock(CompileFlowWorkbenchServerProperties.AsyncInvocation.class);
        when(properties.getAsyncInvocation()).thenReturn(asyncInvocation);
        when(asyncInvocation.getConcurrency()).thenReturn(1);
        when(asyncInvocation.getQueueCapacity()).thenReturn(1);
        when(asyncInvocation.getDispatchBatchSize()).thenReturn(1);
        when(asyncInvocation.getLeaseDuration()).thenReturn(Duration.ofMillis(500));
        LifecycleProperties lifecycleProperties = new LifecycleProperties();
        lifecycleProperties.setTimeoutPerShutdownPhase(Duration.ofSeconds(5));
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        stubAliasResolution(executionService);
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch releaseExecution = new CountDownLatch(1);
        when(executionService.execute(pinFor("payment.drain"), anyMap(), any(ProcessExecutionOptions.class)))
            .thenAnswer(invocation -> {
                executionStarted.countDown();
                assertThat(releaseExecution.await(5, TimeUnit.SECONDS)).isTrue();
                return response(true, "trace-drain", "v1");
            });
        AsyncInvocationService service = new AsyncInvocationService(repository, stateMachine(repository),
                executionService, readyRuntimeInstaller(), properties, lifecycleProperties, new ObjectMapper());
        repository.save(queued("inv-drain-stop", "payment.drain"));
        org.springframework.context.ApplicationContext context =
                mock(org.springframework.context.ApplicationContext.class);
        service.worker().setApplicationContext(context);

        service.worker().start();
        assertThat(executionStarted.await(5, TimeUnit.SECONDS)).isTrue();
        service.worker().onApplicationEvent(new org.springframework.context.event.ContextClosedEvent(context));
        Thread stopThread = new Thread(service.worker()::stop, "async-worker-stop-test");
        stopThread.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (service.worker().isAcceptingWork() && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            assertThat(service.worker().isAcceptingWork()).isFalse();
            assertThat(stopThread.isAlive()).isTrue();
            assertThat(service.worker().isRunning()).isTrue();
            Long beforeLeaseUntil = repository.findById("inv-drain-stop").orElseThrow().getLeaseUntil();
            assertThat(beforeLeaseUntil).isNotNull();

            TimeUnit.MILLISECONDS.sleep(25);
            service.worker().renewLocalRunningLeases();

            assertThat(repository.findById("inv-drain-stop").orElseThrow().getLeaseUntil()).isGreaterThan(
                    beforeLeaseUntil);
        } finally {
            releaseExecution.countDown();
            stopThread.join(5_000L);
        }
        assertThat(stopThread.isAlive()).isFalse();
        assertThat(service.worker().isRunning()).isFalse();
        assertThat(service.worker().isAcceptingWork()).isFalse();
        assertThat(service.get("inv-drain-stop").orElseThrow().status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
    }

    @Test
    void ownedWorkerRecreatesItsExecutorAcrossLifecycleRestart() {
        CompileFlowWorkbenchServerProperties properties = mock(CompileFlowWorkbenchServerProperties.class);
        CompileFlowWorkbenchServerProperties.AsyncInvocation asyncInvocation =
                mock(CompileFlowWorkbenchServerProperties.AsyncInvocation.class);
        when(properties.getAsyncInvocation()).thenReturn(asyncInvocation);
        when(asyncInvocation.getConcurrency()).thenReturn(1);
        when(asyncInvocation.getQueueCapacity()).thenReturn(1);
        when(asyncInvocation.getDispatchBatchSize()).thenReturn(1);
        when(asyncInvocation.getLeaseDuration()).thenReturn(TEST_LEASE_DURATION);
        AsyncInvocationRepository repository = repository();
        AsyncInvocationService service = new AsyncInvocationService(repository, stateMachine(repository),
                mock(PublishedProcessExecutionService.class), readyRuntimeInstaller(), properties,
                new LifecycleProperties(), new ObjectMapper());

        service.worker().start();
        service.worker().stop();
        service.worker().start();

        assertThat(service.worker().isRunning()).isTrue();
        service.worker().destroy();
        assertThatThrownBy(service.worker()::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Async invocation worker is destroyed");
    }

    @Test
    void forcedStopReleasesQueuedDispatchForLifecycleRestart() throws Exception {
        CompileFlowWorkbenchServerProperties properties = mock(CompileFlowWorkbenchServerProperties.class);
        CompileFlowWorkbenchServerProperties.AsyncInvocation asyncInvocation =
                mock(CompileFlowWorkbenchServerProperties.AsyncInvocation.class);
        when(properties.getAsyncInvocation()).thenReturn(asyncInvocation);
        when(asyncInvocation.getConcurrency()).thenReturn(1);
        when(asyncInvocation.getQueueCapacity()).thenReturn(1);
        when(asyncInvocation.getDispatchBatchSize()).thenReturn(2);
        when(asyncInvocation.getLeaseDuration()).thenReturn(TEST_LEASE_DURATION);
        LifecycleProperties lifecycleProperties = new LifecycleProperties();
        lifecycleProperties.setTimeoutPerShutdownPhase(Duration.ofMillis(50));
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        stubAliasResolution(executionService);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(executionService.execute(any(PublishedProcessExecutionService.AliasPin.class), anyMap(),
                any(ProcessExecutionOptions.class)))
            .thenAnswer(invocation -> {
                PublishedProcessExecutionService.AliasPin pin = invocation.getArgument(0);
                if ("payment.first".equals(pin.alias().code())) {
                    firstStarted.countDown();
                    try {
                        releaseFirst.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return response(true, "trace-first", "v1");
                }
                return response(true, "trace-second", "v1");
            });
        AsyncInvocationService service = new AsyncInvocationService(repository, stateMachine(repository),
                executionService, readyRuntimeInstaller(), properties, lifecycleProperties, new ObjectMapper());
        service.worker().start();
        try {
            repository.save(queued("inv-first", "payment.first"));
            repository.save(queued("inv-second", "payment.second"));
            service.worker().dispatch("inv-first");
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            service.worker().dispatch("inv-second");
            assertThat(service.worker().dispatchedCount()).isEqualTo(2);

            service.worker().stop();

            service.worker().start();
            long completionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (service.get("inv-second").orElseThrow().status() != AsyncInvocationStatus.SUCCEEDED
                    && System.nanoTime() < completionDeadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            assertThat(service.get("inv-second").orElseThrow().status()).isEqualTo(AsyncInvocationStatus.SUCCEEDED);
        } finally {
            releaseFirst.countDown();
            service.worker().destroy();
        }
    }

    @Test
    void startupDoesNotRequeueRunningInvocationWithValidLease() {
        AsyncInvocationRepository repository = repository();
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        AsyncInvocationEntity running = running("inv-active-running", "payment.active", 1);
        running.setLeaseUntil(System.currentTimeMillis() + 60000L);
        repository.save(running);
        AsyncInvocationService service = service(repository, executionService);

        service.worker().start();

        AsyncInvocationResponse result = service.get("inv-active-running").orElseThrow();
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.RUNNING);
        assertThat(result.currentAttemptCount()).isEqualTo(1);
        assertThat(result.error()).isEqualTo("previous attempt stalled");
        verify(executionService, never())
            .execute(any(PublishedProcessExecutionService.AliasPin.class), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void recoveryRequeuesExpiredRunningInvocation() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationEntity running = running("inv-expired-1", "payment.expired", 1);
        repository.save(running);
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));

        service.worker().recoverExpiredRunningInvocations();

        AsyncInvocationResponse result = service.get("inv-expired-1").orElseThrow();
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.QUEUED);
        assertThat(result.error()).isEqualTo("Async invocation lease expired");
    }

    @Test
    void recoveryDeadLettersExpiredRunningInvocationAfterRetryBudget() {
        AsyncInvocationRepository repository = repository();
        AsyncInvocationEntity running = running("inv-expired-dead-1", "payment.expired", 3);
        repository.save(running);
        AsyncInvocationService service =
                serviceWithoutDispatch(repository, mock(PublishedProcessExecutionService.class));

        service.worker().recoverExpiredRunningInvocations();

        AsyncInvocationResponse result = service.get("inv-expired-dead-1").orElseThrow();
        assertThat(result.status()).isEqualTo(AsyncInvocationStatus.DEAD_LETTER);
        assertThat(result.error()).isEqualTo("Async invocation lease expired");
    }
}
