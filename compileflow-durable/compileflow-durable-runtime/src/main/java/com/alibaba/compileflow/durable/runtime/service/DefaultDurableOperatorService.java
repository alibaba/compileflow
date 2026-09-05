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
package com.alibaba.compileflow.durable.runtime.service;

import com.alibaba.compileflow.durable.api.DurableOperatorService;
import com.alibaba.compileflow.durable.api.command.EffectResolutionDecision;
import com.alibaba.compileflow.durable.api.command.OutboxResolutionDecision;
import com.alibaba.compileflow.durable.api.command.PauseRunCommand;
import com.alibaba.compileflow.durable.api.command.ResolveEffectCommand;
import com.alibaba.compileflow.durable.api.command.ResolveOutboxEventCommand;
import com.alibaba.compileflow.durable.api.command.ResumeRunCommand;
import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.api.model.ActiveWorkPage;
import com.alibaba.compileflow.durable.api.model.ActiveWorkQuery;
import com.alibaba.compileflow.durable.api.model.OutboxEventPage;
import com.alibaba.compileflow.durable.api.model.OutboxEventQuery;
import com.alibaba.compileflow.durable.api.model.OutboxEvent;
import com.alibaba.compileflow.durable.api.model.AuditPrincipal;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunPage;
import com.alibaba.compileflow.durable.api.model.ProcessRunQuery;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.ProcessTimelinePage;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineQuery;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.spi.store.DurableOperatorStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application-code-free operator facade over committed Kernel authority.
 *
 * @author yusu
 */
public final class DefaultDurableOperatorService implements DurableOperatorService {
    private final DurableOperatorStore store;
    private final DurableProcessRuntimeManager processManager;

    public DefaultDurableOperatorService(DurableOperatorStore store, DurableProcessRuntimeManager processManager) {
        this.store = Objects.requireNonNull(store, "store");
        this.processManager = Objects.requireNonNull(processManager, "processManager");
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
    public ProcessTimelinePage listTimeline(ProcessTimelineQuery query) {
        Objects.requireNonNull(query, "query");
        DurableCursorCodec.TimelineKey cursor = DurableCursorCodec.timeline(query.cursor());
        DurableStore.TimelinePage page = store.listTimeline(
                new DurableStore.TimelineQuery(query.runId(), cursor == null ? 0 : cursor.snapshotSequence(),
                        cursor == null ? 0 : cursor.lastSequence(), query.limit()));
        return DurableStoreResultMapper.timelinePage(query, page);
    }

    @Override
    public ActiveWorkPage listActiveWork(ActiveWorkQuery query) {
        Objects.requireNonNull(query, "query");
        long after = DurableCursorCodec.activeWork(query.cursor());
        return DurableStoreResultMapper.activeWorkPage(query,
                store.listActiveWork(new DurableStore.ActiveWorkQuery(query.runId(), after, query.limit())));
    }

    @Override
    public ProcessRun pauseRun(PauseRunCommand command) {
        Objects.requireNonNull(command, "command");
        return control(command.runId(), command.expectedControlRevision(), DurableStore.ControlOperation.PAUSE,
                command.actor(), command.reason(), command.auditContextId());
    }

    @Override
    public ProcessRun resumeRun(ResumeRunCommand command) {
        Objects.requireNonNull(command, "command");
        return control(command.runId(), command.expectedControlRevision(), DurableStore.ControlOperation.RESUME,
                command.actor(), command.reason(), command.auditContextId());
    }

    @Override
    public Optional<OutboxEvent> getOutboxEvent(ProcessRunId runId, String eventId) {
        ProcessRunId owner = Objects.requireNonNull(runId, "runId");
        UUID occurrence = UUID.fromString(DurableIdentifiers.requireCanonicalUuid(eventId, "eventId"));
        return DurableStoreResultMapper.requireOutboxLookup(store.findOutbox(owner, occurrence), owner, occurrence);
    }

    @Override
    public OutboxEventPage listOutboxEvents(OutboxEventQuery query) {
        Objects.requireNonNull(query, "query");
        DurableCursorCodec.OutboxKey cursor = DurableCursorCodec.outbox(query.cursor());
        DurableStore.OutboxPage page = store.listOutbox(
                new DurableStore.OutboxQuery(query.namespace(), query.code(), query.statuses(),
                        cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.eventId(),
                        query.limit()));
        return DurableStoreResultMapper.outboxPage(query, page);
    }

    @Override
    public ProcessRun resolveEffect(ResolveEffectCommand command) {
        Objects.requireNonNull(command, "command");
        ProcessRunId owner = command.runId();
        UUID occurrence = UUID.fromString(command.effectId());
        EffectResolutionDecision resolution = command.decision();
        Map<String, ?> typedResult = command.result();
        DurableStore.Envelope encoded = null;
        DurableStore.RunProcess targetProcess = null;
        if (resolution == EffectResolutionDecision.CONFIRM_SUCCEEDED) {
            DurableStore.EffectTarget target = store
                .findEffectTarget(owner, occurrence)
                .orElseThrow(() -> DurableProcessException.of(DurableErrorCode.EFFECT_NOT_FOUND, "Effect does not exist"));
            target = DurableStoreResultMapper.requireEffectTarget(target, owner, occurrence);
            targetProcess = target.rootProcess();
            encoded = new DurableStore.Envelope(processManager
                .requireValueSerializer(target.processId())
                .encodeEffectOutput(target.elementId(), typedResult));
        }
        ProcessRun resolved = store.resolveEffect(
                new DurableStore.EffectResolution(owner, occurrence, command.expectedReviewRevision(), resolution,
                        encoded, command.reason(), command.actor(), command.auditContextId()));
        return targetProcess == null
                ? DurableStoreResultMapper.requireRun(resolved, owner)
                : DurableStoreResultMapper.requireRun(resolved, owner, targetProcess);
    }

    @Override
    public OutboxEvent resolveOutboxEvent(ResolveOutboxEventCommand command) {
        Objects.requireNonNull(command, "command");
        UUID occurrence = UUID.fromString(command.eventId());
        return DurableStoreResultMapper.requireOutbox(store.resolveOutbox(
                        new DurableStore.OutboxResolution(occurrence, command.expectedRevision(),
                                command.decision() == OutboxResolutionDecision.RETRY
                                ? DurableStore.OutboxResolutionDecision.RETRY
                                : DurableStore.OutboxResolutionDecision.ABANDON, command.actor(), command.reason(),
                                command.auditContextId())), occurrence);
    }

    private ProcessRun control(ProcessRunId runId, long expectedControlRevision, DurableStore.ControlOperation operation,
            AuditPrincipal actor, String reason, String auditContextId) {
        ProcessRunId owner = Objects.requireNonNull(runId, "runId");
        return DurableStoreResultMapper.requireRun(store.control(
                        new DurableStore.ControlCommand(owner, expectedControlRevision, operation,
                                Objects.requireNonNull(actor, "actor"),
                                DurableIdentifiers.requireHumanText(reason, "reason",
                                        DurableIdentifiers.MAX_REASON_CHARACTERS), auditContextId)), owner);
    }
}
