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
package com.alibaba.compileflow.durable.mysql;

import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.api.command.EffectResolutionDecision;
import com.alibaba.compileflow.durable.api.command.OutboxResolutionDecision;
import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.api.model.ActiveEffect;
import com.alibaba.compileflow.durable.api.model.ActiveTimer;
import com.alibaba.compileflow.durable.api.model.ActiveWait;
import com.alibaba.compileflow.durable.api.model.ActiveWorkSummary;
import com.alibaba.compileflow.durable.api.model.ActiveWork;
import com.alibaba.compileflow.durable.api.model.EffectReadinessCode;
import com.alibaba.compileflow.durable.api.model.EffectExecutionStatus;
import com.alibaba.compileflow.durable.api.model.OutboxEventStatus;
import com.alibaba.compileflow.durable.api.model.OutboxEvent;
import com.alibaba.compileflow.durable.api.model.AuditPrincipal;
import com.alibaba.compileflow.durable.api.model.ProcessRunControlState;
import com.alibaba.compileflow.durable.api.model.ProcessRunControl;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunRetryState;
import com.alibaba.compileflow.durable.api.model.ProcessRunStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.RunRetryCode;
import com.alibaba.compileflow.durable.spi.store.DurableLeaseStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.TimeZone;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * MySQL implementation of the seven-table Durable Kernel.
 *
 * @author yusu
 */
public final class MySqlDurableStore implements DurableStore {
    private static final int MAX_OUTSTANDING_OCCURRENCES = 4096;
    private static final byte[] EMPTY_ENVELOPE = new Envelope("{}".getBytes(StandardCharsets.UTF_8)).bytes();
    private static final String RUN_VIEW =
            """
        SELECT r.run_id, r.process_id, r.process_code,
               r.namespace, r.process_version,
               r.status, r.control_state, r.control_revision,
               r.available_at, r.turn_fault_streak,
               r.retry_code, r.retry_observed_at,
               r.failure_code, r.failure_message, r.cancel_requested_at,
               r.created_at, r.updated_at, r.completed_at,
               COALESCE(w.external_waits, 0) AS active_external_waits,
               COALESCE(w.timers, 0) AS active_timers,
               COALESCE(e.effects, 0) AS active_effects,
               COALESCE(e.running_effects, 0) AS running_effects,
               COALESCE(e.unknown_effects, 0) AS unknown_effects,
               COALESCE(e.review_required_effects, 0) AS review_required_effects
          FROM cf_durable_run r
          LEFT JOIN (
              SELECT run_id,
                     SUM(kind = 'EXTERNAL') AS external_waits,
                     SUM(kind = 'TIMER') AS timers
                FROM cf_durable_wait
               WHERE status = 'ACTIVE'
               GROUP BY run_id
          ) w ON w.run_id = r.run_id
          LEFT JOIN (
              SELECT run_id, count(*) AS effects,
                     SUM(status = 'RUNNING') AS running_effects,
                     SUM(status = 'UNKNOWN') AS unknown_effects,
                     SUM(review_required_at IS NOT NULL) AS review_required_effects
                FROM cf_durable_effect
               WHERE status IN ('PENDING', 'RUNNING', 'UNKNOWN')
               GROUP BY run_id
          ) e ON e.run_id = r.run_id
        """;
    private final MySqlTransactionExecutor transactions;

