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
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.runtime.service.DurableCursorCodec;
import com.alibaba.compileflow.durable.runtime.service.DurableStoreResultMapper;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasStateSource;
import com.alibaba.compileflow.durable.spi.store.DurableDigests;
import com.alibaba.compileflow.durable.spi.store.DurableProcessStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
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
    private final DurableProcessStore store;
    private final DurableProcessRuntimeManager processManager;
    private final AliasAdmission aliasAdmission;

    public DefaultDurableProcessEngine(DurableProcessStore store, DurableProcessRuntimeManager processManager) {
        this(store, processManager, (AliasAdmission) null);
    }

    public DefaultDurableProcessEngine(DurableProcessStore store, DurableProcessRuntimeManager processManager,
            DurableAliasStateSource aliasStateSource) {
        this(store, processManager, new AliasAdmission(aliasStateSource));
    }

    public DefaultDurableProcessEngine(DurableProcessStore store, DurableProcessRuntimeManager processManager,
            DurableAliasStateSource aliasStateSource, Map<String, ProcessAliasTargetingPolicy> aliasTargetingPolicies) {
        this(store, processManager, new AliasAdmission(aliasStateSource, aliasTargetingPolicies));
    }

    DefaultDurableProcessEngine(DurableProcessStore store, DurableProcessRuntimeManager processManager,
            AliasAdmission aliasAdmission) {
        this.store = Objects.requireNonNull(store, "store");
        this.processManager = Objects.requireNonNull(processManager, "processManager");
        this.aliasAdmission = aliasAdmission;
    }

    @Override
    public ProcessRun start(ProcessRunId runId, ProcessDefinition definition, Map<String, ?> input) {
        ProcessRunId requestedRunId = Objects.requireNonNull(runId, "runId");
        rejectExistingRun(requestedRunId);
        DurableStore.RunProcess stored = processManager.register(Objects.requireNonNull(definition, "definition"));
        return startStored(requestedRunId, stored, input, null);
    }

    @Override
    public ProcessRun start(ProcessRunId runId, ProcessRef.Version version, Map<String, ?> input) {
        ProcessRunId requestedRunId = Objects.requireNonNull(runId, "runId");
        rejectExistingRun(requestedRunId);
        DurableStore.RunProcess stored = processManager.register(Objects.requireNonNull(version, "version"));
        return startStored(requestedRunId, stored, input, null);
    }

    @Override
    public ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input,
            AliasRoutingOptions options) {
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
    }

    @Override
    public ProcessRun completeWait(WaitToken token, Map<String, ?> result) {
        String tokenDigest = DurableDigests.sha256(Objects.requireNonNull(token, "token").value());
        DurableStore.WaitTarget target =
                DurableStoreResultMapper.requireWaitTarget(store.findWaitTarget(tokenDigest), tokenDigest);
        byte[] encoded =
                processManager.requireValueSerializer(target.processId()).encodeWaitPayload(payload(result, "result"));
        return DurableStoreResultMapper.requireRun(store.completeWait(
                        new DurableStore.WaitCompletion(target.runId(), tokenDigest, new DurableStore.Envelope(encoded))),
                target.runId(), target.rootProcess());
    }

    @Override
    public ProcessRun cancel(ProcessRunId runId) {
        ProcessRunId owner = Objects.requireNonNull(runId, "runId");
        return DurableStoreResultMapper.requireRun(store.requestCancel(new DurableStore.CancelCommand(owner)), owner);
    }

    @Override
    public Optional<ProcessRun> getRun(ProcessRunId runId) {
        ProcessRunId requested = Objects.requireNonNull(runId, "runId");
        return DurableStoreResultMapper.requireRunLookup(store.findRun(requested), requested);
    }

    @Override
    public ProcessRunPage listRuns(ProcessRunQuery query) {
        Objects.requireNonNull(query, "query");
        DurableCursorCodec.RunKey cursor = DurableCursorCodec.run(query.cursor());
        DurableStore.RunPage page = store.listRuns(
                new DurableStore.RunQuery(query.namespace(), query.code(), query.statuses(),
                        cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.runId(),
                        query.limit()));
        return DurableStoreResultMapper.runPage(query, page);
    }

    @Override
    public ProcessRunResult getRunResult(ProcessRunId runId) {
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
                        loaded.valueSerializer().decodeProcessResult(result.result().payload()), result.completedAt());
            }
            case FAILED -> new ProcessRunResult.Failed(requested, process.namespace(), process.processCode(),
                    process.processVersion(), result.failureCode(), result.failureMessage(), result.completedAt());
            case CANCELLED -> new ProcessRunResult.Cancelled(requested, process.namespace(), process.processCode(),
                    process.processVersion(), result.completedAt());
            default -> new ProcessRunResult.NotCompleted(requested, process.namespace(), process.processCode(),
                    process.processVersion(), result.status());
        };
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
