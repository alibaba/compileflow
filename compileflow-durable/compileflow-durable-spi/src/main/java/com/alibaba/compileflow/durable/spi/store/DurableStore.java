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
package com.alibaba.compileflow.durable.spi.store;

import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.api.command.EffectResolutionDecision;
import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.durable.api.model.OutboxEventStatus;
import com.alibaba.compileflow.durable.api.model.OutboxEvent;
import com.alibaba.compileflow.durable.api.model.ActiveWork;
import com.alibaba.compileflow.durable.api.model.AuditPrincipal;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * First-party Durable Kernel provider transaction contract.
 *
 * <p>This is deliberately a transaction protocol rather than a generic storage
 * abstraction. Every mutating method corresponds to one legal state
 * transition over the Durable authority state model. Application code is always executed
 * between a claim commit and a token-fenced completion commit.</p>
 *
 * @author yusu
 */
public interface DurableStore
        extends DurableCatalogStore, DurableProcessStore, DurableOperatorStore, DurableTurnStore, DurableEffectStore,
        DurableOutboxDeliveryStore, DurableLeaseStore, DurableMaintenanceStore {
    int MAX_DEFINITION_BYTES = 4 * 1024 * 1024;
    int MAX_ENVELOPE_BYTES = 4 * 1024 * 1024;

    /**
     * Exact stored Process identity and admission attribution selected for one Run.
     */
    record RunProcess(UUID processId, String namespace, String processCode, ProcessRef.Version processVersion) {
        public RunProcess {
            processId = Objects.requireNonNull(processId, "processId");
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            processCode = ProcessIdentifiers.requireCode(processCode);
            requireVersionAttribution(namespace, processCode, processVersion);
        }
    }

    /**
     * One immutable stored-Process registration.
     */
    record ProcessRegistration(UUID processId, String processCode, ProcessModelType modelType, byte[] definitionBytes,
            String definitionDigest) {
        public ProcessRegistration {
            processId = Objects.requireNonNull(processId, "processId");
            processCode = ProcessIdentifiers.requireCode(processCode);
            modelType = Objects.requireNonNull(modelType, "modelType");
            definitionBytes = copyDefinitionBytes(definitionBytes);
            definitionDigest = DurableIdentifiers.requireSha256(definitionDigest, "definitionDigest");
            String computed = ProcessDefinitionDigest.compute(modelType, processCode, definitionBytes);
            if (!definitionDigest.equals(computed)) {
                throw new IllegalArgumentException("definitionDigest does not match the stored Process definition");
            }
        }

        @Override
        public byte[] definitionBytes() {
            return definitionBytes.clone();
        }
    }

    /**
     * Immutable Process semantics returned by a Durable Store.
     */
    record StoredProcess(UUID processId, String processCode, ProcessModelType modelType, byte[] definitionBytes,
            String definitionDigest, Instant registeredAt) {
        public StoredProcess {
            processId = Objects.requireNonNull(processId, "processId");
            processCode = ProcessIdentifiers.requireCode(processCode);
            modelType = Objects.requireNonNull(modelType, "modelType");
            definitionBytes = copyDefinitionBytes(definitionBytes);
            definitionDigest = DurableIdentifiers.requireSha256(definitionDigest, "definitionDigest");
            if (!definitionDigest.equals(ProcessDefinitionDigest.compute(modelType, processCode, definitionBytes))) {
                throw new IllegalArgumentException("definitionDigest does not match the stored Process definition");
            }
            registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        }

        @Override
        public byte[] definitionBytes() {
            return definitionBytes.clone();
        }
    }

    private static byte[] copyDefinitionBytes(byte[] source) {
        byte[] bytes = Objects.requireNonNull(source, "definitionBytes").clone();
        if (bytes.length == 0 || bytes.length > MAX_DEFINITION_BYTES) {
            throw new IllegalArgumentException("definitionBytes must contain 1.." + MAX_DEFINITION_BYTES + " bytes");
        }
        return bytes;
    }

    private static void requireVersionAttribution(String namespace, String processCode,
            ProcessRef.Version processVersion) {
        if (processVersion != null
                && (!namespace.equals(processVersion.namespace()) || !processCode.equals(processVersion.code()))) {
            throw new IllegalArgumentException("processVersion must identify the same process");
        }
    }

    /**
     * Versioned Engine-owned persisted payload.
     *
     * <p>The compact header is a format-evolution anchor only. It is not codec
     * identity, Process identity, Worker routing, or an application extension
     * point. New instances wrap raw payload bytes in the current format; Store
     * implementations reconstruct validated values with {@link #fromStoredBytes(byte[])}.
     */
    final class Envelope {
        private static final byte[] MAGIC = {'C', 'F', 'D'};
        private static final int CURRENT_FORMAT_VERSION = 1;
        private static final int HEADER_BYTES = MAGIC.length + 1;
        private final byte[] bytes;

        /**
         * Wraps one raw Engine-owned payload in the current persisted format.
         *
         * @param payload raw Engine-owned payload
         */
        public Envelope(byte[] payload) {
            byte[] value = copyPayload(payload);
            this.bytes = new byte[HEADER_BYTES + value.length];
            System.arraycopy(MAGIC, 0, bytes, 0, MAGIC.length);
            bytes[MAGIC.length] = (byte) CURRENT_FORMAT_VERSION;
            System.arraycopy(value, 0, bytes, HEADER_BYTES, value.length);
        }

        /**
         * Reconstructs and validates bytes read from authoritative storage.
         *
         * @param storedBytes complete persisted envelope bytes
         * @return validated envelope
         */
        public static Envelope fromStoredBytes(byte[] storedBytes) {
            byte[] stored = requireStoredEnvelope(storedBytes);
            return new Envelope(Arrays.copyOfRange(stored, HEADER_BYTES, stored.length));
        }

        /**
         * Returns the complete versioned bytes written to storage.
         *
         * @return defensive copy of persisted bytes
         */
        public byte[] bytes() {
            return bytes.clone();
        }

        /**
         * Returns the raw payload after validating the persisted format header.
         *
         * @return defensive copy of raw payload bytes
         */
        public byte[] payload() {
            return Arrays.copyOfRange(bytes, HEADER_BYTES, bytes.length);
        }

        /**
         * Returns the persisted format version, never an execution identity.
         *
         * @return persisted envelope format version
         */
        public int formatVersion() {
            return Byte.toUnsignedInt(bytes[MAGIC.length]);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Envelope that && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "Envelope[formatVersion=" + formatVersion() + ", payloadBytes=" + (bytes.length - HEADER_BYTES) + ']';
        }

        private static byte[] copyPayload(byte[] source) {
            byte[] payload = Objects.requireNonNull(source, "payload").clone();
            if (payload.length == 0 || payload.length > MAX_ENVELOPE_BYTES - HEADER_BYTES) {
                throw new IllegalArgumentException(
                        "payload must contain 1.." + (MAX_ENVELOPE_BYTES - HEADER_BYTES) + " bytes");
            }
            return payload;
        }

        private static byte[] requireStoredEnvelope(byte[] source) {
            byte[] stored = copyBoundedBytes(source, "stored envelope");
            if (stored.length <= HEADER_BYTES || stored[0] != MAGIC[0] || stored[1] != MAGIC[1] || stored[2] != MAGIC[2]) {
                throw new IllegalArgumentException("stored envelope has an invalid CompileFlow Durable header");
            }
            int version = Byte.toUnsignedInt(stored[MAGIC.length]);
            if (version != CURRENT_FORMAT_VERSION) {
                throw new IllegalArgumentException("stored envelope format version is not supported: " + version);
            }
            return stored;
        }
    }

    /**
     * Immutable provider value for the ProcessRuntimeDemandQuery contract.
     */
    record ProcessRuntimeDemandQuery(UUID afterProcessId, int limit) {
        public ProcessRuntimeDemandQuery {
            limit = DurableNumbers.requireRange(limit, 1, 1000, "limit");
        }
    }

    /**
     * Immutable provider value for the ProcessRuntimeDemandPage contract.
     */
    record ProcessRuntimeDemandPage(List<UUID> processIds, UUID nextProcessId) {
        public ProcessRuntimeDemandPage {
            processIds = List.copyOf(Objects.requireNonNull(processIds, "processIds"));
            if (new LinkedHashSet<>(processIds).size() != processIds.size()) {
                throw new IllegalArgumentException("processIds must not contain duplicates");
            }
            if (nextProcessId != null
                    && (processIds.isEmpty() || !nextProcessId.equals(processIds.get(processIds.size() - 1)))) {
                throw new IllegalArgumentException("nextProcessId must identify the last Process in a non-empty page");
            }
        }
    }

    /**
     * Atomically creates a Run and retains every exact Process required by its continuation.
     */
    record NewRun(ProcessRunId runId, RunProcess rootProcess, Set<UUID> recoveryProcessIds, Envelope continuation,
            Envelope admissionFact) {
        private static final int MAX_RECOVERY_PROCESS_IDS = 4_097;

        public NewRun {
            runId = Objects.requireNonNull(runId, "runId");
            rootProcess = Objects.requireNonNull(rootProcess, "rootProcess");
            recoveryProcessIds = Set.copyOf(Objects.requireNonNull(recoveryProcessIds, "recoveryProcessIds"));
            if (!recoveryProcessIds.contains(rootProcess.processId())) {
                throw new IllegalArgumentException("recoveryProcessIds must contain the root Process");
            }
            if (recoveryProcessIds.size() > MAX_RECOVERY_PROCESS_IDS) {
                throw new IllegalArgumentException(
                        "recoveryProcessIds must contain at most " + MAX_RECOVERY_PROCESS_IDS + " Processes");
            }
            continuation = Objects.requireNonNull(continuation, "continuation");
        }
    }

    /**
     * Immutable provider value for the WaitCompletion contract.
     */
    record WaitCompletion(ProcessRunId runId, String tokenDigest, Envelope result) {
        public WaitCompletion {
            runId = Objects.requireNonNull(runId, "runId");
            tokenDigest = DurableIdentifiers.requireSha256(tokenDigest, "tokenDigest");
            result = Objects.requireNonNull(result, "result");
        }
    }

    /**
     * Immutable provider value for the WaitTarget contract.
     */
    record WaitTarget(ProcessRunId runId, RunProcess rootProcess, UUID processId, String tokenDigest) {
        public WaitTarget {
            runId = Objects.requireNonNull(runId, "runId");
            rootProcess = Objects.requireNonNull(rootProcess, "rootProcess");
            processId = Objects.requireNonNull(processId, "processId");
            tokenDigest = DurableIdentifiers.requireSha256(tokenDigest, "tokenDigest");
        }
    }

    /**
     * Immutable provider value for the CancelCommand contract.
     */
    record CancelCommand(ProcessRunId runId) {
        public CancelCommand {
            runId = Objects.requireNonNull(runId, "runId");
        }
    }

    /**
     * Immutable provider value for the RunResultProjection contract.
     */
    record RunResultProjection(ProcessRunId runId, RunProcess rootProcess, ProcessRunStatus status, Envelope result,
            String failureCode, String failureMessage, Instant completedAt) {
        public RunResultProjection {
            runId = Objects.requireNonNull(runId, "runId");
            rootProcess = Objects.requireNonNull(rootProcess, "rootProcess");
            status = Objects.requireNonNull(status, "status");
            failureCode = DurableIdentifiers.optionalIdentity(failureCode, "failureCode", 128);
            failureMessage = DurableIdentifiers.optionalHumanText(failureMessage, "failureMessage", 2_048);
            if (status.isTerminal() != (completedAt != null)) {
                throw new IllegalArgumentException("completedAt must be present exactly for terminal status");
            }
            if ((status == ProcessRunStatus.SUCCEEDED) != (result != null)) {
                throw new IllegalArgumentException("result must be present exactly for SUCCEEDED");
            }
            if ((status == ProcessRunStatus.FAILED) != (failureCode != null)) {
                throw new IllegalArgumentException("failureCode must be present exactly for FAILED");
            }
            if ((status == ProcessRunStatus.FAILED) != (failureMessage != null)) {
                throw new IllegalArgumentException("failureMessage must be present exactly for FAILED");
            }
        }
    }

    /**
     * Immutable provider value for the ControlCommand contract.
     */
    record ControlCommand(ProcessRunId runId, long expectedControlRevision, ControlOperation operation,
            AuditPrincipal actor, String reason, String auditContextId) {
        public ControlCommand {
            runId = Objects.requireNonNull(runId, "runId");
            expectedControlRevision = DurableNumbers.requireNonNegative(expectedControlRevision,
                    "expectedControlRevision");
            operation = Objects.requireNonNull(operation, "operation");
            actor = Objects.requireNonNull(actor, "actor");
            reason = DurableIdentifiers.requireHumanText(reason, "reason", DurableIdentifiers.MAX_REASON_CHARACTERS);
            auditContextId = DurableIdentifiers.optionalIdentity(auditContextId, "auditContextId",
                    DurableIdentifiers.MAX_AUDIT_CONTEXT_ID_CHARACTERS);
        }
    }

    /**
     * Closed provider vocabulary for the ControlOperation contract.
     */
    enum ControlOperation {
        PAUSE,
        RESUME
    }

    /**
     * Immutable provider value for the EffectResolution contract.
     */
    record EffectResolution(ProcessRunId runId, UUID effectId, long expectedReviewRevision,
            EffectResolutionDecision decision, Envelope result, String reason, AuditPrincipal actor,
            String auditContextId) {
        public EffectResolution {
            runId = Objects.requireNonNull(runId, "runId");
            effectId = Objects.requireNonNull(effectId, "effectId");
            expectedReviewRevision = DurableNumbers.requireNonNegative(expectedReviewRevision, "expectedReviewRevision");
            decision = Objects.requireNonNull(decision, "decision");
            if ((decision == EffectResolutionDecision.CONFIRM_SUCCEEDED) != (result != null)) {
                throw new IllegalArgumentException("result is present exactly for CONFIRM_SUCCEEDED");
            }
            reason = DurableIdentifiers.requireHumanText(reason, "reason", DurableIdentifiers.MAX_REASON_CHARACTERS);
            actor = Objects.requireNonNull(actor, "actor");
            auditContextId = DurableIdentifiers.optionalIdentity(auditContextId, "auditContextId",
                    DurableIdentifiers.MAX_AUDIT_CONTEXT_ID_CHARACTERS);
        }
    }

    /**
     * Immutable provider value for the EffectTarget contract.
     */
    record EffectTarget(ProcessRunId runId, UUID effectId, RunProcess rootProcess, UUID processId, String elementId) {
        public EffectTarget {
            runId = Objects.requireNonNull(runId, "runId");
            effectId = Objects.requireNonNull(effectId, "effectId");
            rootProcess = Objects.requireNonNull(rootProcess, "rootProcess");
            processId = Objects.requireNonNull(processId, "processId");
            elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
        }
    }

    /**
     * Immutable provider value for the OutboxResolution contract.
     */
    record OutboxResolution(UUID eventId, long expectedRevision, OutboxResolutionDecision decision, AuditPrincipal actor,
            String reason, String auditContextId) {
        public OutboxResolution {
            eventId = Objects.requireNonNull(eventId, "eventId");
            expectedRevision = DurableNumbers.requireNonNegative(expectedRevision, "expectedRevision");
            decision = Objects.requireNonNull(decision, "decision");
            actor = Objects.requireNonNull(actor, "actor");
            reason = DurableIdentifiers.requireHumanText(reason, "reason", DurableIdentifiers.MAX_REASON_CHARACTERS);
            auditContextId = DurableIdentifiers.optionalIdentity(auditContextId, "auditContextId",
                    DurableIdentifiers.MAX_AUDIT_CONTEXT_ID_CHARACTERS);
        }
    }

    /**
     * Closed provider vocabulary for the OutboxResolutionDecision contract.
     */
    enum OutboxResolutionDecision {
        RETRY,
        ABANDON
    }

    /**
     * Immutable provider value for the RunQuery contract.
     */
    record RunQuery(String namespace, String code, Set<ProcessRunStatus> statuses, Instant beforeCreatedAt,
            ProcessRunId beforeRunId, int limit) {
        public RunQuery {
            if (namespace != null) {
                namespace = ProcessIdentifiers.requireNamespace(namespace);
            }
            if (code != null) {
                code = ProcessIdentifiers.requireCode(code);
            }
            statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
            if ((beforeCreatedAt == null) != (beforeRunId == null)) {
                throw new IllegalArgumentException("Run cursor is incomplete");
            }
            limit = DurableNumbers.requireRange(limit, 1, 200, "limit");
        }
    }

    /**
     * Immutable provider value for the RunPageCursor contract.
     */
    record RunPageCursor(Instant createdAt, ProcessRunId runId) {
        public RunPageCursor {
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            runId = Objects.requireNonNull(runId, "runId");
        }
    }

    /**
     * Immutable provider value for the RunPage contract.
     */
    record RunPage(List<ProcessRun> items, Instant nextCreatedAt, ProcessRunId nextRunId) {
        public RunPage {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if ((nextCreatedAt == null) != (nextRunId == null)) {
                throw new IllegalArgumentException("Run page cursor is incomplete");
            }
        }
    }

    /**
     * Immutable provider value for the ActiveWorkQuery contract.
     */
    record ActiveWorkQuery(ProcessRunId runId, long afterOccurrenceSequence, int limit) {
        public ActiveWorkQuery {
            runId = Objects.requireNonNull(runId, "runId");
            afterOccurrenceSequence = DurableNumbers.requireNonNegative(afterOccurrenceSequence,
                    "afterOccurrenceSequence");
            limit = DurableNumbers.requireRange(limit, 1, 200, "limit");
        }
    }

    /**
     * Immutable provider value for the ActiveWorkPage contract.
     */
    record ActiveWorkPage(List<ActiveWork> items, Long nextSequence) {
        public ActiveWorkPage {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (nextSequence != null) {
                DurableNumbers.requirePositive(nextSequence, "nextSequence");
            }
        }
    }

    /**
     * Immutable provider value for the TimelineQuery contract.
     */
    record TimelineQuery(ProcessRunId runId, long snapshotSequence, long afterSequence, int limit) {
        public TimelineQuery {
            runId = Objects.requireNonNull(runId, "runId");
            snapshotSequence = DurableNumbers.requireNonNegative(snapshotSequence, "snapshotSequence");
            afterSequence = DurableNumbers.requireNonNegative(afterSequence, "afterSequence");
            if (snapshotSequence > 0 && afterSequence > snapshotSequence) {
                throw new IllegalArgumentException("afterSequence exceeds snapshotSequence");
            }
            limit = DurableNumbers.requireRange(limit, 1, 200, "limit");
        }
    }

    /**
     * Immutable provider value for the TimelinePageCursor contract.
     */
    record TimelinePageCursor(long snapshotSequence, long lastSequence) {
        public TimelinePageCursor {
            snapshotSequence = DurableNumbers.requirePositive(snapshotSequence, "snapshotSequence");
            lastSequence = DurableNumbers.requirePositive(lastSequence, "lastSequence");
            if (lastSequence > snapshotSequence) {
                throw new IllegalArgumentException("lastSequence exceeds snapshotSequence");
            }
        }
    }

    /**
     * Immutable provider value for the JournalFact contract.
     */
    record JournalFact(long sequence, String type, Envelope fact, Instant occurredAt) {
        public JournalFact {
            sequence = DurableNumbers.requirePositive(sequence, "sequence");
            type = DurableIdentifiers.requireIdentity(type, "type", 64);
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Immutable provider value for the TimelinePage contract.
     */
    record TimelinePage(long snapshotSequence, List<JournalFact> items, Long nextSequence) {
        public TimelinePage {
            snapshotSequence = DurableNumbers.requireNonNegative(snapshotSequence, "snapshotSequence");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (nextSequence != null) {
                DurableNumbers.requirePositive(nextSequence, "nextSequence");
            }
        }
    }

    /**
     * Immutable provider value for the OutboxQuery contract.
     */
    record OutboxQuery(String namespace, String code, Set<OutboxEventStatus> statuses, Instant beforeCreatedAt,
            UUID beforeEventId, int limit) {
        public OutboxQuery {
            if (namespace != null) {
                namespace = ProcessIdentifiers.requireNamespace(namespace);
            }
            if (code != null) {
                code = ProcessIdentifiers.requireCode(code);
            }
            if (code != null && namespace == null) {
                throw new IllegalArgumentException("code requires namespace");
            }
            statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
            if ((beforeCreatedAt == null) != (beforeEventId == null)) {
                throw new IllegalArgumentException("Outbox cursor is incomplete");
            }
            limit = DurableNumbers.requireRange(limit, 1, 200, "limit");
        }
    }

    /**
     * Immutable provider value for the OutboxPageCursor contract.
     */
    record OutboxPageCursor(Instant createdAt, UUID eventId) {
        public OutboxPageCursor {
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            eventId = Objects.requireNonNull(eventId, "eventId");
        }
    }

    /**
     * Immutable provider value for the OutboxPage contract.
     */
    record OutboxPage(List<OutboxEvent> items, Instant nextCreatedAt, UUID nextEventId) {
        public OutboxPage {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if ((nextCreatedAt == null) != (nextEventId == null)) {
                throw new IllegalArgumentException("Outbox page cursor is incomplete");
            }
        }
    }

    /**
     * Immutable provider value for the RunClaimRequest contract.
     *
     * <p>The set is the worker's loaded root-Process capability snapshot. A Store must claim a
     * Run only when its root Process is in this set. Processes in the Run recovery set are
     * retention references; the turn runtime may load them lazily when a continuation enters
     * one.</p>
     */
    record RunClaimRequest(String workerId, Set<UUID> readyRootProcessIds, Duration leaseDuration) {
        private static final int MAX_READY_PROCESS_IDS = 10_000;

        public RunClaimRequest {
            workerId = DurableIdentifiers.requireIdentity(workerId, "workerId", 128);
            readyRootProcessIds = Set.copyOf(Objects.requireNonNull(readyRootProcessIds, "readyRootProcessIds"));
            if (readyRootProcessIds.isEmpty()) {
                throw new IllegalArgumentException("readyRootProcessIds must not be empty");
            }
            if (readyRootProcessIds.size() > MAX_READY_PROCESS_IDS) {
                throw new IllegalArgumentException(
                        "readyRootProcessIds must contain at most " + MAX_READY_PROCESS_IDS + " Processes");
            }
            readyRootProcessIds.forEach(processId -> Objects.requireNonNull(processId,
                    "readyRootProcessIds contains null"));
            leaseDuration = requirePositive(leaseDuration, "leaseDuration", Duration.ofHours(1));
        }
    }

    /**
     * Immutable provider value for the RunLease contract.
     */
    record RunLease(ProcessRunId runId, UUID token) {
        public RunLease {
            runId = Objects.requireNonNull(runId, "runId");
            token = Objects.requireNonNull(token, "token");
        }
    }

    /**
     * Immutable provider value for the RunClaim contract.
     */
    record RunClaim(RunLease lease, RunProcess rootProcess, Envelope continuation, long occurrenceSequence,
            List<OccurrenceResult> occurrenceResults, boolean cancelRequested, Instant leaseUntil) {
        public RunClaim {
            lease = Objects.requireNonNull(lease, "lease");
            rootProcess = Objects.requireNonNull(rootProcess, "rootProcess");
            continuation = Objects.requireNonNull(continuation, "continuation");
            occurrenceSequence = DurableNumbers.requireNonNegative(occurrenceSequence, "occurrenceSequence");
            occurrenceResults = List.copyOf(Objects.requireNonNull(occurrenceResults, "occurrenceResults"));
            if (occurrenceResults.size() > 256) {
                throw new IllegalArgumentException("One Run claim exposes at most 256 occurrence results");
            }
            if (occurrenceResults
                .stream()
                .map(OccurrenceResult::occurrence)
                .collect(Collectors.toUnmodifiableSet())
                .size() != occurrenceResults.size()) {
                throw new IllegalArgumentException("occurrenceResults must have unique identities");
            }
            long previous = 0;
            for (OccurrenceResult result : occurrenceResults) {
                if (result.occurrenceSequence() <= previous || result.occurrenceSequence() > occurrenceSequence) {
                    throw new IllegalArgumentException(
                            "occurrenceResults must be ordered unique issues within the Run high-water mark");
                }
                previous = result.occurrenceSequence();
            }
            leaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil");
        }
    }

    /**
     * Closed provider vocabulary for the OccurrenceKind contract.
     */
    enum OccurrenceKind {
        WAIT,
        TIMER,
        EFFECT
    }

    /**
     * Closed provider vocabulary for the WaitResolution contract.
     */
    enum WaitResolution {
        COMPLETED,
        EXPIRED
    }

    /**
     * Immutable provider value for the OccurrenceKey contract.
     */
    record OccurrenceKey(OccurrenceKind kind, UUID id) {
        public OccurrenceKey {
            kind = Objects.requireNonNull(kind, "kind");
            id = Objects.requireNonNull(id, "id");
        }
    }

    /**
     * Closed provider outcome contract for OccurrenceResult.
     */
    sealed interface OccurrenceResult permits WaitResult, TimerResult, EffectResult {
        OccurrenceKey occurrence();

        long occurrenceSequence();

        UUID processId();

        long processInvocationId();

        String frontierId();

        String elementId();

        Envelope result();
    }

    /**
     * Immutable provider value for the WaitResult contract.
     */
    record WaitResult(OccurrenceKey occurrence, long occurrenceSequence, UUID processId, long processInvocationId,
            String frontierId, String elementId, String event, WaitResolution resolution, Envelope result,
            Instant deadline, Instant resolvedAt) implements OccurrenceResult {
        public WaitResult {
            occurrence = requireOccurrence(occurrence, OccurrenceKind.WAIT);
            occurrenceSequence = positiveOccurrence(occurrenceSequence);
            processId = Objects.requireNonNull(processId, "processId");
            processInvocationId = DurableNumbers.requireNonNegative(processInvocationId, "processInvocationId");
            frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
            elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
            event = ProcessIdentifiers.optionalEvent(event);
            resolution = Objects.requireNonNull(resolution, "resolution");
            result = Objects.requireNonNull(result, "result");
            if ((resolution == WaitResolution.EXPIRED) != (deadline != null)) {
                throw new IllegalArgumentException("Only an expired Wait result requires its deadline");
            }
            resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt");
            if (deadline != null && resolvedAt.isBefore(deadline)) {
                throw new IllegalArgumentException("Wait result cannot precede its deadline");
            }
        }
    }

    /**
     * Immutable provider value for the TimerResult contract.
     */
    record TimerResult(OccurrenceKey occurrence, long occurrenceSequence, UUID processId, long processInvocationId,
            String frontierId, String elementId, Envelope result, Instant scheduledAt, Instant dueAt,
            Instant resolvedAt) implements OccurrenceResult {
        public TimerResult {
            occurrence = requireOccurrence(occurrence, OccurrenceKind.TIMER);
            occurrenceSequence = positiveOccurrence(occurrenceSequence);
            processId = Objects.requireNonNull(processId, "processId");
            processInvocationId = DurableNumbers.requireNonNegative(processInvocationId, "processInvocationId");
            frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
            elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
            result = Objects.requireNonNull(result, "result");
            scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt");
            dueAt = Objects.requireNonNull(dueAt, "dueAt");
            resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt");
            if (dueAt.isBefore(scheduledAt)) {
                throw new IllegalArgumentException("Timer dueAt must not be before scheduledAt");
            }
            if (resolvedAt.isBefore(dueAt)) {
                throw new IllegalArgumentException("Timer resolvedAt must not be before dueAt");
            }
        }
    }

    /**
     * Immutable provider value for the EffectResult contract.
     */
    record EffectResult(OccurrenceKey occurrence, long occurrenceSequence, UUID processId, long processInvocationId,
            String frontierId, String elementId, Envelope result, Instant resolvedAt) implements OccurrenceResult {
        public EffectResult {
            occurrence = requireOccurrence(occurrence, OccurrenceKind.EFFECT);
            occurrenceSequence = positiveOccurrence(occurrenceSequence);
            processId = Objects.requireNonNull(processId, "processId");
            processInvocationId = DurableNumbers.requireNonNegative(processInvocationId, "processInvocationId");
            frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
            elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
            result = Objects.requireNonNull(result, "result");
            resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt");
        }
    }

    /**
     * Closed provider outcome contract for OccurrenceCommit.
     */
    sealed interface OccurrenceCommit permits WaitCommit, TimerCommit, EffectCommit {
        OccurrenceKey occurrence();

        long occurrenceSequence();

        UUID processId();

        long processInvocationId();

        String frontierId();

        String elementId();
    }

    /**
     * Immutable provider value for the WaitCommit contract.
     */
    record WaitCommit(OccurrenceKey occurrence, long occurrenceSequence, UUID processId, long processInvocationId,
            String frontierId, String elementId, String event, String tokenDigest, Duration deadlineAfter,
            Envelope outboxPayload) implements OccurrenceCommit {
        public WaitCommit {
            occurrence = requireOccurrence(occurrence, OccurrenceKind.WAIT);
            occurrenceSequence = positiveOccurrence(occurrenceSequence);
            processId = Objects.requireNonNull(processId, "processId");
            processInvocationId = DurableNumbers.requireNonNegative(processInvocationId, "processInvocationId");
            frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
            elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
            event = ProcessIdentifiers.optionalEvent(event);
            tokenDigest = DurableIdentifiers.requireSha256(tokenDigest, "tokenDigest");
            if (deadlineAfter != null) {
                deadlineAfter = DurableNumbers.requireDurationMillis(deadlineAfter, Duration.ofDays(36500),
                        "deadlineAfter");
            }
            outboxPayload = Objects.requireNonNull(outboxPayload, "outboxPayload");
        }
    }

    /**
     * Immutable provider value for the TimerCommit contract.
     */
    record TimerCommit(OccurrenceKey occurrence, long occurrenceSequence, UUID processId, long processInvocationId,
            String frontierId, String elementId, Duration delay, Instant dueAt) implements OccurrenceCommit {
        public TimerCommit {
            occurrence = requireOccurrence(occurrence, OccurrenceKind.TIMER);
            occurrenceSequence = positiveOccurrence(occurrenceSequence);
            processId = Objects.requireNonNull(processId, "processId");
            processInvocationId = DurableNumbers.requireNonNegative(processInvocationId, "processInvocationId");
            frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
            elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
            if ((delay == null) == (dueAt == null)) {
                throw new IllegalArgumentException("Timer declares exactly delay or dueAt");
            }
            if (delay != null) {
                delay = DurableNumbers.requireDurationMillis(delay, Duration.ofDays(36500), "Timer delay");
            }
        }
    }

    /**
     * Immutable provider value for the EffectCommit contract.
     */
    record EffectCommit(OccurrenceKey occurrence, long occurrenceSequence, UUID processId, long processInvocationId,
            String frontierId, String elementId, EffectRecoveryPlan recoveryPlan, Envelope input)
            implements OccurrenceCommit {
        public EffectCommit {
            occurrence = requireOccurrence(occurrence, OccurrenceKind.EFFECT);
            occurrenceSequence = positiveOccurrence(occurrenceSequence);
            processId = Objects.requireNonNull(processId, "processId");
            processInvocationId = DurableNumbers.requireNonNegative(processInvocationId, "processInvocationId");
            frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
            elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
            recoveryPlan = Objects.requireNonNull(recoveryPlan, "recoveryPlan");
            input = Objects.requireNonNull(input, "input");
        }
    }

    /**
     * One complete, token-fenced Machine Turn transaction.
     *
     * <p>Resolved occurrence consumption and new occurrence issue are committed with the next Run
     * state. The current sequential Machine emits at most one issue, while the Store protocol is
     * already collection-shaped for multi-frontier execution.</p>
     */
    record TurnCommit(List<OccurrenceKey> consumedOccurrences, List<OccurrenceCommit> issuedOccurrences,
            TurnState state) {
        public TurnCommit {
            consumedOccurrences = List.copyOf(Objects.requireNonNull(consumedOccurrences, "consumedOccurrences"));
            issuedOccurrences = List.copyOf(Objects.requireNonNull(issuedOccurrences, "issuedOccurrences"));
            state = Objects.requireNonNull(state, "state");
            if (consumedOccurrences.size() > 256 || issuedOccurrences.size() > 256) {
                throw new IllegalArgumentException("One Turn supports at most 256 consumed and issued occurrences");
            }
            Set<OccurrenceKey> consumedKeys = Set.copyOf(consumedOccurrences);
            if (consumedKeys.size() != consumedOccurrences.size()) {
                throw new IllegalArgumentException("consumedOccurrences must be unique");
            }
            Set<OccurrenceKey> issuedKeys =
                    issuedOccurrences
                .stream()
                .map(OccurrenceCommit::occurrence)
                .collect(Collectors.toUnmodifiableSet());
            if (issuedKeys.size() != issuedOccurrences.size()) {
                throw new IllegalArgumentException("issuedOccurrences must have unique identities");
            }
            if (issuedOccurrences
                .stream()
                .map(OccurrenceCommit::frontierId)
                .collect(Collectors.toUnmodifiableSet())
                .size() != issuedOccurrences.size()) {
                throw new IllegalArgumentException("One Turn may issue at most one occurrence per frontier");
            }
            if (issuedKeys.stream().anyMatch(consumedKeys::contains)) {
                throw new IllegalArgumentException("An occurrence cannot be consumed and issued in one Turn");
            }
        }
    }

    /**
     * Closed provider outcome contract for TurnState.
     */
    sealed interface TurnState permits RunnableTurn, WaitingTurn, SucceededTurn, FailedTurn {
    }

    /**
     * Immutable provider value for the RunnableTurn contract.
     */
    record RunnableTurn(Envelope continuation) implements TurnState {
        public RunnableTurn {
            continuation = Objects.requireNonNull(continuation, "continuation");
        }
    }

    /**
     * Immutable provider value for the WaitingTurn contract.
     */
    record WaitingTurn(Envelope continuation) implements TurnState {
        public WaitingTurn {
            continuation = Objects.requireNonNull(continuation, "continuation");
        }
    }

    /**
     * Immutable provider value for the SucceededTurn contract.
     */
    record SucceededTurn(Envelope result) implements TurnState {
        public SucceededTurn {
            result = Objects.requireNonNull(result, "result");
        }
    }

    /**
     * Immutable provider value for the FailedTurn contract.
     */
    record FailedTurn(String code, String message) implements TurnState {
        public FailedTurn {
            code = DurableIdentifiers.requireIdentity(code, "code", 128);
            message = DurableIdentifiers.requireHumanText(message, "message", 2048);
        }
    }

    /**
     * Immutable provider value for the EffectClaimRequest contract.
     */
    record EffectClaimRequest(String workerId, EffectOperation operation, Duration leaseDuration) {
        public EffectClaimRequest {
            workerId = DurableIdentifiers.requireIdentity(workerId, "workerId", 128);
            operation = Objects.requireNonNull(operation, "operation");
            leaseDuration = requirePositive(leaseDuration, "leaseDuration", Duration.ofHours(1));
        }
    }

    /**
     * Closed provider vocabulary for the EffectOperation contract.
     */
    enum EffectOperation {
        DISPATCH,
        RECONCILE
    }

    /**
     * Immutable provider value for the EffectLease contract.
     */
    record EffectLease(ProcessRunId runId, UUID effectId, UUID token) {
        public EffectLease {
            runId = Objects.requireNonNull(runId, "runId");
            effectId = Objects.requireNonNull(effectId, "effectId");
            token = Objects.requireNonNull(token, "token");
        }
    }

    /**
     * Immutable provider value for the EffectClaim contract.
     */
    record EffectClaim(EffectLease lease, ProcessRunId runId, RunProcess rootProcess, UUID processId, String frontierId,
            String elementId, EffectOperation operation, int dispatchAttempts, int reconcileAttempts, Envelope input,
            EffectRecoveryPlan recoveryPlan, Instant createdAt, Instant unknownSince, Instant authorityTime,
            Instant leaseUntil) {
        public EffectClaim {
            lease = Objects.requireNonNull(lease, "lease");
            runId = Objects.requireNonNull(runId, "runId");
            if (!runId.equals(lease.runId())) {
                throw new IllegalArgumentException("Effect lease and claim runId must match");
            }
            rootProcess = Objects.requireNonNull(rootProcess, "rootProcess");
            processId = Objects.requireNonNull(processId, "processId");
            frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
            elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
            operation = Objects.requireNonNull(operation, "operation");
            dispatchAttempts = DurableNumbers.requireRange(dispatchAttempts, 0, Integer.MAX_VALUE, "dispatchAttempts");
            reconcileAttempts = DurableNumbers.requireRange(reconcileAttempts, 0, Integer.MAX_VALUE, "reconcileAttempts");
            if (operation == EffectOperation.DISPATCH && dispatchAttempts == 0
                    || operation == EffectOperation.RECONCILE && reconcileAttempts == 0) {
                throw new IllegalArgumentException("Claimed operation attempt must be positive");
            }
            input = Objects.requireNonNull(input, "input");
            recoveryPlan = Objects.requireNonNull(recoveryPlan, "recoveryPlan");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            if ((operation == EffectOperation.RECONCILE) != (unknownSince != null)) {
                throw new IllegalArgumentException("unknownSince is present exactly for reconciliation claims");
            }
            if (unknownSince != null && unknownSince.isBefore(createdAt)) {
                throw new IllegalArgumentException("unknownSince must not be before createdAt");
            }
            authorityTime = Objects.requireNonNull(authorityTime, "authorityTime");
            leaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil");
        }
    }

    /**
     * Immutable provider value for the OutboxClaimRequest contract.
     */
    record OutboxClaimRequest(String workerId, Duration leaseDuration) {
        public OutboxClaimRequest {
            workerId = DurableIdentifiers.requireIdentity(workerId, "workerId", 128);
            leaseDuration = requirePositive(leaseDuration, "leaseDuration", Duration.ofHours(1));
        }
    }

    /**
     * Immutable provider value for the OutboxLease contract.
     */
    record OutboxLease(ProcessRunId runId, UUID eventId, UUID token) {
        public OutboxLease {
            runId = Objects.requireNonNull(runId, "runId");
            eventId = Objects.requireNonNull(eventId, "eventId");
            token = Objects.requireNonNull(token, "token");
        }
    }

    /**
     * Immutable provider value for the OutboxClaim contract.
     */
    record OutboxClaim(OutboxLease lease, ProcessRunId runId, RunProcess rootProcess, String eventType,
            UUID occurrenceId, Envelope payload, int attempt, Instant leaseUntil) {
        public OutboxClaim {
            lease = Objects.requireNonNull(lease, "lease");
            runId = Objects.requireNonNull(runId, "runId");
            if (!runId.equals(lease.runId())) {
                throw new IllegalArgumentException("Outbox lease and claim runId must match");
            }
            rootProcess = Objects.requireNonNull(rootProcess, "rootProcess");
            eventType = DurableIdentifiers.requireOutboxEventType(eventType);
            boolean occurrenceEvent = "WAIT_COMMITTED".equals(eventType) || "EFFECT_REVIEW_REQUIRED".equals(eventType);
            if (occurrenceEvent != (occurrenceId != null)) {
                throw new IllegalArgumentException("occurrenceId is present exactly for occurrence events");
            }
            payload = Objects.requireNonNull(payload, "payload");
            attempt = DurableNumbers.requireRange(attempt, 1, Integer.MAX_VALUE, "attempt");
            leaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil");
        }
    }

    private static Duration requirePositive(Duration value, String name, Duration maximum) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.compareTo(Duration.ofMillis(1)) < 0 || duration.compareTo(maximum) > 0
                || duration.toNanosPart() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    name + " must be a positive whole-millisecond duration at most " + maximum);
        }
        return duration;
    }

    private static long positiveOccurrence(long value) {
        return DurableNumbers.requirePositive(value, "occurrenceSequence");
    }

    private static OccurrenceKey requireOccurrence(OccurrenceKey occurrence, OccurrenceKind expected) {
        OccurrenceKey value = Objects.requireNonNull(occurrence, "occurrence");
        if (value.kind() != expected) {
            throw new IllegalArgumentException("Occurrence kind must be " + expected);
        }
        return value;
    }

    private static byte[] copyBoundedBytes(byte[] source, String name) {
        byte[] bytes = Objects.requireNonNull(source, name).clone();
        if (bytes.length == 0 || bytes.length > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException(name + " must contain 1.." + MAX_ENVELOPE_BYTES + " bytes");
        }
        return bytes;
    }
}
