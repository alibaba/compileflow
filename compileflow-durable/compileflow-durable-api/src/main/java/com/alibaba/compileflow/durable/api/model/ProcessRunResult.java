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
import com.alibaba.compileflow.durable.api.validation.DurablePayload;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Closed, payload-bearing result of querying one Durable Process Run.
 *
 * @author yusu
 */
public sealed interface ProcessRunResult {
    ProcessRunId runId();

    /**
     * Completed Run with its immutable business output.
     *
     * @param runId exact Run identity
     * @param namespace root Process namespace
     * @param processCode root Process code
     * @param processVersion exact Version selected at admission, or {@code null} when started from a definition
     * @param output immutable business output
     * @param completedAt authority completion time
     */
    record Succeeded(ProcessRunId runId, String namespace, String processCode, ProcessRef.Version processVersion,
            Map<String, Object> output, Instant completedAt) implements ProcessRunResult {
        public Succeeded {
            runId = Objects.requireNonNull(runId, "runId");
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            processCode = ProcessIdentifiers.requireCode(processCode);
            requireSameProcess(namespace, processCode, processVersion);
            output = DurablePayload.immutablePayload(Objects.requireNonNull(output, "output"), "output");
            completedAt = Objects.requireNonNull(completedAt, "completedAt");
        }

        @Override
        public String toString() {
            return "Succeeded{runId=" + runId + ", processCode=" + processCode + ", outputKeys=" + output.keySet()
                    + ", completedAt=" + completedAt + '}';
        }
    }

    /**
     * Failed Run with bounded caller-safe failure information.
     *
     * @param runId exact Run identity
     * @param namespace root Process namespace
     * @param processCode root Process code
     * @param processVersion exact Version selected at admission, or {@code null} when started from a definition
     * @param errorCode stable failure code
     * @param errorMessage caller-safe failure message
     * @param completedAt authority completion time
     */
    record Failed(ProcessRunId runId, String namespace, String processCode, ProcessRef.Version processVersion,
            String errorCode, String errorMessage, Instant completedAt) implements ProcessRunResult {
        public Failed {
            runId = Objects.requireNonNull(runId, "runId");
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            processCode = ProcessIdentifiers.requireCode(processCode);
            requireSameProcess(namespace, processCode, processVersion);
            errorCode = DurableIdentifiers.requireIdentity(errorCode, "errorCode", 128);
            errorMessage = DurableIdentifiers.requireHumanText(errorMessage, "errorMessage", 2_048);
            completedAt = Objects.requireNonNull(completedAt, "completedAt");
        }
    }

    /**
     * Cooperatively cancelled terminal Run.
     *
     * @param runId exact Run identity
     * @param namespace root Process namespace
     * @param processCode root Process code
     * @param processVersion exact Version selected at admission, or {@code null} when started from a definition
     * @param completedAt authority completion time
     */
    record Cancelled(ProcessRunId runId, String namespace, String processCode, ProcessRef.Version processVersion,
            Instant completedAt) implements ProcessRunResult {
        public Cancelled {
            runId = Objects.requireNonNull(runId, "runId");
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            processCode = ProcessIdentifiers.requireCode(processCode);
            requireSameProcess(namespace, processCode, processVersion);
            completedAt = Objects.requireNonNull(completedAt, "completedAt");
        }
    }

    /**
     * Existing Run that has not reached a terminal outcome.
     *
     * @param runId exact Run identity
     * @param namespace root Process namespace
     * @param processCode root Process code
     * @param processVersion exact Version selected at admission, or {@code null} when started from a definition
     * @param status current non-terminal status
     */
    record NotCompleted(ProcessRunId runId, String namespace, String processCode, ProcessRef.Version processVersion,
            ProcessRunStatus status) implements ProcessRunResult {
        public NotCompleted {
            runId = Objects.requireNonNull(runId, "runId");
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            processCode = ProcessIdentifiers.requireCode(processCode);
            requireSameProcess(namespace, processCode, processVersion);
            status = Objects.requireNonNull(status, "status");
            if (status.isTerminal()) {
                throw new IllegalArgumentException("NotCompleted requires a non-terminal status");
            }
        }
    }

    /**
     * Missing Run result for the requested identity.
     *
     * @param runId requested Run identity
     */
    record NotFound(ProcessRunId runId) implements ProcessRunResult {
        public NotFound {
            runId = Objects.requireNonNull(runId, "runId");
        }
    }

    private static void requireSameProcess(String namespace, String processCode, ProcessRef.Version processVersion) {
        if (processVersion != null
                && (!namespace.equals(processVersion.namespace()) || !processCode.equals(processVersion.code()))) {
            throw new IllegalArgumentException("processVersion must identify the same process");
        }
    }
}
