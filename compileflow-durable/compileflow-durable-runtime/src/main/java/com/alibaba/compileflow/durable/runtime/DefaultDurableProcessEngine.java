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
package com.alibaba.compileflow.durable.runtime;

import com.alibaba.compileflow.durable.api.DurableProcessEngine;
import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunPage;
import com.alibaba.compileflow.durable.api.model.ProcessRunQuery;
import com.alibaba.compileflow.durable.api.model.ProcessRunResult;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.WaitToken;
import com.alibaba.compileflow.durable.api.validation.DurablePayload;
import com.alibaba.compileflow.durable.runtime.codec.DurableKernelJsonCodec;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.worker.DurableWorkerCoordinator;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer;
import com.alibaba.compileflow.engine.core.lifecycle.OperationGate;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import java.util.function.Supplier;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.runtime.service.DurableCursorCodec;
import com.alibaba.compileflow.durable.runtime.service.DurableStoreResultMapper;
import com.alibaba.compileflow.durable.spi.store.DurableDigests;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default application-facing engine for exact stored-Process Durable Runs.
 *
 * @author yusu
 */
public final class DefaultDurableProcessEngine implements DurableProcessEngine {
    private static final DurableKernelJsonCodec KERNEL_JSON = new DurableKernelJsonCodec();
    private final DurableStore store;
    private final boolean outboxSinkConfigured;
    private final DurableProcessRuntimeManager processManager;
    private final AliasAdmission aliasAdmission;
    private final OperationGate lifecycle = new OperationGate("DurableProcessEngine");
    private final DurableProcessRuntimeCache runtimeCache;
    private final ScriptExecutorRegistry scripts;
    private final DurableLeaseRenewer leases;
    private final DurableWorkerCoordinator workers;
    private final DurableRuntimeMetrics metrics;

