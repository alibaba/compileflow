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
package com.alibaba.compileflow.durable.api.model;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Instant;
import java.util.Objects;

/**
 * Public domain state of one Durable Process Run.
 *
 * <p>State, cursor, Wait token, Wait completion payload, Effect input/result,
 * lease, and fencing data are deliberately absent from this application-facing model.</p>
 *
 * @param runId            stable Run identity
 * @param namespace        root Process namespace
 * @param processCode      root Process code
 * @param processVersion   exact Version selected at admission, or {@code null} when started from a definition
 * @param status           public lifecycle
 * @param control          independent Run-execution admission state
 * @param activeWork       bounded unresolved-occurrence summary
 * @param availableAt      authoritative Run-execution scheduling eligibility
 * @param retry            current execution-retry evidence, or {@code null}
 * @param errorCode        stable redaction-safe error code, or {@code null}
 * @param errorMessage     redaction-safe diagnostic, or {@code null}
 * @param cancelRequestedAt cooperative cancellation intent time, or {@code null}
 * @param createdAt        creation time
 * @param updatedAt        latest state-change time
 * @param completedAt      terminal time, or {@code null}
 * @author yusu
 */
public record ProcessRun(ProcessRunId runId, String namespace, String processCode, ProcessRef.Version processVersion,
        ProcessRunStatus status, ProcessRunControl control, ActiveWorkSummary activeWork, Instant availableAt,
        ProcessRunRetryState retry, String errorCode, String errorMessage, Instant cancelRequestedAt, Instant createdAt,
        Instant updatedAt, Instant completedAt) {
    public ProcessRun {
        runId = Objects.requireNonNull(runId, "runId");
        namespace = ProcessIdentifiers.requireNamespace(namespace);
        processCode = ProcessIdentifiers.requireCode(processCode);
        if (processVersion != null
                && (!namespace.equals(processVersion.namespace()) || !processCode.equals(processVersion.code()))) {
            throw new IllegalArgumentException("processVersion must identify the same process");
        }
        status = Objects.requireNonNull(status, "status");
        control = Objects.requireNonNull(control, "control");
        activeWork = Objects.requireNonNull(activeWork, "activeWork");
        availableAt = Objects.requireNonNull(availableAt, "availableAt");
        errorCode = DurableIdentifiers.optionalIdentity(errorCode, "errorCode", 128);
        errorMessage = DurableIdentifiers.optionalHumanText(errorMessage, "errorMessage", 2_048);
        if (status == ProcessRunStatus.FAILED) {
            if (errorCode == null || errorMessage == null) {
                throw new IllegalArgumentException("FAILED Run requires both errorCode and errorMessage");
            }
        } else if (errorCode != null || errorMessage != null) {
            throw new IllegalArgumentException("Failure details are valid only for FAILED Run");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (cancelRequestedAt != null && cancelRequestedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("cancelRequestedAt must not be before createdAt");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (retry != null && retry.observedAt().isBefore(createdAt)) {
            throw new IllegalArgumentException("retry observedAt must not be before createdAt");
        }
        if (status.isTerminal() != (completedAt != null)) {
            throw new IllegalArgumentException("completedAt must be present exactly for terminal status");
        }
        if (completedAt != null && completedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("completedAt must not be before updatedAt");
        }
        if (status.isTerminal() && !activeWork.isEmpty()) {
            throw new IllegalArgumentException("terminal Run must not expose active work");
        }
        if (status.isTerminal() && retry != null) {
            throw new IllegalArgumentException("terminal Run must not expose retry evidence");
        }
        validateActiveWork(status, activeWork);
        validateControlState(status, control.state(), activeWork);
    }

    private static void validateActiveWork(ProcessRunStatus status, ActiveWorkSummary work) {
        if (status == ProcessRunStatus.WAITING && work.isEmpty()) {
            throw new IllegalArgumentException("WAITING Run requires unresolved work");
        }
    }

    private static void validateControlState(ProcessRunStatus status, ProcessRunControlState controlState,
            ActiveWorkSummary work) {
        if (status.isTerminal() && controlState != ProcessRunControlState.ACTIVE) {
            throw new IllegalArgumentException("Terminal Runs must be ACTIVE");
        }
        if (controlState == ProcessRunControlState.PAUSED && status == ProcessRunStatus.RUNNING) {
            throw new IllegalArgumentException("RUNNING Run cannot be PAUSED");
        }
        if (controlState == ProcessRunControlState.PAUSED && work.runningEffects() > 0) {
            throw new IllegalArgumentException("PAUSED Run cannot retain Effect execution authority");
        }
        if (controlState == ProcessRunControlState.PAUSE_REQUESTED && status != ProcessRunStatus.RUNNING
                && work.runningEffects() == 0) {
            throw new IllegalArgumentException("PAUSE_REQUESTED requires issued execution authority");
        }
    }
}