    public MySqlDurableStore(DataSource dataSource) {
        transactions = new MySqlTransactionExecutor(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public StoredProcess registerProcess(ProcessRegistration registration) {
        ProcessRegistration value = Objects.requireNonNull(registration, "registration");
        return transactions.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                INSERT INTO cf_durable_process(
                    process_id, process_code, model_type, definition_bytes, definition_digest)
                VALUES (?, ?, ?, ?, ?)
                """)) {
                setProviderObject(statement, 1, value.processId());
                statement.setString(2, value.processCode());
                statement.setString(3, value.modelType().name());
                statement.setBytes(4, value.definitionBytes());
                statement.setString(5, value.definitionDigest());
                try {
                    statement.executeUpdate();
                } catch (SQLException failure) {
                    if (!isDuplicateEntry(failure) || selectProcess(connection, value.processId()).isEmpty()) {
                        throw failure;
                    }
                }
            }
            StoredProcess stored = selectProcess(connection, value.processId())
                .orElseThrow(() -> new IllegalStateException("Stored Process registration disappeared"));
            if (!stored.processCode().equals(value.processCode()) || stored.modelType() != value.modelType()
                    || !Arrays.equals(stored.definitionBytes(), value.definitionBytes())
                    || !stored.definitionDigest().equals(value.definitionDigest())) {
                throw error(DurableErrorCode.PROCESS_IDENTITY_MISMATCH,
                        "Process ID is already registered with different semantics");
            }
            return stored;
        });
    }

    @Override
    public Optional<StoredProcess> findProcess(UUID processId) {
        UUID id = Objects.requireNonNull(processId, "processId");
        return transactions.withConnection(connection -> selectProcess(connection, id));
    }

    @Override
    public ProcessRuntimeDemandPage listProcessRuntimeDemand(ProcessRuntimeDemandQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.withConnection(connection -> {
            String cursor = query.afterProcessId() == null ? "" : "WHERE demand.process_id > ?";
            String sql = """
                SELECT demand.process_id
                  FROM (
                        SELECT r.process_id
                          FROM cf_durable_run r
                         WHERE r.status IN ('RUNNABLE', 'RUNNING')
                           AND r.control_state = 'ACTIVE' AND r.cancel_requested_at IS NULL
                        UNION
                        SELECT e.process_id
                          FROM cf_durable_effect e
                          JOIN cf_durable_run r ON r.run_id = e.run_id
                         WHERE e.status IN ('PENDING', 'RUNNING', 'UNKNOWN')
                           AND r.status IN ('RUNNABLE', 'RUNNING', 'WAITING')
                           AND (e.status <> 'PENDING'
                                OR (r.control_state = 'ACTIVE' AND r.cancel_requested_at IS NULL))
                       ) demand
                  %s
                 ORDER BY demand.process_id
                 LIMIT ?
                """
                .formatted(cursor);
            List<UUID> items = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(sql)) {
                int index = 1;
                if (query.afterProcessId() != null) {
                    setProviderObject(select, index++, query.afterProcessId());
                }
                select.setInt(index, query.limit() + 1);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        items.add(providerUuid(rows, "process_id"));
                    }
                }
            }
            UUID next = null;
            if (items.size() > query.limit()) {
                items.remove(items.size() - 1);
                next = items.get(items.size() - 1);
            }
            return new ProcessRuntimeDemandPage(items, next);
        });
    }

    @Override
    public ProcessRun start(NewRun command) {
        NewRun value = Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> {
            List<UUID> recoveryProcessIds = new ArrayList<>(value.recoveryProcessIds());
            Collections.sort(recoveryProcessIds);
            RunProcess rootProcess = value.rootProcess();
            StoredProcess stored = lockProcessesForStart(connection, rootProcess.processId(), recoveryProcessIds);
            if (!stored.processCode().equals(rootProcess.processCode())) {
                throw error(DurableErrorCode.PROCESS_IDENTITY_MISMATCH, "Run Process does not match the stored Process");
            }
            UUID runId = uuid(value.runId());
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                INSERT INTO cf_durable_run(
                    run_id, process_id, process_code, namespace, process_version,
                    continuation_envelope)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
                setProviderObject(statement, 1, runId);
                setProviderObject(statement, 2, rootProcess.processId());
                statement.setString(3, rootProcess.processCode());
                bindRunAttribution(statement, 4, rootProcess.namespace(), rootProcess.processVersion());
                statement.setBytes(6, value.continuation().bytes());
                try {
                    statement.executeUpdate();
                } catch (SQLException failure) {
                    if (!isDuplicateEntry(failure)) {
                        throw failure;
                    }
                    throw error(DurableErrorCode.RUN_ALREADY_EXISTS, "Durable Run ID already exists");
                }
            }
            insertRunRecoveryProcesses(connection, runId, recoveryProcessIds);
            appendFact(connection, runId, "RUN_CREATED", value.admissionFact());
            return requireRunView(connection, runId);
        });
    }

    @Override
    public ProcessRun completeWait(WaitCompletion completion) {
        WaitCompletion value = Objects.requireNonNull(completion, "completion");
        return transactions.inTransaction(connection -> {
            UUID runId = uuid(value.runId());
            lockRun(connection, runId);
            UUID waitId;
            String waitStatus;
            String resolutionKind;
            byte[] committedResult;
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT wait_id, status, resolution_kind, result_envelope
                  FROM cf_durable_wait
                 WHERE run_id = ? AND kind = 'EXTERNAL' AND token_digest = ?
                 FOR UPDATE
                """)) {
                setProviderObject(select, 1, runId);
                select.setString(2, value.tokenDigest());
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        throw error(DurableErrorCode.INVALID_WAIT_TOKEN, "Active Wait and authority token do not match");
                    }
                    waitId = providerUuid(rows, "wait_id");
                    waitStatus = rows.getString("status");
                    resolutionKind = rows.getString("resolution_kind");
                    committedResult = rows.getBytes("result_envelope");
                }
            }
            if ("RESOLVED".equals(waitStatus)) {
                if ("COMPLETED".equals(resolutionKind)) {
                    if (Arrays.equals(committedResult, value.result().bytes())) {
                        settleWaitCommittedOutbox(connection, runId, waitId, "DELIVERED");
                        return requireRunView(connection, runId);
                    }
                    throw error(DurableErrorCode.WAIT_COMPLETION_MISMATCH,
                            "Wait was already completed with a different result");
                }
                throw error(DurableErrorCode.INVALID_WAIT_TOKEN, "Wait authority is no longer active");
            }
            if (!"ACTIVE".equals(waitStatus)) {
                throw error(DurableErrorCode.INVALID_WAIT_TOKEN, "Wait authority is no longer active");
            }
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_wait
                   SET status = 'RESOLVED', resolution_kind = 'COMPLETED',
                       result_envelope = ?, resolved_at = CURRENT_TIMESTAMP(3)
                 WHERE wait_id = ? AND status = 'ACTIVE'
                """)) {
                update.setBytes(1, value.result().bytes());
                setProviderObject(update, 2, waitId);
                requireOne(update.executeUpdate(), "Wait resolution lost its authority");
            }
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_run
                   SET available_at = CASE WHEN status = 'WAITING'
                                           THEN CURRENT_TIMESTAMP(3) ELSE available_at END,
                       status = CASE WHEN status = 'WAITING' THEN 'RUNNABLE' ELSE status END,
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE run_id = ? AND status IN ('RUNNABLE', 'RUNNING', 'WAITING')
                """)) {
                setProviderObject(update, 1, runId);
                requireOne(update.executeUpdate(), "Run is not active for the supplied Wait");
            }
            settleWaitCommittedOutbox(connection, runId, waitId, "DELIVERED");
            appendFact(connection, runId, "WAIT_COMPLETED", null);
            return requireRunView(connection, runId);
        });
    }

    @Override
    public ProcessRun requestCancel(CancelCommand command) {
        Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> {
            UUID runId = uuid(command.runId());
            ProcessRunStatus status = lockRunStatus(connection, runId);
            if (!status.isTerminal()) {
                if (cancelRequested(connection, runId)) {
                    return requireRunView(connection, runId);
                }
                try (PreparedStatement update = connection.prepareStatement(
                        """
                    UPDATE cf_durable_run
                       SET cancel_requested_at = COALESCE(cancel_requested_at, CURRENT_TIMESTAMP(3)),
                           updated_at = CURRENT_TIMESTAMP(3)
                     WHERE run_id = ?
                    """)) {
                    setProviderObject(update, 1, runId);
                    update.executeUpdate();
                }
                appendFact(connection, runId, "RUN_CANCEL_REQUESTED", null);
                if (status != ProcessRunStatus.RUNNING) {
                    settleCancellation(connection, runId);
                }
            }
            return requireRunView(connection, runId);
        });
    }

    @Override
    public ProcessRun control(ControlCommand command) {
        Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> {
            UUID runId = uuid(command.runId());
            RunControlRow run = lockRunControl(connection, runId);
            if (run.status().isTerminal()) {
                throw error(DurableErrorCode.RUN_CONTROL_NOT_ALLOWED, "Terminal Run does not accept Pause or Resume");
            }
            if (run.controlRevision() != command.expectedControlRevision()) {
                if (run.controlRevision() == command.expectedControlRevision() + 1
                        && controlIntentSatisfied(run.controlState(), command.operation())) {
                    return requireRunView(connection, runId);
                }
                throw error(DurableErrorCode.CONCURRENT_MODIFICATION, "Run control revision changed");
            }
            if (controlIntentSatisfied(run.controlState(), command.operation())) {
                return requireRunView(connection, runId);
            }
            String next;
            String fact;
            if (command.operation() == ControlOperation.RESUME) {
                next = "ACTIVE";
                fact = "RUN_RESUMED";
            } else {
                next = hasIssuedAuthority(connection, runId, run.status()) ? "PAUSE_REQUESTED" : "PAUSED";
                fact = next.equals("PAUSED") ? "RUN_PAUSED" : "RUN_PAUSE_REQUESTED";
            }
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_run
                   SET control_state = ?, control_revision = control_revision + 1,
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE run_id = ?
                """)) {
                update.setString(1, next);
                setProviderObject(update, 2, runId);
                update.executeUpdate();
            }
            appendFact(connection, runId, fact,
                    auditEnvelope(command.actor(), command.reason(), command.auditContextId()));
            return requireRunView(connection, runId);
        });
    }

    @Override
    public ProcessRun resolveEffect(EffectResolution command) {
        EffectResolution value = Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> {
            UUID runId = uuid(value.runId());
            lockRun(connection, runId);
            EffectRow effect = lockEffect(connection, value.effectId(), runId);
            if (effect.reviewRevision() != value.expectedReviewRevision()) {
                throw error(DurableErrorCode.CONCURRENT_MODIFICATION, "Effect review revision changed");
            }
            if (!"UNKNOWN".equals(effect.status())) {
                if (value.decision() == EffectResolutionDecision.CONFIRM_SUCCEEDED
                        && "COMPLETED".equals(effect.status())
                        && !Arrays.equals(effect.resultEnvelope(), value.result().bytes())) {
                    throw error(DurableErrorCode.CONCURRENT_MODIFICATION, "Effect was completed with a different result");
                }
                if (value.decision() == EffectResolutionDecision.FAIL_RUN && "CANCELLED".equals(effect.status())) {
                    ProcessRun current = requireRunView(connection, runId);
                    if (current.status() == ProcessRunStatus.FAILED
                            && "EFFECT_RESOLVED_FAILED".equals(current.errorCode())) {
                        return current;
                    }
                    throw error(DurableErrorCode.CONCURRENT_MODIFICATION,
                            "Effect was cancelled without this failure resolution");
                }
                if (effectResolutionSatisfied(effect.status(), value.decision())) {
                    return requireRunView(connection, runId);
                }
                throw error(DurableErrorCode.CONCURRENT_MODIFICATION, "Effect is no longer UNKNOWN");
            }
            switch (value.decision()) {
                case CONFIRM_SUCCEEDED -> completeEffectByOperator(connection, runId, value.effectId(),
                        value.result().bytes());
                case CONFIRM_NOT_EXECUTED_RETRY -> retryEffectByOperator(connection, runId, value.effectId());
                case FAIL_RUN -> failRunFromEffect(connection, runId, value.effectId(), "EFFECT_RESOLVED_FAILED",
                        value.reason());
            }
            appendFact(connection, runId, "EFFECT_RESOLVED_" + value.decision().name(),
                    auditEnvelope(value.actor(), value.reason(), value.auditContextId()));
            return requireRunView(connection, runId);
        });
    }

    @Override
    public OutboxEvent resolveOutbox(OutboxResolution command) {
        OutboxResolution value = Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> {
            UUID runId = outboxRunId(connection, value.eventId());
            lockRun(connection, runId);
            OutboxAuthority authority = lockOutbox(connection, value.eventId(), runId);
            if (authority.revision() != value.expectedRevision()) {
                if (authority.revision() == value.expectedRevision() + 1
                        && outboxResolutionSatisfied(authority.status(), value.decision())) {
                    return requireOutbox(connection, value.eventId());
                }
                throw error(DurableErrorCode.CONCURRENT_MODIFICATION, "Outbox revision changed");
            }
            if (outboxResolutionSatisfied(authority.status(), value.decision())) {
                return requireOutbox(connection, value.eventId());
            }
            String status;
            if (value.decision() == OutboxResolutionDecision.RETRY) {
                if ("WAIT_COMMITTED".equals(authority.eventType()) && "ABANDONED".equals(authority.status())) {
                    throw error(DurableErrorCode.INVALID_ARGUMENT,
                            "A revoked WAIT_COMMITTED authority delivery cannot be retried");
                }
                try (PreparedStatement update = connection.prepareStatement(
                        """
                    UPDATE cf_durable_outbox
                       SET status = 'PENDING', revision = revision + 1,
                           available_at = CURRENT_TIMESTAMP(3),
                           lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                           completed_at = NULL
                     WHERE event_id = ? AND status IN ('PENDING', 'ABANDONED')
                    """)) {
                    setProviderObject(update, 1, value.eventId());
                    requireOne(update.executeUpdate(), "Outbox event cannot be retried from its current state");
                }
                status = "PENDING";
            } else {
                if ("WAIT_COMMITTED".equals(authority.eventType())) {
                    throw error(DurableErrorCode.INVALID_ARGUMENT,
                            "WAIT_COMMITTED is a non-abandonable authority delivery");
                }
                try (PreparedStatement update = connection.prepareStatement(
                        """
                    UPDATE cf_durable_outbox
                       SET status = 'ABANDONED', revision = revision + 1,
                           completed_at = CURRENT_TIMESTAMP(3),
                           lease_owner = NULL, lease_token = NULL, lease_until = NULL
                     WHERE event_id = ? AND status IN ('PENDING', 'ABANDONED')
                    """)) {
                    setProviderObject(update, 1, value.eventId());
                    requireOne(update.executeUpdate(), "Outbox event cannot be abandoned from its current state");
                }
                status = "ABANDONED";
            }
            appendFact(connection, runId, "OUTBOX_RESOLVED_" + status,
                    auditEnvelope(value.actor(), value.reason(), value.auditContextId()));
            return requireOutbox(connection, value.eventId());
        });
    }

    @Override
    public Optional<ProcessRun> findRun(ProcessRunId runId) {
        return transactions.withConnection(connection -> selectRunView(connection, uuid(runId)));
    }

    @Override
    public Optional<RunResultProjection> findRunResult(ProcessRunId runId) {
        ProcessRunId requested = Objects.requireNonNull(runId, "runId");
        return transactions.withConnection(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT process_id, process_code, namespace, process_version, status,
                       result_envelope, failure_code, failure_message, completed_at
                  FROM cf_durable_run
                 WHERE run_id = ?
                """)) {
                setProviderObject(select, 1, uuid(requested));
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    byte[] result = rows.getBytes("result_envelope");
                    return Optional.of(
                            new RunResultProjection(requested, runProcess(rows),
                                    ProcessRunStatus.valueOf(rows.getString("status")),
                                    result == null ? null : Envelope.fromStoredBytes(result),
                                    rows.getString("failure_code"), rows.getString("failure_message"),
                                    nullableInstant(rows, "completed_at")));
                }
            }
        });
    }

    @Override
    public Optional<WaitTarget> findWaitTarget(String tokenDigest) {
        String digest = DurableIdentifiers.requireSha256(tokenDigest, "tokenDigest");
        return transactions.withConnection(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT w.run_id, r.process_id AS root_process_id, r.process_code,
                       r.namespace, r.process_version, w.process_id
                  FROM cf_durable_wait w
                  JOIN cf_durable_run r ON r.run_id = w.run_id
                 WHERE w.kind = 'EXTERNAL' AND w.token_digest = ?
                """)) {
                select.setString(1, digest);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(
                            new WaitTarget(new ProcessRunId(providerUuid(rows, "run_id").toString()),
                                    runProcess(rows, "root_process_id"), providerUuid(rows, "process_id"), digest));
                }
            }
        });
    }

    @Override
    public Optional<EffectTarget> findEffectTarget(ProcessRunId runId, UUID effectId) {
        ProcessRunId owner = Objects.requireNonNull(runId, "runId");
        UUID occurrence = Objects.requireNonNull(effectId, "effectId");
        return transactions.withConnection(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT r.process_id AS root_process_id, r.process_code,
                       r.namespace, r.process_version, e.process_id, e.element_id
                  FROM cf_durable_effect e
                  JOIN cf_durable_run r ON r.run_id = e.run_id
                 WHERE e.run_id = ? AND e.effect_id = ?
                """)) {
                setProviderObject(select, 1, uuid(owner));
                setProviderObject(select, 2, occurrence);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(
                            new EffectTarget(owner, occurrence, runProcess(rows, "root_process_id"),
                                    providerUuid(rows, "process_id"), rows.getString("element_id")));
                }
            }
        });
    }

    @Override
    public RunPage listRuns(RunQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.withConnection(connection -> listRuns(connection, query));
    }

    @Override
    public TimelinePage listTimeline(TimelineQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.inTransaction(connection -> listTimeline(connection, query));
    }

    @Override
    public ActiveWorkPage listActiveWork(ActiveWorkQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.withConnection(connection -> listActiveWork(connection, query));
    }

    @Override
    public Optional<OutboxEvent> findOutbox(ProcessRunId runId, UUID eventId) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(eventId, "eventId");
        return transactions.withConnection(connection -> selectOutbox(connection, eventId)
            .filter(view -> view.runId().equals(runId)));
    }

    @Override
    public OutboxPage listOutbox(OutboxQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.withConnection(connection -> listOutbox(connection, query));
    }

    @Override
    public Optional<RunClaim> claimRun(RunClaimRequest request) {
        Objects.requireNonNull(request, "request");
        return transactions.inTransaction(connection -> {
            List<UUID> readyRootProcessIds = new ArrayList<>(request.readyRootProcessIds());
            Collections.sort(readyRootProcessIds);
            if (readyRootProcessIds.isEmpty()) {
                return Optional.empty();
            }
            String sql = """
                    SELECT r.run_id
                      FROM cf_durable_run r
                     WHERE r.status = 'RUNNABLE' AND r.control_state = 'ACTIVE'
                       AND r.cancel_requested_at IS NULL AND r.available_at <= CURRENT_TIMESTAMP(3)
                       AND r.process_id IN (%s)
                     ORDER BY r.available_at, r.created_at, r.run_id
                     LIMIT 1
                     FOR UPDATE SKIP LOCKED
                    """
                .formatted(placeholders(readyRootProcessIds.size()));
            UUID runId;
            try (PreparedStatement select = connection.prepareStatement(sql)) {
                bindUuids(select, 1, readyRootProcessIds);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    runId = providerUuid(rows, 1);
                }
            }
            UUID token = UUID.randomUUID();
            try (PreparedStatement update = connection.prepareStatement(
                    """
                    UPDATE cf_durable_run
                       SET status = 'RUNNING', lease_owner = ?, lease_token = ?,
                           lease_until = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)),
                           updated_at = CURRENT_TIMESTAMP(3)
                     WHERE run_id = ?
                    """)) {
                update.setString(1, request.workerId());
                setProviderObject(update, 2, token);
                update.setLong(3, request.leaseDuration().toMillis());
                setProviderObject(update, 4, runId);
                update.executeUpdate();
            }
            return Optional.of(readRunClaim(connection, runId, token));
        });
    }

    @Override
    public Set<RunLease> renewRunLeases(Set<RunLease> leases, Duration leaseDuration) {
        Set<RunLease> snapshot = leaseSnapshot(leases, "leases");
        if (snapshot.isEmpty()) {
            return Set.of();
        }
        long leaseMillis = positiveMillis(leaseDuration);
        return transactions.inTransaction(connection -> {
            lockRuns(connection,
                    snapshot
                        .stream()
                        .map(lease -> uuid(lease.runId()))
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            LinkedHashSet<RunLease> renewed = new LinkedHashSet<>();
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_run
                   SET lease_until = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)),
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE run_id = ? AND status = 'RUNNING' AND lease_token = ?
                   AND lease_until > CURRENT_TIMESTAMP(3)
                """)) {
                for (RunLease lease : snapshot) {
                    update.setLong(1, leaseMillis);
                    setProviderObject(update, 2, uuid(lease.runId()));
                    setProviderObject(update, 3, lease.token());
                    if (update.executeUpdate() == 1) {
                        renewed.add(lease);
                    }
                }
            }
            return Set.copyOf(renewed);
        });
    }

    @Override
    public boolean commitTurn(RunLease lease, TurnCommit commit) {
        RunLease authority = Objects.requireNonNull(lease, "lease");
        TurnCommit value = Objects.requireNonNull(commit, "commit");
        return transactions.inTransaction(connection -> {
            LockedRun run = lockRunLease(connection, authority);
            if (run == null) {
                return false;
            }
            UUID runId = uuid(authority.runId());
            if (run.cancelRequested()) {
                settleCancellation(connection, runId);
                return true;
            }
            ensureOccurrenceProcessesRegistered(connection, runId, value.issuedOccurrences());
            consumeResolvedOccurrences(connection, runId, value.consumedOccurrences());
            ensureFrontiersAvailable(connection, runId, value.issuedOccurrences());
            ensureOutstandingCapacity(connection, runId, value.issuedOccurrences().size());
            long occurrenceSequence = issueOccurrences(connection, run, runId, value.issuedOccurrences());
            TurnState state = value.state();
            if (state instanceof RunnableTurn runnable) {
                commitContinuationTurn(connection, runId, occurrenceSequence, "RUNNABLE", runnable.continuation());
            } else if (state instanceof WaitingTurn waiting) {
                boolean resolved = hasUnconsumedResolvedOccurrence(connection, runId);
                if (!resolved && !hasOutstandingOccurrence(connection, runId)) {
                    throw error(DurableErrorCode.INVALID_ARGUMENT,
                            "Waiting Turn must leave an active or resolved occurrence");
                }
                String status = resolved ? "RUNNABLE" : "WAITING";
                commitContinuationTurn(connection, runId, occurrenceSequence, status, waiting.continuation());
            } else if (state instanceof SucceededTurn succeeded) {
                if (!value.issuedOccurrences().isEmpty() || hasOutstandingOccurrence(connection, runId)) {
                    throw error(DurableErrorCode.CONCURRENT_MODIFICATION,
                            "Succeeded Turn cannot leave issued or unconsumed occurrences");
                }
                terminalizeRun(connection, runId, "SUCCEEDED", succeeded.result(), null, null);
            } else {
                FailedTurn failed = (FailedTurn) state;
                if (!value.issuedOccurrences().isEmpty() || hasOutstandingOccurrence(connection, runId)) {
                    throw error(DurableErrorCode.CONCURRENT_MODIFICATION,
                            "Failed Turn cannot leave issued or unconsumed occurrences");
                }
                terminalizeRun(connection, runId, "FAILED", null, failed.code(), failed.message());
            }
            return true;
        });
    }

    private static void ensureOccurrenceProcessesRegistered(Connection connection, UUID runId,
            List<OccurrenceCommit> occurrences) throws SQLException {
        if (occurrences.isEmpty()) {
            return;
        }
        Set<UUID> processIds =
                occurrences
            .stream()
            .map(OccurrenceCommit::processId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        String sql = """
                    SELECT count(DISTINCT process_id)
                      FROM cf_durable_run_recovery_process
                     WHERE run_id = ? AND process_id IN (%s)
                    """
            .formatted(placeholders(processIds.size()));
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            setProviderObject(select, 1, runId);
            bindUuids(select, 2, processIds);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                if (rows.getLong(1) != processIds.size()) {
                    throw error(DurableErrorCode.INVALID_ARGUMENT,
                            "An issued occurrence must reference a Process retained by the Run");
                }
            }
        }
    }

    private static void consumeResolvedOccurrences(Connection connection, UUID runId, List<OccurrenceKey> occurrences)
            throws SQLException {
        for (OccurrenceKey occurrence : occurrences) {
            String sql = switch (occurrence.kind()) {
                case WAIT, TIMER -> """
                    UPDATE cf_durable_wait
                       SET consumed_at = CURRENT_TIMESTAMP(3)
                     WHERE wait_id = ? AND run_id = ? AND kind = ?
                       AND status = 'RESOLVED' AND consumed_at IS NULL
                    """;
                case EFFECT -> """
                    UPDATE cf_durable_effect
                       SET consumed_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
                     WHERE effect_id = ? AND run_id = ?
                       AND status = 'COMPLETED' AND consumed_at IS NULL
                    """;
            };
            try (PreparedStatement update = connection.prepareStatement(sql)) {
                setProviderObject(update, 1, occurrence.id());
                setProviderObject(update, 2, runId);
                if (occurrence.kind() == OccurrenceKind.WAIT || occurrence.kind() == OccurrenceKind.TIMER) {
                    update.setString(3, occurrence.kind() == OccurrenceKind.WAIT ? "EXTERNAL" : "TIMER");
                }
                if (update.executeUpdate() != 1) {
                    throw error(DurableErrorCode.CONCURRENT_MODIFICATION,
                            "Occurrence is not a resolved, unconsumed result of the claimed Run");
                }
            }
        }
    }

    private long issueOccurrences(Connection connection, LockedRun run, UUID runId, List<OccurrenceCommit> occurrences)
            throws SQLException {
        long sequence = run.occurrenceSequence();
        for (OccurrenceCommit occurrence : occurrences) {
            if (occurrence.occurrenceSequence() != sequence + 1) {
                throw error(DurableErrorCode.CONCURRENT_MODIFICATION,
                        "Occurrence sequence must be one contiguous successor range");
            }
            if (occurrence instanceof WaitCommit wait) {
                insertWaitOccurrence(connection, runId, wait);
            } else if (occurrence instanceof TimerCommit timer) {
                insertTimerOccurrence(connection, runId, timer);
            } else if (occurrence instanceof EffectCommit effect) {
                insertEffectOccurrence(connection, runId, effect);
            }
            sequence++;
        }
        return sequence;
    }

    private static void ensureFrontiersAvailable(Connection connection, UUID runId, List<OccurrenceCommit> occurrences)
            throws SQLException {
        if (occurrences.isEmpty()) {
            return;
        }
        List<String> frontiers = occurrences.stream().map(OccurrenceCommit::frontierId).distinct().toList();
        String sql = """
                    SELECT frontier_id
                      FROM (
                            SELECT frontier_id
                              FROM cf_durable_wait
                             WHERE run_id = ?
                               AND (status = 'ACTIVE'
                                    OR (status = 'RESOLVED' AND consumed_at IS NULL))
                            UNION ALL
                            SELECT frontier_id
                              FROM cf_durable_effect
                             WHERE run_id = ?
                               AND (status IN ('PENDING', 'RUNNING', 'UNKNOWN')
                                    OR (status = 'COMPLETED' AND consumed_at IS NULL))
                           ) outstanding
                     WHERE frontier_id IN (%s)
                     LIMIT 1
                    """
            .formatted(placeholders(frontiers.size()));
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            setProviderObject(select, 1, runId);
            setProviderObject(select, 2, runId);
            int index = 3;
            for (String frontier : frontiers) {
                select.setString(index++, frontier);
            }
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    throw error(DurableErrorCode.CONCURRENT_MODIFICATION,
                            "Frontier already owns an unresolved occurrence: " + rows.getString("frontier_id"));
                }
            }
        }
    }

    private static void commitContinuationTurn(Connection connection, UUID runId, long occurrenceSequence, String status,
            Envelope continuation) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_run
               SET status = ?, continuation_envelope = ?, occurrence_sequence = ?,
                   available_at = CURRENT_TIMESTAMP(3),
                   turn_fault_streak = 0,
                   retry_code = NULL, retry_observed_at = NULL,
                   control_state = CASE WHEN control_state = 'PAUSE_REQUESTED'
                                         AND NOT EXISTS (
                                             SELECT 1 FROM cf_durable_effect e
                                              WHERE e.run_id = cf_durable_run.run_id
                                                AND e.status = 'RUNNING')
                                        THEN 'PAUSED' ELSE control_state END,
                   lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                   updated_at = CURRENT_TIMESTAMP(3)
             WHERE run_id = ? AND status = 'RUNNING'
            """)) {
            update.setString(1, status);
            update.setBytes(2, continuation.bytes());
            update.setLong(3, occurrenceSequence);
            setProviderObject(update, 4, runId);
            requireOne(update.executeUpdate(), "Continuation Turn commit lost its authority");
        }
    }

    private void insertWaitOccurrence(Connection connection, UUID runId, WaitCommit value) throws SQLException {
        String insertWait = value.deadlineAfter() == null
                ? """
                INSERT INTO cf_durable_wait(
                    wait_id, run_id, occurrence_sequence, process_id, process_invocation_id,
                    frontier_id, element_id, kind,
                    event_name, token_digest, due_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'EXTERNAL', ?, ?, NULL)
                """
                : """
                INSERT INTO cf_durable_wait(
                    wait_id, run_id, occurrence_sequence, process_id, process_invocation_id,
                    frontier_id, element_id, kind,
                    event_name, token_digest, due_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'EXTERNAL', ?, ?,
                       TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)), CURRENT_TIMESTAMP(3))
                """;
        try (PreparedStatement insert = connection.prepareStatement(insertWait)) {
            setProviderObject(insert, 1, value.occurrence().id());
            setProviderObject(insert, 2, runId);
            insert.setLong(3, value.occurrenceSequence());
            setProviderObject(insert, 4, value.processId());
            insert.setLong(5, value.processInvocationId());
            insert.setString(6, value.frontierId());
            insert.setString(7, value.elementId());
            setNullableString(insert, 8, value.event());
            insert.setString(9, value.tokenDigest());
            if (value.deadlineAfter() != null) {
                insert.setLong(10, value.deadlineAfter().toMillis());
            }
            insert.executeUpdate();
        }
        insertOutbox(connection, runId, "WAIT_COMMITTED", value.occurrence().id(), value.outboxPayload());
        appendFact(connection, runId, "WAIT_COMMITTED", null);
    }

    private static void insertTimerOccurrence(Connection connection, UUID runId, TimerCommit value) throws SQLException {
        String insertTimer = value.delay() == null
                ? """
                INSERT INTO cf_durable_wait(
                    wait_id, run_id, occurrence_sequence, process_id, process_invocation_id,
                    frontier_id, element_id, kind, due_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'TIMER', ?)
                """
                : """
                INSERT INTO cf_durable_wait(
                    wait_id, run_id, occurrence_sequence, process_id, process_invocation_id,
                    frontier_id, element_id, kind, due_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'TIMER',
                       TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)), CURRENT_TIMESTAMP(3))
                """;
        try (PreparedStatement insert = connection.prepareStatement(insertTimer)) {
            setProviderObject(insert, 1, value.occurrence().id());
            setProviderObject(insert, 2, runId);
            insert.setLong(3, value.occurrenceSequence());
            setProviderObject(insert, 4, value.processId());
            insert.setLong(5, value.processInvocationId());
            insert.setString(6, value.frontierId());
            insert.setString(7, value.elementId());
            if (value.delay() == null) {
                insert.setTimestamp(8, timestamp(value.dueAt()), utcCalendar());
            } else {
                insert.setLong(8, value.delay().toMillis());
            }
            insert.executeUpdate();
        }
        appendFact(connection, runId, "TIMER_COMMITTED", null);
    }

    private static void insertEffectOccurrence(Connection connection, UUID runId, EffectCommit value)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO cf_durable_effect(
                    effect_id, run_id, occurrence_sequence, process_id, process_invocation_id,
                    frontier_id, element_id,
                    recovery_mode, max_attempts, max_reconcile_attempts, retry_delay_ms,
                    recovery_deadline_ms, input_envelope)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            setProviderObject(insert, 1, value.occurrence().id());
            setProviderObject(insert, 2, runId);
            insert.setLong(3, value.occurrenceSequence());
            setProviderObject(insert, 4, value.processId());
            insert.setLong(5, value.processInvocationId());
            insert.setString(6, value.frontierId());
            insert.setString(7, value.elementId());
            EffectRecoveryPlan recovery = value.recoveryPlan();
            insert.setString(8, recovery.mode().name());
            insert.setInt(9, recovery.maxAttempts());
            insert.setInt(10, recovery.maxReconcileAttempts());
            setNullableLong(insert, 11, recovery.recoveryDelay() == null ? null : recovery.recoveryDelay().toMillis());
            setNullableLong(insert, 12,
                    recovery.maxRecoveryDuration() == null ? null : recovery.maxRecoveryDuration().toMillis());
            insert.setBytes(13, value.input().bytes());
            insert.executeUpdate();
        }
        appendFact(connection, runId, "EFFECT_COMMITTED", null);
    }

    @Override
    public boolean commitRunCancelled(RunLease lease) {
        RunLease value = Objects.requireNonNull(lease, "lease");
        return transactions.inTransaction(connection -> {
            LockedRun run = lockRunLease(connection, value);
            if (run == null) {
                return false;
            }
            settleCancellation(connection, uuid(value.runId()));
            return true;
        });
    }

    @Override
    public boolean releaseRunFault(RunLease lease, Duration delay, RunRetryCode code) {
        RunLease authority = Objects.requireNonNull(lease, "lease");
        long delayMillis = delayMillis(delay);
        if (Objects.requireNonNull(code, "code") != RunRetryCode.TURN_EXECUTION_FAULT) {
            throw new IllegalArgumentException("Turn fault requires TURN_EXECUTION_FAULT");
        }
        return transactions.inTransaction(connection -> {
            LockedRun run = lockRunLease(connection, authority);
            if (run == null) {
                return false;
            }
            UUID runId = uuid(authority.runId());
            if (run.cancelRequested()) {
                settleCancellation(connection, runId);
            } else {
                try (PreparedStatement update = connection.prepareStatement(
                        """
                    UPDATE cf_durable_run
                       SET status = 'RUNNABLE',
                           available_at = TIMESTAMPADD(MICROSECOND,
                               LEAST(? * POWER(2.0, LEAST(turn_fault_streak, 16)), 3600000) * 1000,
                               CURRENT_TIMESTAMP(3)),
                           turn_fault_streak = turn_fault_streak + 1,
                           retry_code = ?, retry_observed_at = CURRENT_TIMESTAMP(3),
                           control_state = CASE WHEN control_state = 'PAUSE_REQUESTED'
                                                 AND NOT EXISTS (
                                                     SELECT 1 FROM cf_durable_effect e
                                                      WHERE e.run_id = cf_durable_run.run_id
                                                        AND e.status = 'RUNNING')
                                                THEN 'PAUSED' ELSE control_state END,
                           lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                           updated_at = CURRENT_TIMESTAMP(3)
                     WHERE run_id = ?
                    """)) {
                    update.setLong(1, delayMillis);
                    update.setString(2, code.name());
                    setProviderObject(update, 3, runId);
                    requireOne(update.executeUpdate(), "Turn fault release lost its authority");
                    int streak = selectTurnFaultStreak(connection, runId);
                    if (isPowerOfTwo(streak)) {
                        appendFact(connection, runId, "TURN_FAULT_BACKOFF_MILESTONE", turnFaultBackoffMilestone(streak));
                    }
                }
            }
            return true;
        });
    }

    @Override
    public boolean releaseRunAfterCapabilityLoss(RunLease lease, Duration delay) {
        RunLease authority = Objects.requireNonNull(lease, "lease");
        long delayMillis = delayMillis(delay);
        return transactions.inTransaction(connection -> {
            LockedRun run = lockRunLease(connection, authority);
            if (run == null) {
                return false;
            }
            UUID runId = uuid(authority.runId());
            if (run.cancelRequested()) {
                settleCancellation(connection, runId);
            } else {
                try (PreparedStatement update = connection.prepareStatement(
                        """
                    UPDATE cf_durable_run
                       SET status = 'RUNNABLE',
                           available_at = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)),
                           control_state = CASE WHEN control_state = 'PAUSE_REQUESTED'
                                                 AND NOT EXISTS (
                                                     SELECT 1 FROM cf_durable_effect e
                                                      WHERE e.run_id = cf_durable_run.run_id
                                                        AND e.status = 'RUNNING')
                                                THEN 'PAUSED' ELSE control_state END,
                           lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                           updated_at = CURRENT_TIMESTAMP(3)
                     WHERE run_id = ?
                    """)) {
                    update.setLong(1, delayMillis);
                    setProviderObject(update, 2, runId);
                    requireOne(update.executeUpdate(), "Run capability release lost its authority");
                }
            }
            return true;
        });
    }

    @Override
    public int reclaimExpiredRuns(int limit) {
        requireLimit(limit);
        return transactions.inTransaction(connection -> {
            List<UUID> ids = lockedIds(connection,
                    """
                SELECT run_id FROM cf_durable_run
                 WHERE status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP(3)
                 ORDER BY lease_until, run_id LIMIT ? FOR UPDATE SKIP LOCKED
                """,
                    limit);
            for (UUID runId : ids) {
                boolean cancel = cancelRequested(connection, runId);
                if (cancel) {
                    settleCancellation(connection, runId);
                } else {
                    try (PreparedStatement update = connection.prepareStatement(
                            """
                        UPDATE cf_durable_run
                           SET status = 'RUNNABLE', available_at = CURRENT_TIMESTAMP(3),
                               control_state = CASE WHEN control_state = 'PAUSE_REQUESTED'
                                                     AND NOT EXISTS (
                                                         SELECT 1 FROM cf_durable_effect e
                                                          WHERE e.run_id = cf_durable_run.run_id
                                                            AND e.status = 'RUNNING')
                                                    THEN 'PAUSED' ELSE control_state END,
                               lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                               updated_at = CURRENT_TIMESTAMP(3)
                         WHERE run_id = ?
                        """)) {
                        setProviderObject(update, 1, runId);
                        update.executeUpdate();
                    }
                    appendFact(connection, runId, "RUN_LEASE_EXPIRED", null);
                }
            }
            return ids.size();
        });
    }

    @Override
    public int resolveDueWaits(int limit) {
        requireLimit(limit);
        int resolved = 0;
        for (WaitCandidate candidate : dueWaitCandidates(limit)) {
            if (resolveDueWait(candidate)) {
                resolved++;
            }
        }
        return resolved;
    }

    private List<WaitCandidate> dueWaitCandidates(int limit) {
        return transactions.withConnection(connection -> {
            List<WaitCandidate> result = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT wait_id, run_id FROM cf_durable_wait
                 WHERE status = 'ACTIVE' AND due_at <= CURRENT_TIMESTAMP(3)
                 ORDER BY due_at, wait_id LIMIT ?
                """)) {
                select.setInt(1, limit);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        result.add(new WaitCandidate(providerUuid(rows, "wait_id"), providerUuid(rows, "run_id")));
                    }
                }
            }
            return result;
        });
    }

    private boolean resolveDueWait(WaitCandidate candidate) {
        return transactions.inTransaction(connection -> {
            ProcessRunStatus status = lockRunStatus(connection, candidate.runId());
            if (status.isTerminal()) {
                return false;
            }
            String kind;
            try (PreparedStatement lock = connection.prepareStatement(
                    """
                SELECT kind FROM cf_durable_wait
                 WHERE wait_id = ? AND run_id = ?
                   AND status = 'ACTIVE' AND due_at <= CURRENT_TIMESTAMP(3)
                 FOR UPDATE
                """)) {
                setProviderObject(lock, 1, candidate.waitId());
                setProviderObject(lock, 2, candidate.runId());
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) {
                        return false;
                    }
                    kind = rows.getString("kind");
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_wait
                   SET status = 'RESOLVED', resolution_kind = ?,
                       result_envelope = ?, resolved_at = CURRENT_TIMESTAMP(3)
                 WHERE wait_id = ? AND status = 'ACTIVE'
                """)) {
                update.setString(1, "TIMER".equals(kind) ? "FIRED" : "EXPIRED");
                update.setBytes(2, EMPTY_ENVELOPE);
                setProviderObject(update, 3, candidate.waitId());
                requireOne(update.executeUpdate(), "Due Wait resolution lost its authority");
            }
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_run
                   SET available_at = CASE WHEN status = 'WAITING'
                                           THEN CURRENT_TIMESTAMP(3) ELSE available_at END,
                       status = CASE WHEN status = 'WAITING' THEN 'RUNNABLE' ELSE status END,
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE run_id = ? AND status IN ('RUNNABLE', 'RUNNING', 'WAITING')
                """)) {
                setProviderObject(update, 1, candidate.runId());
                requireOne(update.executeUpdate(), "Due Wait owner Run is no longer active");
            }
            if ("TIMER".equals(kind)) {
                appendFact(connection, candidate.runId(), "TIMER_FIRED", null);
            } else {
                settleWaitCommittedOutbox(connection, candidate.runId(), candidate.waitId(), "ABANDONED");
                appendFact(connection, candidate.runId(), "WAIT_EXPIRED", null);
            }
            return true;
        });
    }

    @Override
    public Optional<EffectClaim> claimEffect(EffectClaimRequest request) {
        Objects.requireNonNull(request, "request");
        return transactions.inTransaction(connection -> {
            String eligible = request.operation() == EffectOperation.DISPATCH
                    ? "e.status = 'PENDING'"
                    : "e.status = 'UNKNOWN' AND e.review_required_at IS NULL";
            String admission = request.operation() == EffectOperation.DISPATCH
                    ? "AND r.control_state = 'ACTIVE' AND r.cancel_requested_at IS NULL"
                    : "";
            ReconciliationCandidate candidate;
            String claimSql = """
                    SELECT e.effect_id, e.run_id, e.reconcile_attempts, e.max_reconcile_attempts
                  FROM cf_durable_effect e
                  JOIN cf_durable_run r ON r.run_id = e.run_id
                 WHERE %s
                   AND e.available_at <= CURRENT_TIMESTAMP(3)
                   AND r.status IN ('RUNNABLE', 'RUNNING', 'WAITING') %s
                 ORDER BY e.available_at, e.created_at, e.effect_id
                 LIMIT 1 FOR UPDATE OF r SKIP LOCKED
                """
                .formatted(eligible, admission);
            try (PreparedStatement select = connection.prepareStatement(claimSql)) {
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    candidate = new ReconciliationCandidate(providerUuid(rows, "effect_id"),
                            providerUuid(rows, "run_id"), rows.getInt("reconcile_attempts"),
                            rows.getInt("max_reconcile_attempts"));
                }
            }
            String lockSql = """
                SELECT e.effect_id
                  FROM cf_durable_effect e
                  JOIN cf_durable_run r ON r.run_id = e.run_id
                 WHERE e.effect_id = ? AND %s
                   AND e.available_at <= CURRENT_TIMESTAMP(3)
                   AND r.status IN ('RUNNABLE', 'RUNNING', 'WAITING') %s
                 FOR UPDATE OF e
                """
                .formatted(eligible, admission);
            try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                setProviderObject(lock, 1, candidate.effectId());
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                }
            }
            if (request.operation() == EffectOperation.RECONCILE
                    && candidate.reconcileAttempts() >= candidate.maxReconcileAttempts()) {
                markExhaustedReconciliationReview(connection, candidate);
                return Optional.empty();
            }
            UUID token = UUID.randomUUID();
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_effect
                   SET status = 'RUNNING', running_operation = ?,
                       dispatch_attempts = dispatch_attempts + CASE WHEN ? = 'DISPATCH' THEN 1 ELSE 0 END,
                       reconcile_attempts = reconcile_attempts + CASE WHEN ? = 'RECONCILE' THEN 1 ELSE 0 END,
                       readiness_code = NULL,
                       lease_owner = ?, lease_token = ?,
                       lease_until = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)),
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE effect_id = ?
                """)) {
                String operation = request.operation().name();
                update.setString(1, operation);
                update.setString(2, operation);
                update.setString(3, operation);
                update.setString(4, request.workerId());
                setProviderObject(update, 5, token);
                update.setLong(6, request.leaseDuration().toMillis());
                setProviderObject(update, 7, candidate.effectId());
                requireOne(update.executeUpdate(), "Effect claim lost its authority");
            }
            return Optional.of(readEffectClaim(connection, candidate.effectId(), token, request.operation()));
        });
    }

    @Override
    public Set<EffectLease> renewEffectLeases(Set<EffectLease> leases, Duration leaseDuration) {
        Set<EffectLease> snapshot = leaseSnapshot(leases, "leases");
        if (snapshot.isEmpty()) {
            return Set.of();
        }
        long leaseMillis = positiveMillis(leaseDuration);
        return transactions.inTransaction(connection -> {
            lockRuns(connection,
                    snapshot
                        .stream()
                        .map(lease -> uuid(lease.runId()))
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            LinkedHashSet<EffectLease> renewed = new LinkedHashSet<>();
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_effect
                   SET lease_until = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)),
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE run_id = ? AND effect_id = ? AND status = 'RUNNING'
                   AND lease_token = ? AND lease_until > CURRENT_TIMESTAMP(3)
                """)) {
                for (EffectLease lease : snapshot) {
                    update.setLong(1, leaseMillis);
                    setProviderObject(update, 2, uuid(lease.runId()));
                    setProviderObject(update, 3, lease.effectId());
                    setProviderObject(update, 4, lease.token());
                    if (update.executeUpdate() == 1) {
                        renewed.add(lease);
                    }
                }
            }
            return Set.copyOf(renewed);
        });
    }

    @Override
    public boolean completeEffect(EffectLease lease, Envelope result) {
        EffectLease authority = Objects.requireNonNull(lease, "lease");
        Envelope value = Objects.requireNonNull(result, "result");
        return transactions.inTransaction(connection -> {
            EffectLeaseRow effect = lockEffectLeaseRunFirst(connection, authority);
            if (effect == null) {
                return false;
            }
            UUID runId = effect.runId();
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_effect
                   SET status = 'COMPLETED', result_envelope = ?, completed_at = CURRENT_TIMESTAMP(3),
                       running_operation = NULL, lease_owner = NULL, lease_token = NULL,
                       lease_until = NULL, unknown_since = NULL,
                       review_required_at = NULL, review_reason = NULL,
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE effect_id = ?
                """)) {
                update.setBytes(1, value.bytes());
                setProviderObject(update, 2, authority.effectId());
                requireOne(update.executeUpdate(), "Effect completion lost its authority");
            }
            advanceAfterEffect(connection, runId);
            appendFact(connection, runId, "EFFECT_COMPLETED", null);
            return true;
        });
    }

    @Override
    public boolean markEffectUnknown(EffectLease lease, Duration delay, String reason) {
        EffectLease authority = Objects.requireNonNull(lease, "lease");
        long delayMillis = delayMillis(delay);
        Objects.requireNonNull(reason, "reason");
        return transactions.inTransaction(connection -> transitionEffectUnknown(connection, authority, delayMillis,
                "EFFECT_OUTCOME_UNKNOWN", false, reason));
    }

    @Override
    public boolean releaseEffectBeforeInvocation(EffectLease lease, Duration delay, EffectReadinessCode readinessCode) {
        EffectLease authority = Objects.requireNonNull(lease, "lease");
        long delayMillis = delayMillis(delay);
        EffectReadinessCode code = Objects.requireNonNull(readinessCode, "readinessCode");
        return transactions.inTransaction(connection -> {
            EffectLeaseRow effect = lockEffectLeaseRunFirst(connection, authority);
            if (effect == null) {
                return false;
            }
            boolean dispatch = "DISPATCH".equals(effect.operation());
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_effect
                   SET status = ?,
                       available_at = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)),
                       dispatch_attempts = dispatch_attempts - CASE WHEN ? THEN 1 ELSE 0 END,
                       reconcile_attempts = reconcile_attempts - CASE WHEN ? THEN 0 ELSE 1 END,
                       running_operation = NULL, lease_owner = NULL, lease_token = NULL,
                       lease_until = NULL,
                       readiness_code = ?,
                       unknown_since = CASE WHEN ? THEN NULL ELSE unknown_since END,
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE effect_id = ?
                """)) {
                update.setString(1, dispatch ? "PENDING" : "UNKNOWN");
                update.setLong(2, delayMillis);
                update.setBoolean(3, dispatch);
                update.setBoolean(4, dispatch);
                update.setString(5, code.name());
                update.setBoolean(6, dispatch);
                setProviderObject(update, 7, authority.effectId());
                requireOne(update.executeUpdate(), "Effect release lost its authority");
            }
            boolean cancelling = cancelRequested(connection, effect.runId());
            if (cancelling) {
                settleCancellation(connection, effect.runId());
            } else {
                pauseAtSafePoint(connection, effect.runId());
            }
            if (!cancelling || !dispatch) {
                appendFact(connection, effect.runId(), "EFFECT_READINESS_BACKOFF", null);
            }
            return true;
        });
    }

    @Override
    public boolean scheduleEffectRedispatch(EffectLease lease, Duration delay) {
        EffectLease authority = Objects.requireNonNull(lease, "lease");
        long delayMillis = delayMillis(delay);
        return transactions.inTransaction(connection -> {
            EffectLeaseRow effect = lockEffectLeaseRunFirst(connection, authority);
            if (effect == null) {
                return false;
            }
            if (!"RECONCILE".equals(effect.operation())) {
                throw error(DurableErrorCode.CONCURRENT_MODIFICATION,
                        "Only a reconciliation claim may schedule redispatch");
            }
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_effect
                   SET status = 'PENDING',
                       available_at = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)),
                       running_operation = NULL,
                       lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                       unknown_since = NULL, review_required_at = NULL, review_reason = NULL,
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE effect_id = ?
                """)) {
                update.setLong(1, delayMillis);
                setProviderObject(update, 2, authority.effectId());
                requireOne(update.executeUpdate(), "Effect redispatch lost its authority");
            }
            boolean cancelling = cancelRequested(connection, effect.runId());
            if (cancelling) {
                settleCancellation(connection, effect.runId());
            } else {
                pauseAtSafePoint(connection, effect.runId());
                appendFact(connection, effect.runId(), "EFFECT_REDISPATCH_SCHEDULED", null);
            }
            return true;
        });
    }

    @Override
    public boolean requireEffectReview(EffectLease lease, String reason) {
        EffectLease authority = Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(reason, "reason");
        return transactions.inTransaction(connection -> transitionEffectUnknown(connection, authority, 0L,
                "EFFECT_REVIEW_REQUIRED", true, reason));
    }

    private void markExhaustedReconciliationReview(Connection connection, ReconciliationCandidate candidate)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_effect
               SET review_required_at = CURRENT_TIMESTAMP(3),
                   review_reason = 'EFFECT_RECONCILE_ATTEMPTS_EXHAUSTED',
                   review_revision = review_revision + 1,
                   updated_at = CURRENT_TIMESTAMP(3)
             WHERE effect_id = ? AND status = 'UNKNOWN'
               AND review_required_at IS NULL
               AND reconcile_attempts >= max_reconcile_attempts
            """)) {
            setProviderObject(update, 1, candidate.effectId());
            requireOne(update.executeUpdate(), "Reconciliation review transition lost its authority");
        }
        if (cancelRequested(connection, candidate.runId())) {
            settleCancellation(connection, candidate.runId());
        } else {
            pauseAtSafePoint(connection, candidate.runId());
        }
        appendFact(connection, candidate.runId(), "EFFECT_REVIEW_REQUIRED", null);
        insertOutbox(connection, candidate.runId(), "EFFECT_REVIEW_REQUIRED", candidate.effectId(),
                new Envelope("{}".getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public int reclaimExpiredEffects(int limit) {
        requireLimit(limit);
        List<EffectCandidate> candidates = transactions.withConnection(connection -> {
            List<EffectCandidate> result = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT effect_id, run_id FROM cf_durable_effect
                 WHERE status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP(3)
                 ORDER BY lease_until, effect_id LIMIT ?
                """)) {
                select.setInt(1, limit);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        result.add(new EffectCandidate(providerUuid(rows, "effect_id"), providerUuid(rows, "run_id")));
                    }
                }
            }
            return result;
        });
        int reclaimed = 0;
        for (EffectCandidate candidate : candidates) {
            boolean committed = transactions.inTransaction(connection -> {
                lockRun(connection, candidate.runId());
                try (PreparedStatement lock = connection.prepareStatement(
                        """
                    SELECT effect_id FROM cf_durable_effect
                     WHERE effect_id = ? AND run_id = ? AND status = 'RUNNING'
                       AND lease_until <= CURRENT_TIMESTAMP(3)
                     FOR UPDATE
                    """)) {
                    setProviderObject(lock, 1, candidate.effectId());
                    setProviderObject(lock, 2, candidate.runId());
                    try (ResultSet rows = lock.executeQuery()) {
                        if (!rows.next()) {
                            return false;
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        """
                    UPDATE cf_durable_effect
                       SET status = 'UNKNOWN', available_at = CURRENT_TIMESTAMP(3),
                           running_operation = NULL, lease_owner = NULL, lease_token = NULL,
                           lease_until = NULL,
                           unknown_since = COALESCE(unknown_since, CURRENT_TIMESTAMP(3)),
                           updated_at = CURRENT_TIMESTAMP(3)
                     WHERE effect_id = ?
                    """)) {
                    setProviderObject(update, 1, candidate.effectId());
                    requireOne(update.executeUpdate(), "Expired Effect reclaim lost its authority");
                }
                if (cancelRequested(connection, candidate.runId())) {
                    settleCancellation(connection, candidate.runId());
                } else {
                    pauseAtSafePoint(connection, candidate.runId());
                }
                appendFact(connection, candidate.runId(), "EFFECT_LEASE_EXPIRED_UNKNOWN", null);
                return true;
            });
            if (committed) {
                reclaimed++;
            }
        }
        return reclaimed;
    }

    @Override
    public Optional<OutboxClaim> claimOutbox(OutboxClaimRequest request) {
        Objects.requireNonNull(request, "request");
        return transactions.inTransaction(connection -> {
            UUID eventId;
            UUID runId;
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT o.event_id, o.run_id
                  FROM cf_durable_outbox o
                  JOIN cf_durable_run r ON r.run_id = o.run_id
                 WHERE o.status = 'PENDING' AND o.available_at <= CURRENT_TIMESTAMP(3)
                 ORDER BY o.available_at, o.created_at, o.event_id
                 LIMIT 1 FOR UPDATE OF r SKIP LOCKED
                """)) {
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    eventId = providerUuid(rows, "event_id");
                    runId = providerUuid(rows, "run_id");
                }
            }
            try (PreparedStatement lock = connection.prepareStatement(
                    """
                SELECT event_id FROM cf_durable_outbox
                 WHERE event_id = ? AND run_id = ? AND status = 'PENDING'
                   AND available_at <= CURRENT_TIMESTAMP(3)
                 FOR UPDATE
                """)) {
                setProviderObject(lock, 1, eventId);
                setProviderObject(lock, 2, runId);
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                }
            }
            UUID token = UUID.randomUUID();
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_outbox
                   SET status = 'DELIVERING', attempt_count = attempt_count + 1,
                       revision = revision + 1,
                       lease_owner = ?, lease_token = ?,
                       lease_until = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3))
                 WHERE event_id = ?
                """)) {
                update.setString(1, request.workerId());
                setProviderObject(update, 2, token);
                update.setLong(3, request.leaseDuration().toMillis());
                setProviderObject(update, 4, eventId);
                requireOne(update.executeUpdate(), "Outbox claim lost its authority");
            }
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT o.run_id, r.process_id, r.process_code, r.namespace, r.process_version,
                       o.event_type, o.occurrence_id, o.payload_envelope,
                       o.attempt_count, o.lease_until
                  FROM cf_durable_outbox o
                  JOIN cf_durable_run r ON r.run_id = o.run_id
                 WHERE o.event_id = ?
                """)) {
                setProviderObject(select, 1, eventId);
                try (ResultSet rows = select.executeQuery()) {
                    rows.next();
                    return Optional.of(
                            new OutboxClaim(new OutboxLease(runId(runId), eventId, token), runId(runId),
                                    new RunProcess(providerUuid(rows, "process_id"), rows.getString("namespace"),
                                            rows.getString("process_code"),
                                            processVersion(rows, rows.getString("namespace"),
                                                    rows.getString("process_code"))), rows.getString("event_type"),
                                    providerUuid(rows, "occurrence_id"), envelope(rows.getBytes("payload_envelope")),
                                    rows.getInt("attempt_count"), instant(rows, "lease_until")));
                }
            }
        });
    }

    @Override
    public boolean completeOutbox(OutboxLease lease) {
        return transitionOutbox(Objects.requireNonNull(lease, "lease"), OutboxTransition.DELIVER, null);
    }

    @Override
    public Set<OutboxLease> renewOutboxLeases(Set<OutboxLease> leases, Duration leaseDuration) {
        Set<OutboxLease> snapshot = leaseSnapshot(leases, "leases");
        if (snapshot.isEmpty()) {
            return Set.of();
        }
        long leaseMillis = positiveMillis(leaseDuration);
        return transactions.inTransaction(connection -> {
            lockRuns(connection,
                    snapshot
                        .stream()
                        .map(lease -> uuid(lease.runId()))
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            LinkedHashSet<OutboxLease> renewed = new LinkedHashSet<>();
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_outbox
                   SET lease_until = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3))
                 WHERE run_id = ? AND event_id = ? AND status = 'DELIVERING'
                   AND lease_token = ? AND lease_until > CURRENT_TIMESTAMP(3)
                """)) {
                for (OutboxLease lease : snapshot) {
                    update.setLong(1, leaseMillis);
                    setProviderObject(update, 2, uuid(lease.runId()));
                    setProviderObject(update, 3, lease.eventId());
                    setProviderObject(update, 4, lease.token());
                    if (update.executeUpdate() == 1) {
                        renewed.add(lease);
                    }
                }
            }
            return Set.copyOf(renewed);
        });
    }

    @Override
    public boolean retryOutbox(OutboxLease lease, Duration delay) {
        return transitionOutbox(Objects.requireNonNull(lease, "lease"), OutboxTransition.RETRY, delayMillis(delay));
    }

    @Override
    public boolean abandonOutbox(OutboxLease lease) {
        return transitionOutbox(Objects.requireNonNull(lease, "lease"), OutboxTransition.ABANDON, null);
    }

    @Override
    public int reclaimExpiredOutbox(int limit) {
        requireLimit(limit);
        List<OutboxCandidate> candidates = transactions.withConnection(connection -> {
            List<OutboxCandidate> result = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT event_id, run_id FROM cf_durable_outbox
                 WHERE status = 'DELIVERING' AND lease_until <= CURRENT_TIMESTAMP(3)
                 ORDER BY lease_until, event_id LIMIT ?
                """)) {
                select.setInt(1, limit);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        result.add(new OutboxCandidate(providerUuid(rows, "event_id"), providerUuid(rows, "run_id")));
                    }
                }
            }
            return result;
        });
        int reclaimed = 0;
        for (OutboxCandidate candidate : candidates) {
            boolean committed = transactions.inTransaction(connection -> {
                lockRun(connection, candidate.runId());
                try (PreparedStatement lock = connection.prepareStatement(
                        """
                    SELECT event_id FROM cf_durable_outbox
                     WHERE event_id = ? AND run_id = ? AND status = 'DELIVERING'
                       AND lease_until <= CURRENT_TIMESTAMP(3)
                     FOR UPDATE
                    """)) {
                    setProviderObject(lock, 1, candidate.eventId());
                    setProviderObject(lock, 2, candidate.runId());
                    try (ResultSet rows = lock.executeQuery()) {
                        if (!rows.next()) {
                            return false;
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        """
                    UPDATE cf_durable_outbox
                       SET status = 'PENDING', available_at = CURRENT_TIMESTAMP(3),
                           revision = revision + 1,
                           lease_owner = NULL, lease_token = NULL, lease_until = NULL
                     WHERE event_id = ?
                    """)) {
                    setProviderObject(update, 1, candidate.eventId());
                    requireOne(update.executeUpdate(), "Expired Outbox reclaim lost its authority");
                }
                return true;
            });
            if (committed) {
                reclaimed++;
            }
        }
        return reclaimed;
    }

    @Override
    public int purgeTerminalRuns(Duration retention, int limit) {
        long retentionMillis = retentionMillis(retention);
        requireLimit(limit);
        return transactions.inTransaction(connection -> {
            List<UUID> runIds = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT r.run_id
                  FROM cf_durable_run r
                 WHERE r.status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
                   AND r.completed_at <=
                       TIMESTAMPADD(MICROSECOND, -? * 1000, CURRENT_TIMESTAMP(3))
                   AND NOT EXISTS (
                       SELECT 1 FROM cf_durable_outbox o
                        WHERE o.run_id = r.run_id
                          AND o.status IN ('PENDING', 'DELIVERING'))
                 ORDER BY r.completed_at, r.run_id
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """)) {
                select.setLong(1, retentionMillis);
                select.setInt(2, limit);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        runIds.add(providerUuid(rows, "run_id"));
                    }
                }
            }
            if (runIds.isEmpty()) {
                return 0;
            }

            deleteRunRows(connection, "cf_durable_journal", runIds);
            deleteRunRows(connection, "cf_durable_outbox", runIds);
            deleteRunRows(connection, "cf_durable_wait", runIds);
            deleteRunRows(connection, "cf_durable_effect", runIds);
            int deleted = deleteRunRows(connection, "cf_durable_run", runIds);
            if (deleted != runIds.size()) {
                throw new SQLException("Terminal Run purge lost locked candidates");
            }
            return deleted;
        });
    }

    @Override
    public int purgeConsumedOccurrences(Duration retention, int limit) {
        long retentionMillis = retentionMillis(retention);
        requireLimit(limit);
        return transactions.inTransaction(connection -> {
            List<UUID> waits = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    """
                    SELECT w.wait_id
                      FROM cf_durable_wait w
                     WHERE w.consumed_at <=
                           TIMESTAMPADD(MICROSECOND, -? * 1000, CURRENT_TIMESTAMP(3))
                       AND NOT EXISTS (
                           SELECT 1 FROM cf_durable_outbox o
                            WHERE o.run_id = w.run_id
                              AND o.occurrence_id = w.wait_id
                              AND o.status IN ('PENDING', 'DELIVERING'))
                     ORDER BY w.consumed_at, w.wait_id
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                """)) {
                select.setLong(1, retentionMillis);
                select.setInt(2, limit);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        waits.add(providerUuid(rows, "wait_id"));
                    }
                }
            }
            int deletedWaits = deleteByIds(connection, "cf_durable_wait", "wait_id", waits);
            int remaining = limit - deletedWaits;
            if (remaining == 0) {
                return deletedWaits;
            }
            List<UUID> effects = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    """
                    SELECT e.effect_id
                      FROM cf_durable_effect e
                     WHERE e.consumed_at <=
                           TIMESTAMPADD(MICROSECOND, -? * 1000, CURRENT_TIMESTAMP(3))
                       AND NOT EXISTS (
                           SELECT 1 FROM cf_durable_outbox o
                            WHERE o.run_id = e.run_id
                              AND o.occurrence_id = e.effect_id
                              AND o.status IN ('PENDING', 'DELIVERING'))
                     ORDER BY e.consumed_at, e.effect_id
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                """)) {
                select.setLong(1, retentionMillis);
                select.setInt(2, remaining);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        effects.add(providerUuid(rows, "effect_id"));
                    }
                }
            }
            int deletedEffects = deleteByIds(connection, "cf_durable_effect", "effect_id", effects);
            return deletedWaits + deletedEffects;
        });
    }

    @Override
    public int purgeUnusedProcesses(Duration retention, int limit) {
        long retentionMillis = retentionMillis(retention);
        requireLimit(limit);
        return transactions.inTransaction(connection -> {
            List<UUID> processIds = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    """
                SELECT p.process_id
                  FROM cf_durable_process p
                 WHERE p.registered_at <=
                       TIMESTAMPADD(MICROSECOND, -? * 1000, CURRENT_TIMESTAMP(3))
                   AND NOT EXISTS (
                       SELECT 1 FROM cf_durable_run r
                        WHERE r.process_id = p.process_id)
                   AND NOT EXISTS (
                       SELECT 1 FROM cf_durable_run_recovery_process recovery_process
                        WHERE recovery_process.process_id = p.process_id)
                 ORDER BY p.registered_at, p.process_id
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """)) {
                select.setLong(1, retentionMillis);
                select.setInt(2, limit);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        processIds.add(providerUuid(rows, "process_id"));
                    }
                }
            }
            int deleted = 0;
            try (PreparedStatement delete = connection.prepareStatement(
                    """
                DELETE FROM cf_durable_process p
                 WHERE p.process_id = ?
                   AND NOT EXISTS (
                       SELECT 1 FROM cf_durable_run r
                        WHERE r.process_id = p.process_id)
                   AND NOT EXISTS (
                       SELECT 1 FROM cf_durable_run_recovery_process recovery_process
                        WHERE recovery_process.process_id = p.process_id)
                """)) {
                for (UUID processId : processIds) {
                    setProviderObject(delete, 1, processId);
                    deleted += delete.executeUpdate();
                }
            }
            return deleted;
        });
    }

    private boolean transitionOutbox(OutboxLease lease, OutboxTransition transition, Long delayMillis) {
        return transactions.inTransaction(connection -> {
            if (!lockOutboxLeaseRunFirst(connection, lease)) {
                return false;
            }
            String sql =
                    """
                UPDATE cf_durable_outbox
                   SET status = ?,
                       revision = revision + 1,
                       available_at = CASE WHEN ? IS NULL THEN available_at
                           ELSE TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)) END,
                       completed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                       payload_envelope = CASE
                           WHEN event_type = 'WAIT_COMMITTED' AND ? THEN ?
                           ELSE payload_envelope END,
                       lease_owner = NULL, lease_token = NULL, lease_until = NULL
                 WHERE event_id = ?
                   AND (? <> 'ABANDONED' OR event_type <> 'WAIT_COMMITTED')
                """;
            try (PreparedStatement update = connection.prepareStatement(sql)) {
                update.setString(1, transition.status);
                if (delayMillis == null) {
                    update.setNull(2, Types.BIGINT);
                    update.setNull(3, Types.BIGINT);
                } else {
                    update.setLong(2, delayMillis);
                    update.setLong(3, delayMillis);
                }
                update.setBoolean(4, transition.terminal);
                update.setBoolean(5, transition.terminal);
                update.setBytes(6, EMPTY_ENVELOPE);
                setProviderObject(update, 7, lease.eventId());
                update.setString(8, transition.status);
                return update.executeUpdate() == 1;
            }
        });
    }

    private enum OutboxTransition {
        DELIVER("DELIVERED", true),
        RETRY("PENDING", false),
        ABANDON("ABANDONED", true);
        private final String status;
        private final boolean terminal;

        OutboxTransition(String status, boolean terminal) {
            this.status = status;
            this.terminal = terminal;
        }
    }

    private static boolean lockOutboxLeaseRunFirst(Connection connection, OutboxLease lease) throws SQLException {
        lockRun(connection, uuid(lease.runId()));
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT event_id FROM cf_durable_outbox
             WHERE event_id = ? AND run_id = ? AND status = 'DELIVERING'
               AND lease_token = ? AND lease_until > CURRENT_TIMESTAMP(3)
             FOR UPDATE
            """)) {
            setProviderObject(select, 1, lease.eventId());
            setProviderObject(select, 2, uuid(lease.runId()));
            setProviderObject(select, 3, lease.token());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean transitionEffectUnknown(Connection connection, EffectLease lease, long delayMillis, String fact,
            boolean review, String reason) throws SQLException {
        EffectLeaseRow effect = lockEffectLeaseRunFirst(connection, lease);
        if (effect == null) {
            return false;
        }
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_effect
               SET status = 'UNKNOWN',
                   available_at = TIMESTAMPADD(MICROSECOND, ? * 1000, CURRENT_TIMESTAMP(3)),
                   running_operation = NULL,
                   lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                   unknown_since = COALESCE(unknown_since, CURRENT_TIMESTAMP(3)),
                   review_required_at = CASE WHEN ? THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                   review_reason = CASE WHEN ? THEN ? ELSE NULL END,
                   review_revision = review_revision + CASE WHEN ? THEN 1 ELSE 0 END,
                   updated_at = CURRENT_TIMESTAMP(3)
             WHERE effect_id = ?
            """)) {
            update.setLong(1, delayMillis);
            update.setBoolean(2, review);
            update.setBoolean(3, review);
            update.setString(4, reason);
            update.setBoolean(5, review);
            setProviderObject(update, 6, lease.effectId());
            requireOne(update.executeUpdate(), "Effect UNKNOWN transition lost its authority");
        }
        if (cancelRequested(connection, effect.runId())) {
            settleCancellation(connection, effect.runId());
        } else {
            pauseAtSafePoint(connection, effect.runId());
        }
        appendFact(connection, effect.runId(), fact, null);
        if (review) {
            insertOutbox(connection, effect.runId(), "EFFECT_REVIEW_REQUIRED", lease.effectId(),
                    new Envelope("{}".getBytes(StandardCharsets.UTF_8)));
        }
        return true;
    }

    private Optional<StoredProcess> selectProcess(Connection connection, UUID processId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT process_id, process_code, model_type, definition_bytes, definition_digest, registered_at
              FROM cf_durable_process
             WHERE process_id = ?
            """)) {
            setProviderObject(select, 1, processId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(storedProcess(rows)) : Optional.empty();
            }
        }
    }

    private static Optional<StoredProcess> lockProcessForStart(Connection connection, UUID processId)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT process_id, process_code, model_type, definition_bytes, definition_digest, registered_at
              FROM cf_durable_process
             WHERE process_id = ?
             FOR SHARE
            """)) {
            setProviderObject(select, 1, processId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(storedProcess(rows)) : Optional.empty();
            }
        }
    }

    private static StoredProcess lockProcessesForStart(Connection connection, UUID rootProcessId, List<UUID> processIds)
            throws SQLException {
        StoredProcess root = null;
        for (UUID processId : processIds) {
            StoredProcess stored = lockProcessForStart(connection, processId)
                .orElseThrow(() -> error(DurableErrorCode.PROCESS_NOT_FOUND,
                        "A Durable Process referenced by the Run is not registered"));
            if (processId.equals(rootProcessId)) {
                root = stored;
            }
        }
        if (root == null) {
            throw new IllegalStateException("Run references do not contain the root Process");
        }
        return root;
    }

    private static void insertRunRecoveryProcesses(Connection connection, UUID runId, List<UUID> processIds)
            throws SQLException {
        try (PreparedStatement insert =
                connection.prepareStatement(
                        "INSERT INTO cf_durable_run_recovery_process(run_id, process_id) VALUES (?, ?)")) {
            for (UUID processId : processIds) {
                setProviderObject(insert, 1, runId);
                setProviderObject(insert, 2, processId);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static StoredProcess storedProcess(ResultSet rows) throws SQLException {
        return new StoredProcess(providerUuid(rows, "process_id"), rows.getString("process_code"),
                ProcessModelType.valueOf(rows.getString("model_type")), rows.getBytes("definition_bytes"),
                rows.getString("definition_digest"), instant(rows, "registered_at"));
    }

    private static void bindRunAttribution(PreparedStatement statement, int start, String namespace,
            ProcessRef.Version processVersion) throws SQLException {
        statement.setString(start, namespace);
        if (processVersion == null) {
            statement.setNull(start + 1, Types.VARCHAR);
        } else {
            statement.setString(start + 1, processVersion.version());
        }
    }

    private static ProcessRef.Version processVersion(ResultSet rows, String namespace, String processCode)
            throws SQLException {
        String version = rows.getString("process_version");
        return version == null ? null : ProcessRef.version(namespace, processCode, version);
    }

    private static RunProcess runProcess(ResultSet rows) throws SQLException {
        return runProcess(rows, "process_id");
    }

    private static RunProcess runProcess(ResultSet rows, String processIdColumn) throws SQLException {
        String namespace = rows.getString("namespace");
        String code = rows.getString("process_code");
        return new RunProcess(providerUuid(rows, processIdColumn), namespace, code,
                processVersion(rows, namespace, code));
    }

    private ProcessRunStatus lockRunStatus(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select =
                connection.prepareStatement("SELECT status FROM cf_durable_run WHERE run_id = ? FOR UPDATE")) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw error(DurableErrorCode.RUN_NOT_FOUND, "Run does not exist");
                }
                return ProcessRunStatus.valueOf(rows.getString(1));
            }
        }
    }

    private static void lockRun(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select =
                connection.prepareStatement("SELECT run_id FROM cf_durable_run WHERE run_id = ? FOR UPDATE")) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw error(DurableErrorCode.RUN_NOT_FOUND, "Run does not exist");
                }
            }
        }
    }

    private static void protectRunFromDeletion(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select =
                connection.prepareStatement("SELECT run_id FROM cf_durable_run WHERE run_id = ? FOR SHARE")) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw error(DurableErrorCode.RUN_NOT_FOUND, "Run does not exist");
                }
            }
        }
    }

    private static void lockRuns(Connection connection, Set<UUID> runIds) throws SQLException {
        if (runIds.isEmpty()) {
            return;
        }
        String sql = "SELECT run_id FROM cf_durable_run WHERE run_id IN (" + placeholders(runIds.size())
                + ") ORDER BY run_id FOR UPDATE";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            bindUuids(select, 1, runIds);
            try (ResultSet rows = select.executeQuery()) {
                // Consume the complete result to acquire every existing Run lock in UUID order.
                while (rows.next()) {
                    providerUuid(rows, 1);
                }
            }
        }
    }

    private static void requireRunExists(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM cf_durable_run WHERE run_id = ?")) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw error(DurableErrorCode.RUN_NOT_FOUND, "Run does not exist");
                }
            }
        }
    }

    private RunControlRow lockRunControl(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT status, control_state, control_revision
              FROM cf_durable_run WHERE run_id = ? FOR UPDATE
            """)) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw error(DurableErrorCode.RUN_NOT_FOUND, "Run does not exist");
                }
                return new RunControlRow(ProcessRunStatus.valueOf(rows.getString("status")),
                        rows.getString("control_state"), rows.getLong("control_revision"));
            }
        }
    }

    private static boolean hasIssuedAuthority(Connection connection, UUID runId, ProcessRunStatus status)
            throws SQLException {
        if (status == ProcessRunStatus.RUNNING) {
            return true;
        }
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT EXISTS(SELECT 1 FROM cf_durable_effect
                           WHERE run_id = ? AND status = 'RUNNING')
            """)) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private static boolean hasUnconsumedResolvedOccurrence(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT EXISTS(
                SELECT 1 FROM cf_durable_wait
                 WHERE run_id = ? AND status = 'RESOLVED' AND consumed_at IS NULL
                UNION ALL
                SELECT 1 FROM cf_durable_effect
                 WHERE run_id = ? AND status = 'COMPLETED' AND consumed_at IS NULL)
            """)) {
            setProviderObject(select, 1, runId);
            setProviderObject(select, 2, runId);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private static boolean hasOutstandingOccurrence(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT EXISTS(
                SELECT 1 FROM cf_durable_wait
                 WHERE run_id = ? AND (status = 'ACTIVE'
                    OR (status = 'RESOLVED' AND consumed_at IS NULL))
                UNION ALL
                SELECT 1 FROM cf_durable_effect
                 WHERE run_id = ? AND (status IN ('PENDING', 'RUNNING', 'UNKNOWN')
                    OR (status = 'COMPLETED' AND consumed_at IS NULL)))
            """)) {
            setProviderObject(select, 1, runId);
            setProviderObject(select, 2, runId);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private static void ensureOutstandingCapacity(Connection connection, UUID runId, int additional) throws SQLException {
        if (additional == 0) {
            return;
        }
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT (
                SELECT count(*) FROM cf_durable_wait
                 WHERE run_id = ? AND (status = 'ACTIVE'
                    OR (status = 'RESOLVED' AND consumed_at IS NULL)))
                 + (
                SELECT count(*) FROM cf_durable_effect
                 WHERE run_id = ? AND (status IN ('PENDING', 'RUNNING', 'UNKNOWN')
                    OR (status = 'COMPLETED' AND consumed_at IS NULL)))
            """)) {
            setProviderObject(select, 1, runId);
            setProviderObject(select, 2, runId);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                long existing = rows.getLong(1);
                if (existing + additional > MAX_OUTSTANDING_OCCURRENCES) {
                    throw error(DurableErrorCode.INVALID_ARGUMENT,
                            "Run would exceed the bounded outstanding-occurrence limit");
                }
            }
        }
    }

    /**
     * Revokes work that is still provably pre-dispatch and terminalizes only after every possible
     * external dispatch has a known outcome.
     */
    private void settleCancellation(Connection connection, UUID runId) throws SQLException {
        cancelRevocableOccurrences(connection, runId);
        settleAllWaitCommittedOutbox(connection, runId, "ABANDONED");
        if (hasUncertainEffectAuthority(connection, runId)) {
            try (PreparedStatement update = connection.prepareStatement(
                    """
                UPDATE cf_durable_run
                   SET lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                       status = CASE WHEN status = 'RUNNING' THEN 'WAITING' ELSE status END,
                       turn_fault_streak = 0, retry_code = NULL, retry_observed_at = NULL,
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE run_id = ? AND status IN ('RUNNABLE', 'RUNNING', 'WAITING')
                """)) {
                setProviderObject(update, 1, runId);
                requireOne(update.executeUpdate(), "Cancellation owner Run is no longer active");
            }
            pauseAtSafePoint(connection, runId);
            return;
        }
        terminalizeRun(connection, runId, "CANCELLED", null, null, null);
    }

    private static boolean hasUncertainEffectAuthority(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT EXISTS(
                SELECT 1 FROM cf_durable_effect
                 WHERE run_id = ? AND status IN ('RUNNING', 'UNKNOWN'))
            """)) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private static boolean hasOtherUncertainEffect(Connection connection, UUID runId, UUID effectId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT EXISTS(
                SELECT 1 FROM cf_durable_effect
                 WHERE run_id = ? AND effect_id <> ? AND status IN ('RUNNING', 'UNKNOWN'))
            """)) {
            setProviderObject(select, 1, runId);
            setProviderObject(select, 2, effectId);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private static void cancelRevocableOccurrences(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_wait
               SET status = 'CANCELLED', resolved_at = CURRENT_TIMESTAMP(3)
             WHERE run_id = ? AND status = 'ACTIVE'
            """)) {
            setProviderObject(update, 1, runId);
            update.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_effect
               SET status = 'CANCELLED', readiness_code = NULL,
                   completed_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
             WHERE run_id = ? AND status = 'PENDING'
            """)) {
            setProviderObject(update, 1, runId);
            update.executeUpdate();
        }
    }

    private static void cancelActiveOccurrences(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_wait
               SET status = 'CANCELLED', resolved_at = CURRENT_TIMESTAMP(3)
             WHERE run_id = ? AND status = 'ACTIVE'
            """)) {
            setProviderObject(update, 1, runId);
            update.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_effect
               SET status = 'CANCELLED', running_operation = NULL,
                   lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                   unknown_since = NULL, review_required_at = NULL, review_reason = NULL,
                   readiness_code = NULL, completed_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
             WHERE run_id = ? AND status IN ('PENDING', 'RUNNING', 'UNKNOWN')
            """)) {
            setProviderObject(update, 1, runId);
            update.executeUpdate();
        }
    }

    private void terminalizeRun(Connection connection, UUID runId, String status, Envelope result, String failureCode,
            String failureMessage) throws SQLException {
        cancelActiveOccurrences(connection, runId);
        settleAllWaitCommittedOutbox(connection, runId, "ABANDONED");
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_run
               SET status = ?, control_state = 'ACTIVE', turn_fault_streak = 0,
                   retry_code = NULL, retry_observed_at = NULL,
                   lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                   result_envelope = ?, failure_code = ?, failure_message = ?,
                   updated_at = CURRENT_TIMESTAMP(3), completed_at = CURRENT_TIMESTAMP(3)
             WHERE run_id = ? AND status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
            """)) {
            update.setString(1, status);
            if (result == null) {
                update.setNull(2, Types.BINARY);
            } else {
                update.setBytes(2, result.bytes());
            }
            setNullableString(update, 3, failureCode);
            setNullableString(update, 4, failureMessage);
            setProviderObject(update, 5, runId);
            requireOne(update.executeUpdate(), "Run is already terminal");
        }
        String fact = "RUN_" + status;
        appendFact(connection, runId, fact, null);
        insertOutbox(connection, runId, fact, null, new Envelope("{}".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Ends bearer-token delivery for exactly one fulfilled Wait occurrence.
     */
    private static void settleWaitCommittedOutbox(Connection connection, UUID runId, UUID waitId, String terminalStatus)
            throws SQLException {
        settleWaitCommittedOutbox(connection, runId, Objects.requireNonNull(waitId, "waitId"), terminalStatus, false);
    }

    /**
     * Revokes every remaining Wait authority when its owning Run terminalizes.
     */
    private static void settleAllWaitCommittedOutbox(Connection connection, UUID runId, String terminalStatus)
            throws SQLException {
        settleWaitCommittedOutbox(connection, runId, null, terminalStatus, true);
    }

    private static void settleWaitCommittedOutbox(Connection connection, UUID runId, UUID waitId, String terminalStatus,
            boolean allOccurrences) throws SQLException {
        if (!"DELIVERED".equals(terminalStatus) && !"ABANDONED".equals(terminalStatus)) {
            throw new IllegalArgumentException("WAIT_COMMITTED terminal status must be DELIVERED or ABANDONED");
        }
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_outbox
               SET status = ?, revision = revision + 1,
                   completed_at = CURRENT_TIMESTAMP(3),
                   payload_envelope = ?,
                   lease_owner = NULL, lease_token = NULL, lease_until = NULL
             WHERE run_id = ? AND event_type = 'WAIT_COMMITTED'
               AND (? OR occurrence_id = ?)
               AND status IN ('PENDING', 'DELIVERING')
            """)) {
            update.setString(1, terminalStatus);
            update.setBytes(2, EMPTY_ENVELOPE);
            setProviderObject(update, 3, runId);
            update.setBoolean(4, allOccurrences);
            if (waitId == null) {
                update.setNull(5, Types.VARCHAR);
            } else {
                setProviderObject(update, 5, waitId);
            }
            update.executeUpdate();
        }
        // Scrub terminal rows for the same occurrence as a defense against ACK-loss history.
        try (PreparedStatement scrub = connection.prepareStatement(
                """
            UPDATE cf_durable_outbox
               SET payload_envelope = ?
             WHERE run_id = ? AND event_type = 'WAIT_COMMITTED'
               AND (? OR occurrence_id = ?)
               AND status IN ('DELIVERED', 'ABANDONED')
               AND payload_envelope <> ?
            """)) {
            scrub.setBytes(1, EMPTY_ENVELOPE);
            setProviderObject(scrub, 2, runId);
            scrub.setBoolean(3, allOccurrences);
            if (waitId == null) {
                scrub.setNull(4, Types.VARCHAR);
            } else {
                setProviderObject(scrub, 4, waitId);
            }
            scrub.setBytes(5, EMPTY_ENVELOPE);
            scrub.executeUpdate();
        }
    }

    private EffectRow lockEffect(Connection connection, UUID effectId, UUID runId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT status, review_revision, result_envelope FROM cf_durable_effect
             WHERE effect_id = ? AND run_id = ? FOR UPDATE
            """)) {
            setProviderObject(select, 1, effectId);
            setProviderObject(select, 2, runId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw error(DurableErrorCode.EFFECT_NOT_FOUND, "Effect does not exist");
                }
                return new EffectRow(rows.getString("status"), rows.getLong("review_revision"),
                        rows.getBytes("result_envelope"));
            }
        }
    }

    private void completeEffectByOperator(Connection connection, UUID runId, UUID effectId, byte[] result)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_effect
               SET status = 'COMPLETED', result_envelope = ?, completed_at = CURRENT_TIMESTAMP(3),
                   unknown_since = NULL, review_required_at = NULL, review_reason = NULL,
                   readiness_code = NULL, updated_at = CURRENT_TIMESTAMP(3)
             WHERE effect_id = ? AND status = 'UNKNOWN'
            """)) {
            update.setBytes(1, result);
            setProviderObject(update, 2, effectId);
            requireOne(update.executeUpdate(), "Effect resolution lost its authority");
        }
        advanceAfterEffect(connection, runId);
    }

    private void retryEffectByOperator(Connection connection, UUID runId, UUID effectId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_effect
               SET status = 'PENDING', available_at = CURRENT_TIMESTAMP(3), unknown_since = NULL,
                   review_required_at = NULL, review_reason = NULL, readiness_code = NULL,
                   updated_at = CURRENT_TIMESTAMP(3)
             WHERE effect_id = ? AND status = 'UNKNOWN'
            """)) {
            setProviderObject(update, 1, effectId);
            requireOne(update.executeUpdate(), "Effect retry resolution lost its authority");
        }
        if (cancelRequested(connection, runId)) {
            settleCancellation(connection, runId);
        }
    }

    private void failRunFromEffect(Connection connection, UUID runId, UUID effectId, String code, String message)
            throws SQLException {
        if (cancelRequested(connection, runId)) {
            settleCancellation(connection, runId);
            return;
        }
        if (hasOtherUncertainEffect(connection, runId, effectId)) {
            throw error(DurableErrorCode.CONCURRENT_MODIFICATION,
                    "Other uncertain Effect authority must be resolved before failing the Run");
        }
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_effect
               SET status = 'CANCELLED', unknown_since = NULL, readiness_code = NULL,
                   review_required_at = NULL, review_reason = NULL,
                   completed_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
             WHERE effect_id = ? AND status = 'UNKNOWN'
            """)) {
            setProviderObject(update, 1, effectId);
            requireOne(update.executeUpdate(), "Effect failure resolution lost its authority");
        }
        terminalizeRun(connection, runId, "FAILED", null, code, message);
    }

    private LockedRun lockRunLease(Connection connection, RunLease lease) throws SQLException {
        // Evaluate authority time only after any wait for the Run row lock has finished.
        lockRuns(connection, Set.of(uuid(lease.runId())));
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT occurrence_sequence, cancel_requested_at IS NOT NULL AS cancel_requested
              FROM cf_durable_run
             WHERE run_id = ? AND status = 'RUNNING' AND lease_token = ?
               AND lease_until > CURRENT_TIMESTAMP(3)
             FOR UPDATE
            """)) {
            setProviderObject(select, 1, uuid(lease.runId()));
            setProviderObject(select, 2, lease.token());
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new LockedRun(rows.getLong("occurrence_sequence"), rows.getBoolean("cancel_requested"));
            }
        }
    }

    private RunClaim readRunClaim(Connection connection, UUID runId, UUID token) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT process_id, process_code, namespace, process_version,
                   continuation_envelope, occurrence_sequence,
                   cancel_requested_at, lease_until
              FROM cf_durable_run WHERE run_id = ? AND lease_token = ?
            """)) {
            setProviderObject(select, 1, runId);
            setProviderObject(select, 2, token);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("Claimed Run disappeared");
                }
                long sequence = rows.getLong("occurrence_sequence");
                return new RunClaim(new RunLease(runId(runId), token), runProcess(rows),
                        envelope(rows.getBytes("continuation_envelope")), sequence,
                        readOccurrenceResults(connection, runId), nullableInstant(rows, "cancel_requested_at") != null,
                        instant(rows, "lease_until"));
            }
        }
    }

    private List<OccurrenceResult> readOccurrenceResults(Connection connection, UUID runId) throws SQLException {
        List<OccurrenceResult> results = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT occurrence_kind, occurrence_id, occurrence_sequence, process_id,
                   process_invocation_id, frontier_id, element_id,
                   event_name, resolution_kind, result_envelope, scheduled_at, due_at, resolved_at
              FROM (
                    SELECT kind AS occurrence_kind, wait_id AS occurrence_id,
                           occurrence_sequence, process_id, process_invocation_id,
                           frontier_id, element_id, event_name, resolution_kind,
                           result_envelope,
                           created_at AS scheduled_at, due_at, resolved_at
                      FROM cf_durable_wait
                     WHERE run_id = ? AND status = 'RESOLVED' AND consumed_at IS NULL
                    UNION ALL
                    SELECT 'EFFECT' AS occurrence_kind, effect_id AS occurrence_id,
                           occurrence_sequence, process_id, process_invocation_id,
                           frontier_id, element_id, CAST(NULL AS CHAR) AS event_name,
                           CAST(NULL AS CHAR) AS resolution_kind, result_envelope, created_at AS scheduled_at,
                           CAST(NULL AS DATETIME(3)) AS due_at, completed_at AS resolved_at
                      FROM cf_durable_effect
                     WHERE run_id = ? AND status = 'COMPLETED' AND consumed_at IS NULL
                   ) resolved
             ORDER BY occurrence_sequence, occurrence_id
             LIMIT 256
            """)) {
            setProviderObject(select, 1, runId);
            setProviderObject(select, 2, runId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    String kind = rows.getString("occurrence_kind");
                    UUID occurrenceId = providerUuid(rows, "occurrence_id");
                    long sequence = rows.getLong("occurrence_sequence");
                    UUID processId = providerUuid(rows, "process_id");
                    long invocationId = rows.getLong("process_invocation_id");
                    if ("EXTERNAL".equals(kind)) {
                        boolean expired = "EXPIRED".equals(rows.getString("resolution_kind"));
                        results.add(
                                new WaitResult(new OccurrenceKey(OccurrenceKind.WAIT, occurrenceId), sequence, processId,
                                        invocationId, rows.getString("frontier_id"), rows.getString("element_id"),
                                        rows.getString("event_name"),
                                        expired ? WaitResolution.EXPIRED : WaitResolution.COMPLETED,
                                        envelope(rows.getBytes("result_envelope")),
                                        expired ? instant(rows, "due_at") : null, instant(rows, "resolved_at")));
                    } else if ("TIMER".equals(kind)) {
                        results.add(
                                new TimerResult(new OccurrenceKey(OccurrenceKind.TIMER, occurrenceId), sequence,
                                        processId, invocationId, rows.getString("frontier_id"),
                                        rows.getString("element_id"), envelope(rows.getBytes("result_envelope")),
                                        instant(rows, "scheduled_at"), instant(rows, "due_at"),
                                        instant(rows, "resolved_at")));
                    } else if ("EFFECT".equals(kind)) {
                        results.add(
                                new EffectResult(new OccurrenceKey(OccurrenceKind.EFFECT, occurrenceId), sequence,
                                        processId, invocationId, rows.getString("frontier_id"),
                                        rows.getString("element_id"), envelope(rows.getBytes("result_envelope")),
                                        instant(rows, "resolved_at")));
                    }
                }
            }
        }
        return List.copyOf(results);
    }

    private EffectClaim readEffectClaim(Connection connection, UUID effectId, UUID token, EffectOperation operation)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT e.run_id, r.process_id AS root_process_id, r.process_code,
                   r.namespace, r.process_version, e.process_id,
                   e.frontier_id, e.element_id, e.dispatch_attempts, e.reconcile_attempts,
                   e.recovery_mode, e.max_attempts, e.max_reconcile_attempts,
                   e.retry_delay_ms, e.recovery_deadline_ms,
                   e.input_envelope, e.created_at, e.unknown_since,
                   CURRENT_TIMESTAMP(3) AS authority_time,
                   e.lease_until
              FROM cf_durable_effect e
              JOIN cf_durable_run r ON r.run_id = e.run_id
             WHERE e.effect_id = ? AND e.lease_token = ?
            """)) {
            setProviderObject(select, 1, effectId);
            setProviderObject(select, 2, token);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("Claimed Effect disappeared");
                }
                UUID runId = providerUuid(rows, "run_id");
                return new EffectClaim(new EffectLease(runId(runId), effectId, token), runId(runId),
                        runProcess(rows, "root_process_id"), providerUuid(rows, "process_id"),
                        rows.getString("frontier_id"), rows.getString("element_id"), operation,
                        rows.getInt("dispatch_attempts"), rows.getInt("reconcile_attempts"),
                        envelope(rows.getBytes("input_envelope")), effectRecoveryPlan(rows), instant(rows, "created_at"),
                        nullableInstant(rows, "unknown_since"), instant(rows, "authority_time"),
                        instant(rows, "lease_until"));
            }
        }
    }

    private static EffectRecoveryPlan effectRecoveryPlan(ResultSet rows) throws SQLException {
        EffectRecoveryPlan.Mode mode = EffectRecoveryPlan.Mode.valueOf(rows.getString("recovery_mode"));
        int maxAttempts = rows.getInt("max_attempts");
        int maxReconcileAttempts = rows.getInt("max_reconcile_attempts");
        Duration recoveryDelay = nullableDurationMillis(rows, "retry_delay_ms");
        Duration maxRecoveryDuration = nullableDurationMillis(rows, "recovery_deadline_ms");
        return new EffectRecoveryPlan(mode, maxAttempts, maxReconcileAttempts, recoveryDelay, maxRecoveryDuration);
    }

    private static Duration nullableDurationMillis(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : Duration.ofMillis(value);
    }

    private EffectLeaseRow lockEffectLease(Connection connection, EffectLease lease) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT run_id, running_operation FROM cf_durable_effect
             WHERE effect_id = ? AND run_id = ? AND status = 'RUNNING' AND lease_token = ?
               AND lease_until > CURRENT_TIMESTAMP(3)
             FOR UPDATE
            """)) {
            setProviderObject(select, 1, lease.effectId());
            setProviderObject(select, 2, uuid(lease.runId()));
            setProviderObject(select, 3, lease.token());
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new EffectLeaseRow(providerUuid(rows, "run_id"), rows.getString("running_operation"));
            }
        }
    }

    private EffectLeaseRow lockEffectLeaseRunFirst(Connection connection, EffectLease lease) throws SQLException {
        UUID runId = uuid(lease.runId());
        lockRun(connection, runId);
        EffectLeaseRow effect = lockEffectLease(connection, lease);
        if (effect != null && !runId.equals(effect.runId())) {
            throw new IllegalStateException("Effect owner changed while locking immutable authority");
        }
        return effect;
    }

    private void advanceAfterEffect(Connection connection, UUID runId) throws SQLException {
        if (cancelRequested(connection, runId)) {
            settleCancellation(connection, runId);
            return;
        }
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_run
               SET available_at = CASE WHEN status = 'WAITING'
                                       THEN CURRENT_TIMESTAMP(3) ELSE available_at END,
                   status = CASE WHEN status = 'WAITING' THEN 'RUNNABLE' ELSE status END,
                   control_state = CASE WHEN control_state = 'PAUSE_REQUESTED'
                                         AND status <> 'RUNNING'
                                         AND NOT EXISTS(
                                             SELECT 1 FROM cf_durable_effect
                                              WHERE run_id = ? AND status = 'RUNNING')
                                        THEN 'PAUSED' ELSE control_state END,
                   updated_at = CURRENT_TIMESTAMP(3)
             WHERE run_id = ? AND status IN ('RUNNABLE', 'RUNNING', 'WAITING')
            """)) {
            setProviderObject(update, 1, runId);
            setProviderObject(update, 2, runId);
            requireOne(update.executeUpdate(), "Effect owner Run is no longer active");
        }
    }

    private static void pauseAtSafePoint(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                """
            UPDATE cf_durable_run
               SET control_state = CASE WHEN control_state = 'PAUSE_REQUESTED'
                                         AND status <> 'RUNNING'
                                         AND NOT EXISTS(
                                             SELECT 1 FROM cf_durable_effect
                                              WHERE run_id = ? AND status = 'RUNNING')
                                        THEN 'PAUSED' ELSE control_state END,
                   updated_at = CURRENT_TIMESTAMP(3)
             WHERE run_id = ?
            """)) {
            setProviderObject(update, 1, runId);
            setProviderObject(update, 2, runId);
            update.executeUpdate();
        }
    }

    private static boolean cancelRequested(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select =
                connection.prepareStatement(
                        "SELECT cancel_requested_at IS NOT NULL FROM cf_durable_run WHERE run_id = ?")) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw error(DurableErrorCode.RUN_NOT_FOUND, "Run does not exist");
                }
                return rows.getBoolean(1);
            }
        }
    }

    private static List<UUID> lockedIds(Connection connection, String sql, int limit) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, limit);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    ids.add(providerUuid(rows, 1));
                }
            }
        }
        return ids;
    }

    private static void appendFact(Connection connection, UUID runId, String type, Envelope fact) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                """
            INSERT INTO cf_durable_journal(run_id, sequence, fact_type, fact_envelope)
            SELECT ?, COALESCE(MAX(sequence), 0) + 1, ?, ?
              FROM cf_durable_journal WHERE run_id = ?
            """)) {
            setProviderObject(insert, 1, runId);
            insert.setString(2, type);
            if (fact == null) {
                insert.setNull(3, Types.BINARY);
            } else {
                insert.setBytes(3, fact.bytes());
            }
            setProviderObject(insert, 4, runId);
            insert.executeUpdate();
        }
    }

    private static Envelope turnFaultBackoffMilestone(int consecutiveTurnFaults) {
        String payload = "{\"consecutiveTurnFaults\":" + consecutiveTurnFaults + '}';
        return new Envelope(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static void insertOutbox(Connection connection, UUID runId, String type, UUID occurrenceId,
            Envelope payload) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                """
            INSERT INTO cf_durable_outbox(
                event_id, run_id, event_type, occurrence_id, payload_envelope)
            VALUES (?, ?, ?, ?, ?)
            """)) {
            setProviderObject(insert, 1, UUID.randomUUID());
            setProviderObject(insert, 2, runId);
            insert.setString(3, type);
            if (occurrenceId == null) {
                insert.setNull(4, Types.OTHER);
            } else {
                setProviderObject(insert, 4, occurrenceId);
            }
            insert.setBytes(5, payload.bytes());
            insert.executeUpdate();
        }
    }

    private Optional<OutboxEvent> selectOutbox(Connection connection, UUID eventId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT o.run_id, r.process_code, r.namespace, r.process_version,
                   o.event_id, o.event_type, o.occurrence_id,
                   o.status, o.attempt_count, o.revision,
                   o.available_at, o.created_at, o.completed_at
              FROM cf_durable_outbox o
              JOIN cf_durable_run r ON r.run_id = o.run_id
             WHERE o.event_id = ?
            """)) {
            setProviderObject(select, 1, eventId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(outboxView(rows)) : Optional.empty();
            }
        }
    }

    private OutboxEvent requireOutbox(Connection connection, UUID eventId) throws SQLException {
        return selectOutbox(connection, eventId)
            .orElseThrow(() -> error(DurableErrorCode.OUTBOX_EVENT_NOT_FOUND, "Outbox event does not exist"));
    }

    private static OutboxAuthority lockOutbox(Connection connection, UUID eventId, UUID runId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT event_type, status, revision FROM cf_durable_outbox
             WHERE event_id = ? AND run_id = ? FOR UPDATE
            """)) {
            setProviderObject(select, 1, eventId);
            setProviderObject(select, 2, runId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw error(DurableErrorCode.OUTBOX_EVENT_NOT_FOUND, "Outbox event does not exist");
                }
                return new OutboxAuthority(rows.getString("event_type"), rows.getString("status"),
                        rows.getLong("revision"));
            }
        }
    }

    private static UUID outboxRunId(Connection connection, UUID eventId) throws SQLException {
        try (PreparedStatement select =
                connection.prepareStatement("SELECT run_id FROM cf_durable_outbox WHERE event_id = ?")) {
            setProviderObject(select, 1, eventId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw error(DurableErrorCode.OUTBOX_EVENT_NOT_FOUND, "Outbox event does not exist");
                }
                return providerUuid(rows, "run_id");
            }
        }
    }

    private Optional<ProcessRun> selectRunView(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(RUN_VIEW + " WHERE r.run_id = ?")) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(runView(rows)) : Optional.empty();
            }
        }
    }

    private ProcessRun requireRunView(Connection connection, UUID runId) throws SQLException {
        return selectRunView(connection, runId)
            .orElseThrow(() -> error(DurableErrorCode.RUN_NOT_FOUND, "Run does not exist"));
    }

    private static ProcessRun runView(ResultSet rows) throws SQLException {
        ProcessRunStatus status = ProcessRunStatus.valueOf(rows.getString("status"));
        ActiveWorkSummary activeWork = new ActiveWorkSummary(rows.getInt("active_external_waits"),
                rows.getInt("active_timers"), rows.getInt("active_effects"), rows.getInt("running_effects"),
                rows.getInt("unknown_effects"), rows.getInt("review_required_effects"));
        String retryCode = rows.getString("retry_code");
        ProcessRunRetryState retry = retryCode == null
                ? null
                : new ProcessRunRetryState(RunRetryCode.valueOf(retryCode), rows.getInt("turn_fault_streak"),
                        instant(rows, "retry_observed_at"));
        return new ProcessRun(runId(providerUuid(rows, "run_id")), rows.getString("namespace"),
                rows.getString("process_code"),
                processVersion(rows, rows.getString("namespace"), rows.getString("process_code")), status,
                new ProcessRunControl(ProcessRunControlState.valueOf(rows.getString("control_state")),
                        rows.getLong("control_revision")), activeWork, instant(rows, "available_at"), retry,
                rows.getString("failure_code"), rows.getString("failure_message"),
                nullableInstant(rows, "cancel_requested_at"), instant(rows, "created_at"), instant(rows, "updated_at"),
                nullableInstant(rows, "completed_at"));
    }

    private RunPage listRuns(Connection connection, RunQuery query) throws SQLException {
        StringBuilder sql = new StringBuilder(RUN_VIEW).append(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (query.namespace() != null) {
            sql.append(" AND r.namespace = ?");
            parameters.add(query.namespace());
        }
        if (query.code() != null) {
            sql.append(" AND r.process_code = ?");
            parameters.add(query.code());
        }
        if (!query.statuses().isEmpty()) {
            sql
                .append(" AND r.status IN (")
                .append(String.join(",", Collections.nCopies(query.statuses().size(), "?")))
                .append(')');
            query
                .statuses()
                .forEach(status -> parameters.add(status.name()));
        }
        if (query.beforeCreatedAt() != null) {
            sql.append(" AND (r.created_at, r.run_id) < (?, ?)");
            parameters.add(query.beforeCreatedAt());
            parameters.add(uuid(query.beforeRunId()));
        }
        sql.append(" ORDER BY r.created_at DESC, r.run_id DESC LIMIT ?");
        parameters.add(query.limit() + 1);
        List<ProcessRun> items = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(sql.toString())) {
            bindParameters(select, parameters);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    items.add(runView(rows));
                }
            }
        }
        Instant nextCreatedAt = null;
        ProcessRunId nextRunId = null;
        if (items.size() > query.limit()) {
            items.remove(items.size() - 1);
            ProcessRun last = items.get(items.size() - 1);
            nextCreatedAt = last.createdAt();
            nextRunId = last.runId();
        }
        return new RunPage(items, nextCreatedAt, nextRunId);
    }

    private TimelinePage listTimeline(Connection connection, TimelineQuery query) throws SQLException {
        UUID runId = uuid(query.runId());
        protectRunFromDeletion(connection, runId);
        long snapshot = query.snapshotSequence();
        if (snapshot == 0) {
            try (PreparedStatement select =
                    connection.prepareStatement(
                            "SELECT COALESCE(MAX(sequence), 0) FROM cf_durable_journal WHERE run_id = ?")) {
                setProviderObject(select, 1, runId);
                try (ResultSet rows = select.executeQuery()) {
                    rows.next();
                    snapshot = rows.getLong(1);
                }
            }
        }
        List<JournalFact> items = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT sequence, fact_type, fact_envelope, occurred_at
              FROM cf_durable_journal
             WHERE run_id = ? AND sequence > ? AND sequence <= ?
             ORDER BY sequence LIMIT ?
            """)) {
            setProviderObject(select, 1, runId);
            select.setLong(2, query.afterSequence());
            select.setLong(3, snapshot);
            select.setInt(4, query.limit() + 1);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    byte[] bytes = rows.getBytes("fact_envelope");
                    items.add(
                            new JournalFact(rows.getLong("sequence"), rows.getString("fact_type"),
                                    bytes == null ? null : envelope(bytes), instant(rows, "occurred_at")));
                }
            }
        }
        Long next = null;
        if (items.size() > query.limit()) {
            items.remove(items.size() - 1);
            next = items.get(items.size() - 1).sequence();
        }
        return new TimelinePage(snapshot, items, next);
    }

    private ActiveWorkPage listActiveWork(Connection connection, ActiveWorkQuery query) throws SQLException {
        UUID runId = uuid(query.runId());
        requireRunExists(connection, runId);
        List<ActiveWork> items = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                """
            SELECT work_kind, frontier_id, element_id, occurrence_sequence, event_name,
                   created_at, due_at, effect_id, effect_status,
                   dispatch_attempts, reconcile_attempts, readiness_code, next_attempt_at,
                   review_required_at, review_reason, review_revision
              FROM (
                    SELECT kind AS work_kind, frontier_id, element_id, occurrence_sequence,
                           event_name, created_at, due_at,
                           CAST(NULL AS CHAR(36)) AS effect_id, CAST(NULL AS CHAR) AS effect_status,
                           CAST(NULL AS SIGNED) AS dispatch_attempts,
                           CAST(NULL AS SIGNED) AS reconcile_attempts,
                           CAST(NULL AS CHAR) AS readiness_code,
                           CAST(NULL AS DATETIME(3)) AS next_attempt_at,
                           CAST(NULL AS DATETIME(3)) AS review_required_at,
                           CAST(NULL AS CHAR) AS review_reason,
                           CAST(NULL AS SIGNED) AS review_revision
                      FROM cf_durable_wait
                     WHERE run_id = ? AND status = 'ACTIVE'
                    UNION ALL
                    SELECT 'EFFECT' AS work_kind, frontier_id, element_id, occurrence_sequence,
                           CAST(NULL AS CHAR) AS event_name, created_at,
                           CAST(NULL AS DATETIME(3)) AS due_at, effect_id,
                           status AS effect_status, dispatch_attempts,
                           reconcile_attempts, readiness_code,
                           CASE WHEN status IN ('PENDING', 'UNKNOWN')
                                     AND review_required_at IS NULL
                                THEN available_at END AS next_attempt_at,
                           review_required_at, review_reason, review_revision
                      FROM cf_durable_effect
                     WHERE run_id = ? AND status IN ('PENDING', 'RUNNING', 'UNKNOWN')
                   ) work
             WHERE occurrence_sequence > ?
             ORDER BY occurrence_sequence
             LIMIT ?
            """)) {
            setProviderObject(select, 1, runId);
            setProviderObject(select, 2, runId);
            select.setLong(3, query.afterOccurrenceSequence());
            select.setInt(4, query.limit() + 1);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    String kind = rows.getString("work_kind");
                    if ("EFFECT".equals(kind)) {
                        String readinessCode = rows.getString("readiness_code");
                        items.add(
                                new ActiveEffect(providerUuid(rows, "effect_id").toString(),
                                        rows.getString("frontier_id"), rows.getString("element_id"),
                                        EffectExecutionStatus.valueOf(rows.getString("effect_status")),
                                        rows.getLong("occurrence_sequence"), rows.getInt("dispatch_attempts"),
                                        rows.getInt("reconcile_attempts"),
                                        readinessCode == null ? null : EffectReadinessCode.valueOf(readinessCode),
                                        nullableInstant(rows, "next_attempt_at"),
                                        nullableInstant(rows, "review_required_at"), rows.getString("review_reason"),
                                        rows.getLong("review_revision")));
                    } else {
                        if ("TIMER".equals(kind)) {
                            items.add(
                                    new ActiveTimer(rows.getString("frontier_id"), rows.getString("element_id"),
                                            rows.getLong("occurrence_sequence"), instant(rows, "created_at"),
                                            nullableInstant(rows, "due_at")));
                        } else {
                            items.add(
                                    new ActiveWait(rows.getString("frontier_id"), rows.getString("element_id"),
                                            rows.getString("event_name"), rows.getLong("occurrence_sequence"),
                                            instant(rows, "created_at"), nullableInstant(rows, "due_at")));
                        }
                    }
                }
            }
        }
        Long next = null;
        if (items.size() > query.limit()) {
            items.remove(items.size() - 1);
            next = items.get(items.size() - 1).occurrenceSequence();
        }
        return new ActiveWorkPage(items, next);
    }

    private OutboxPage listOutbox(Connection connection, OutboxQuery query) throws SQLException {
        StringBuilder sql = new StringBuilder(
                """
            SELECT o.run_id, r.process_code, r.namespace, r.process_version,
                   o.event_id, o.event_type, o.occurrence_id,
                   o.status, o.attempt_count, o.revision,
                   o.available_at, o.created_at, o.completed_at
              FROM cf_durable_outbox o
              JOIN cf_durable_run r ON r.run_id = o.run_id
             WHERE 1 = 1
            """);
        List<Object> parameters = new ArrayList<>();
        if (query.namespace() != null) {
            sql.append(" AND r.namespace = ?");
            parameters.add(query.namespace());
        }
        if (query.code() != null) {
            sql.append(" AND r.process_code = ?");
            parameters.add(query.code());
        }
        if (!query.statuses().isEmpty()) {
            sql
                .append(" AND o.status IN (")
                .append(String.join(",", Collections.nCopies(query.statuses().size(), "?")))
                .append(')');
            query
                .statuses()
                .forEach(status -> parameters.add(status.name()));
        }
        if (query.beforeCreatedAt() != null) {
            sql.append(" AND (o.created_at, o.event_id) < (?, ?)");
            parameters.add(query.beforeCreatedAt());
            parameters.add(query.beforeEventId());
        }
        sql.append(" ORDER BY o.created_at DESC, o.event_id DESC LIMIT ?");
        parameters.add(query.limit() + 1);
        List<OutboxEvent> items = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(sql.toString())) {
            bindParameters(select, parameters);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    items.add(outboxView(rows));
                }
            }
        }
        Instant nextCreatedAt = null;
        UUID nextEventId = null;
        if (items.size() > query.limit()) {
            items.remove(items.size() - 1);
            OutboxEvent last = items.get(items.size() - 1);
            nextCreatedAt = last.createdAt();
            nextEventId = UUID.fromString(last.eventId());
        }
        return new OutboxPage(items, nextCreatedAt, nextEventId);
    }

    private static OutboxEvent outboxView(ResultSet rows) throws SQLException {
        return new OutboxEvent(runId(providerUuid(rows, "run_id")), rows.getString("namespace"),
                rows.getString("process_code"),
                processVersion(rows, rows.getString("namespace"), rows.getString("process_code")),
                providerUuid(rows, "event_id").toString(), rows.getString("event_type"),
                nullableUuidString(providerUuid(rows, "occurrence_id")),
                OutboxEventStatus.valueOf(rows.getString("status")), rows.getInt("attempt_count"),
                rows.getLong("revision"), instant(rows, "available_at"), instant(rows, "created_at"),
                nullableInstant(rows, "completed_at"));
    }

    private static void bindParameters(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            int jdbcIndex = index + 1;
            if (value instanceof Instant instant) {
                statement.setTimestamp(jdbcIndex, timestamp(instant), utcCalendar());
            } else if (value instanceof UUID uuid) {
                setProviderObject(statement, jdbcIndex, uuid);
            } else if (value instanceof Integer integer) {
                statement.setInt(jdbcIndex, integer);
            } else {
                setProviderObject(statement, jdbcIndex, value);
            }
        }
    }

    private static int deleteRunRows(Connection connection, String relation, List<UUID> runIds) throws SQLException {
        String table = switch (relation) {
            case "cf_durable_journal", "cf_durable_outbox", "cf_durable_wait", "cf_durable_effect", "cf_durable_run" -> relation;
            default -> throw new IllegalArgumentException("Unsupported Durable retention relation: " + relation);
        };
        String parameters = String.join(", ", Collections.nCopies(runIds.size(), "?"));
        try (PreparedStatement delete =
                connection.prepareStatement("DELETE FROM " + table + " WHERE run_id IN (" + parameters + ')')) {
            for (int index = 0; index < runIds.size(); index++) {
                setProviderObject(delete, index + 1, runIds.get(index));
            }
            return delete.executeUpdate();
        }
    }

    private static int deleteByIds(Connection connection, String relation, String idColumn, List<UUID> ids)
            throws SQLException {
        if (ids.isEmpty()) {
            return 0;
        }
        String table = switch (relation) {
            case "cf_durable_wait", "cf_durable_effect" -> relation;
            default -> throw new IllegalArgumentException("Unsupported Durable occurrence relation: " + relation);
        };
        String column = switch (idColumn) {
            case "wait_id", "effect_id" -> idColumn;
            default -> throw new IllegalArgumentException("Unsupported Durable occurrence identity: " + idColumn);
        };
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE " + column + " IN (" + placeholders(ids.size()) + ')')) {
            bindUuids(delete, 1, ids);
            return delete.executeUpdate();
        }
    }

    private static String placeholders(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("placeholder count must be positive");
        }
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static int bindUuids(PreparedStatement statement, int firstIndex, Iterable<UUID> values) throws SQLException {
        int index = firstIndex;
        for (UUID value : values) {
            setProviderObject(statement, index++, value);
        }
        return index;
    }

    private static void setProviderObject(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof UUID uuid) {
            statement.setString(index, uuid.toString());
        } else {
            statement.setObject(index, value);
        }
    }

    private static boolean isDuplicateEntry(SQLException failure) {
        return failure.getErrorCode() == 1062;
    }

    private static UUID providerUuid(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static UUID providerUuid(ResultSet rows, int column) throws SQLException {
        String value = rows.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static String nullableUuidString(UUID value) {
        return value == null ? null : value.toString();
    }

    private static int selectTurnFaultStreak(Connection connection, UUID runId) throws SQLException {
        try (PreparedStatement select =
                connection.prepareStatement("SELECT turn_fault_streak FROM cf_durable_run WHERE run_id = ?")) {
            setProviderObject(select, 1, runId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Run disappeared after Turn fault release");
                }
                return rows.getInt(1);
            }
        }
    }

    private static long positiveMillis(Duration duration) {
        Duration value = Objects.requireNonNull(duration, "duration");
        if (value.compareTo(Duration.ofMillis(1)) < 0 || value.compareTo(Duration.ofHours(1)) > 0
                || value.toNanosPart() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    "lease extension must be a positive whole-millisecond duration at most PT1H");
        }
        return value.toMillis();
    }

    private static long delayMillis(Duration duration) {
        Duration value = Objects.requireNonNull(duration, "delay");
        if (value.isNegative() || value.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("delay must be in [PT0S, P30D]");
        }
        return value.toMillis();
    }

    private static long retentionMillis(Duration duration) {
        Duration value = Objects.requireNonNull(duration, "retention");
        if (value.isNegative() || value.compareTo(Duration.ofDays(3650)) > 0) {
            throw new IllegalArgumentException("retention must be in [PT0S, P3650D]");
        }
        return value.toMillis();
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("limit must be in [1, 10000]");
        }
    }

    private static void requireOne(int count, String message) {
        if (count != 1) {
            throw error(DurableErrorCode.CONCURRENT_MODIFICATION, message);
        }
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private static <T> Set<T> leaseSnapshot(Set<T> leases, String name) {
        Set<T> snapshot = Set.copyOf(Objects.requireNonNull(leases, name));
        if (snapshot.size() > DurableLeaseStore.MAX_RENEWAL_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    name + " must contain at most " + DurableLeaseStore.MAX_RENEWAL_BATCH_SIZE + " authorities");
        }
        return snapshot;
    }

    private static UUID uuid(ProcessRunId runId) {
        return UUID.fromString(Objects.requireNonNull(runId, "runId").value());
    }

    private static ProcessRunId runId(UUID runId) {
        return new ProcessRunId(runId.toString());
    }

    private static Envelope envelope(byte[] bytes) {
        return Envelope.fromStoredBytes(bytes);
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        Timestamp value = rows.getTimestamp(column, utcCalendar());
        if (value == null) {
            throw new SQLException(column + " is unexpectedly NULL");
        }
        return value.toInstant();
    }

    private static Instant nullableInstant(ResultSet rows, String column) throws SQLException {
        Timestamp value = rows.getTimestamp(column, utcCalendar());
        return value == null ? null : value.toInstant();
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static boolean controlIntentSatisfied(String state, ControlOperation operation) {
        return operation == ControlOperation.RESUME
                ? "ACTIVE".equals(state)
                : "PAUSED".equals(state) || "PAUSE_REQUESTED".equals(state);
    }

    private static boolean effectResolutionSatisfied(String status, EffectResolutionDecision decision) {
        return switch (decision) {
            case CONFIRM_SUCCEEDED -> "COMPLETED".equals(status);
            case CONFIRM_NOT_EXECUTED_RETRY -> "PENDING".equals(status);
            case FAIL_RUN -> "CANCELLED".equals(status);
        };
    }

    private static boolean outboxResolutionSatisfied(String status, OutboxResolutionDecision decision) {
        return decision == OutboxResolutionDecision.RETRY ? "PENDING".equals(status) : "ABANDONED".equals(status);
    }

    private static Envelope auditEnvelope(AuditPrincipal actor, String reason, String auditContextId) {
        String audit = auditContextId == null ? "" : ",\"auditContextId\":\"" + jsonEscape(auditContextId) + '"';
        String json =
                "{\"actor\":\"" + jsonEscape(actor.value()) + "\",\"reason\":\"" + jsonEscape(reason) + '"' + audit
                + '}';
        return new Envelope(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static DurableProcessException error(DurableErrorCode code, String message) {
        return DurableProcessException.of(code, message);
    }

    private record RunControlRow(ProcessRunStatus status, String controlState, long controlRevision) {}

    private record LockedRun(long occurrenceSequence, boolean cancelRequested) {}

    private record EffectRow(String status, long reviewRevision, byte[] resultEnvelope) {}

    private record OutboxAuthority(String eventType, String status, long revision) {}

    private record EffectLeaseRow(UUID runId, String operation) {}

    private record EffectCandidate(UUID effectId, UUID runId) {}

    private record ReconciliationCandidate(UUID effectId, UUID runId, int reconcileAttempts, int maxReconcileAttempts) {}

    private record OutboxCandidate(UUID eventId, UUID runId) {}

    private record WaitCandidate(UUID waitId, UUID runId) {}
}