    DefaultDurableProcessEngine(DurableStore store, DurableProcessRuntimeManager processManager,
            AliasAdmission aliasAdmission, DurableProcessRuntimeCache runtimeCache, ScriptExecutorRegistry scripts,
            DurableLeaseRenewer leases, DurableWorkerCoordinator workers, DurableRuntimeMetrics metrics,
            boolean outboxSinkConfigured) {
        this.store = Objects.requireNonNull(store, "store");
        this.outboxSinkConfigured = outboxSinkConfigured;
        this.processManager = Objects.requireNonNull(processManager, "processManager");
        this.aliasAdmission = aliasAdmission;
        this.runtimeCache = Objects.requireNonNull(runtimeCache, "runtimeCache");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.leases = leases;
        this.workers = workers;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public DurableProcessRuntimeManager getProcessRuntimeManager() {
        return processManager;
    }

    public DurableStore getStore() {
        return store;
    }

    public boolean isOutboxSinkConfigured() {
        return outboxSinkConfigured;
    }

    public DurableProcessRuntimeCache getRuntimeCache() {
        return runtimeCache;
    }

    public DurableRuntimeMetrics getMetrics() {
        return metrics;
    }

    public DurableWorkerCoordinator getWorkerCoordinator() {
        return workers;
    }

    public DurableLeaseRenewer getLeaseRenewer() {
        return leases;
    }

    @Override
    public void start() {
        withEngine(() -> {
            if (workers != null) {
                workers.start();
            }
            return null;
        });
    }

    @Override
    public void stop() {
        withEngine(() -> {
            if (workers != null) {
                workers.stop();
            }
            return null;
        });
    }

    @Override
    public boolean isRunning() {
        return workers != null && workers.isRunning();
    }

    private <T> T withEngine(Supplier<T> operation) {
        lifecycle.enter();
        try {
            return operation.get();
        } finally {
            lifecycle.exit();
        }
    }

    @Override
    public void close() {
        if (workers != null) {
            workers.requireExternalLifecycleCaller();
        }
        if (leases != null) {
            leases.requireExternalLifecycleCaller();
        }
        OperationGate.DrainResult drain = lifecycle.beginCloseAndAwaitDrained();
        if (!drain.cleanupOwner()) {
            return;
        }
        boolean interrupted = Thread.interrupted();
        try {
            Throwable failure = null;
            try {
                if (workers != null) {
                    workers.stop();
                }
            } catch (RuntimeException | Error stopped) {
                failure = stopped;
            }
            try {
                if (leases != null) {
                    leases.close();
                }
            } catch (RuntimeException | Error closed) {
                failure = accumulate(failure, closed);
            }
            try {
                scripts.close();
            } catch (RuntimeException | Error closed) {
                failure = accumulate(failure, closed);
            }
            try {
                runtimeCache.clear();
            } catch (RuntimeException | Error cleared) {
                failure = accumulate(failure, cleared);
            }
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        } finally {
            lifecycle.finishClose();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Throwable accumulate(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    @Override
    public ProcessRun start(ProcessRunId runId, ProcessDefinition definition, Map<String, ?> input) {
        return withEngine(() -> {
            ProcessRunId requestedRunId = Objects.requireNonNull(runId, "runId");
            rejectExistingRun(requestedRunId);
            DurableStore.RunProcess stored = processManager.register(Objects.requireNonNull(definition, "definition"));
            return startStored(requestedRunId, stored, input, null);
        });
    }

    @Override
    public ProcessRun start(ProcessRunId runId, ProcessRef.Version version, Map<String, ?> input) {
        return withEngine(() -> {
            ProcessRunId requestedRunId = Objects.requireNonNull(runId, "runId");
            rejectExistingRun(requestedRunId);
            DurableStore.RunProcess stored = processManager.register(Objects.requireNonNull(version, "version"));
            return startStored(requestedRunId, stored, input, null);
        });
    }

    @Override
    public ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input,
            AliasRoutingOptions options) {
        return withEngine(() -> {
            ProcessRunId requestedRunId = Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(alias, "alias");
            AliasRoutingOptions requestedRouting = Objects.requireNonNull(options, "options");
            AliasRoutingOptions routing = requestedRouting.routingKey() == null
                    ? new AliasRoutingOptions(requestedRunId.value(), requestedRouting.attributes())
                    : requestedRouting;
            rejectExistingRun(requestedRunId);
            if (aliasAdmission == null) {
                throw new IllegalStateException("Durable Alias admission is not configured");
            }
            AliasAdmission.Selection selection = aliasAdmission.admit(alias, routing);
            ProcessRef.Version version = selection.version();
            if (!alias.namespace().equals(version.namespace()) || !alias.code().equals(version.code())) {
                throw new IllegalStateException("Alias admission changed Process namespace or code");
            }
            DurableStore.RunProcess stored = processManager.register(version);
            return startStored(requestedRunId, stored, input, aliasAdmissionFact(alias, selection));
        });
    }

    @Override
    public ProcessRun completeWait(WaitToken token, Map<String, ?> result) {
        return withEngine(() -> {
            String tokenDigest = DurableDigests.sha256(Objects.requireNonNull(token, "token").value());
            DurableStore.WaitTarget target =
                    DurableStoreResultMapper.requireWaitTarget(store.findWaitTarget(tokenDigest), tokenDigest);
            byte[] encoded =
                    processManager
                .requireValueSerializer(target.processId())
                .encodeWaitPayload(payload(result, "result"));
            return DurableStoreResultMapper.requireRun(store.completeWait(
                            new DurableStore.WaitCompletion(target.runId(), tokenDigest,
                                    new DurableStore.Envelope(encoded))), target.runId(), target.rootProcess());
        });
    }

    @Override
    public ProcessRun cancel(ProcessRunId runId) {
        return withEngine(() -> {
            ProcessRunId owner = Objects.requireNonNull(runId, "runId");
            return DurableStoreResultMapper.requireRun(store.requestCancel(new DurableStore.CancelCommand(owner)), owner);
        });
    }

    @Override
    public Optional<ProcessRun> getRun(ProcessRunId runId) {
        return withEngine(() -> {
            ProcessRunId requested = Objects.requireNonNull(runId, "runId");
            return DurableStoreResultMapper.requireRunLookup(store.findRun(requested), requested);
        });
    }

    @Override
    public ProcessRunPage listRuns(ProcessRunQuery query) {
        return withEngine(() -> {
            Objects.requireNonNull(query, "query");
            DurableCursorCodec.RunKey cursor = DurableCursorCodec.run(query.cursor());
            DurableStore.RunPage page = store.listRuns(
                    new DurableStore.RunQuery(query.namespace(), query.code(), query.statuses(),
                            cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.runId(),
                            query.limit()));
            return DurableStoreResultMapper.runPage(query, page);
        });
    }

    @Override
    public ProcessRunResult getRunResult(ProcessRunId runId) {
        return withEngine(() -> {
            ProcessRunId requested = Objects.requireNonNull(runId, "runId");
            Optional<DurableStore.RunResultProjection> found =
                    DurableStoreResultMapper.requireRunResultLookup(store.findRunResult(requested), requested);
            if (found.isEmpty()) {
                return new ProcessRunResult.NotFound(requested);
            }
            DurableStore.RunResultProjection result = found.orElseThrow();
            DurableStore.RunProcess process = result.rootProcess();
            return switch (result.status()) {
                case SUCCEEDED -> {
                    DurableProcessRuntime loaded = processManager.requireRuntime(process.processId());
                    yield new ProcessRunResult.Succeeded(requested, process.namespace(), process.processCode(),
                            process.processVersion(),
                            loaded.valueSerializer().decodeProcessResult(result.result().payload()),
                            result.completedAt());
                }
                case FAILED -> new ProcessRunResult.Failed(requested, process.namespace(), process.processCode(),
                        process.processVersion(), result.failureCode(), result.failureMessage(), result.completedAt());
                case CANCELLED -> new ProcessRunResult.Cancelled(requested, process.namespace(), process.processCode(),
                        process.processVersion(), result.completedAt());
                default -> new ProcessRunResult.NotCompleted(requested, process.namespace(), process.processCode(),
                        process.processVersion(), result.status());
            };
        });
    }

    private ProcessRun startStored(ProcessRunId runId, DurableStore.RunProcess registered, Map<String, ?> input,
            DurableStore.Envelope admissionFact) {
        DurableStore.NewRun command =
                processManager.createNewRun(runId, registered, payload(input, "input"), admissionFact);
        return DurableStoreResultMapper.requireRun(store.start(command), runId, registered);
    }

    private void rejectExistingRun(ProcessRunId runId) {
        if (DurableStoreResultMapper.requireRunLookup(store.findRun(runId), runId).isPresent()) {
            throw DurableProcessException.of(DurableErrorCode.RUN_ALREADY_EXISTS, "Durable Run ID already exists");
        }
    }

    static DurableStore.Envelope aliasAdmissionFact(ProcessRef.Alias requested, AliasAdmission.Selection selection) {
        Map<String, Object> attribution = new LinkedHashMap<>();
        attribution.put("alias", requested.alias());
        attribution.put("aliasRevision", selection.aliasRevision());
        attribution.put("aliasTarget", selection.target());
        attribution.put("processVersion", selection.version().version());
        attribution.put("aliasSelectionReason", selection.reason());
        if (selection.targetingPolicy() != null) {
            attribution.put("aliasTargetingPolicy", selection.targetingPolicy());
        }
        return new DurableStore.Envelope(KERNEL_JSON.encode(attribution));
    }

    private static Map<String, Object> payload(Map<String, ?> source, String name) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach(copy::put);
        return DurablePayload.immutablePayload(copy, name);
    }
}
