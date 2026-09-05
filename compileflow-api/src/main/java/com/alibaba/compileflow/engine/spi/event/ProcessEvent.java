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
package com.alibaba.compileflow.engine.spi.event;

import com.alibaba.compileflow.engine.ProcessError;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessTrigger;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, typed lifecycle event emitted by a process engine.
 *
 * <p>Each event subtype exposes only fields that are meaningful for that lifecycle transition.
 * Execution completion and failure events carry the same controlled {@link ProcessExecution}
 * returned to the caller and separate operational {@link ExecutionAttribution}. Events never carry
 * routing keys, process variables, source content, arbitrary metadata, or raw exception objects.
 *
 * @author yusu
 */
public sealed interface ProcessEvent
        permits ProcessEvent.ExecutionStarted, ProcessEvent.ExecutionCompleted, ProcessEvent.ExecutionFailed,
        ProcessEvent.TriggerStarted, ProcessEvent.TriggerCompleted, ProcessEvent.TriggerFailed {
    private static long requireDuration(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        return value;
    }

    private static ExecutionAttribution requireAttribution(ProcessExecution execution, ExecutionAttribution attribution) {
        ProcessExecution exactExecution = Objects.requireNonNull(execution, "execution");
        ExecutionAttribution exactAttribution = Objects.requireNonNull(attribution, "attribution");
        ProcessRef.Alias alias = exactAttribution.admittedAlias();
        if (alias != null
                && (!alias.namespace().equals(exactExecution.getNamespace())
                || !alias.code().equals(exactExecution.getProcessCode()))) {
            throw new IllegalArgumentException("admittedAlias must identify the executed process");
        }
        if (exactAttribution.aliasRevision() != null && exactExecution.getProcessVersion() == null) {
            throw new IllegalArgumentException("Alias selection requires an exact executed process Version");
        }
        return exactAttribution;
    }

    /**
     * Returns the stable event classification.
     *
     * @return event type
     */
    Type getType();

    /**
     * Returns the correlated trace identifier when available.
     *
     * @return trace identifier, or {@code null}
     */
    String traceId();

    /**
     * Returns when the event occurred.
     *
     * @return event time
     */
    Instant occurredAt();

    /**
     * Stable event classification.
     */
    enum Type {
        /**
         * A process execution has started.
         */
        EXECUTION_STARTED,
        /**
         * A process execution completed successfully.
         */
        EXECUTION_COMPLETED,
        /**
         * A process execution failed.
         */
        EXECUTION_FAILED,
        /**
         * A trigger-entry invocation has started a new execution.
         */
        TRIGGER_STARTED,
        /**
         * A trigger-entry invocation completed successfully.
         */
        TRIGGER_COMPLETED,
        /**
         * A trigger-entry invocation failed.
         */
        TRIGGER_FAILED
    }

    /**
     * Operational attribution for a terminal execution event.
     *
     * <p>This value belongs to observability. It is intentionally separate from the semantic
     * {@link ProcessExecution} returned to callers.
     *
     * @param parentInvocationId direct synchronous caller, or {@code null} for a root
     * @param callDepth          zero-based synchronous call depth
     * @param modelType          executed process format
     * @param sourceDigest       exact source digest, or {@code null} when resolution failed
     * @param admittedAlias      Alias used at admission, or {@code null}
     * @param aliasRevision      selected Alias revision, or {@code null} before selection
     * @param aliasTarget        selected stable or candidate target, or {@code null}
     */
    record ExecutionAttribution(String parentInvocationId, int callDepth, ProcessModelType modelType,
            String sourceDigest, ProcessRef.Alias admittedAlias, Long aliasRevision, ProcessAliasTarget aliasTarget) {
        public ExecutionAttribution {
            parentInvocationId = ProcessIdentifiers.optionalInvocationId(parentInvocationId);
            if (callDepth < 0) {
                throw new IllegalArgumentException("callDepth must not be negative");
            }
            if ((callDepth == 0) != (parentInvocationId == null)) {
                throw new IllegalArgumentException("parentInvocationId must be absent exactly at the root");
            }
            modelType = Objects.requireNonNull(modelType, "modelType");
            sourceDigest = ProcessIdentifiers.optionalSha256(sourceDigest, "sourceDigest");
            if ((aliasRevision == null) != (aliasTarget == null)) {
                throw new IllegalArgumentException("aliasRevision and aliasTarget must be present together");
            }
            if (aliasRevision != null && aliasRevision <= 0L) {
                throw new IllegalArgumentException("aliasRevision must be positive");
            }
            if (admittedAlias == null && aliasRevision != null) {
                throw new IllegalArgumentException("Alias selection requires an admitted Alias");
            }
        }
    }

    /**
     * Reports the start of a ProcessEngine process execution.
     *
     * @param namespace    process namespace
     * @param processCode  process code
     * @param invocationId caller-provided or engine-generated invocation identifier
     * @param traceId      correlated trace identifier, or {@code null}
     * @param occurredAt   event time
     */
    record ExecutionStarted(String namespace, String processCode, String invocationId, String traceId,
            Instant occurredAt) implements ProcessEvent {
        /**
         * Validates one execution-start event.
         *
         * @param namespace    process namespace
         * @param processCode  process code
         * @param invocationId invocation identifier
         * @param traceId      correlated trace identifier, or {@code null}
         * @param occurredAt   event time
         */
        public ExecutionStarted {
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            processCode = ProcessIdentifiers.requireCode(processCode);
            invocationId = ProcessIdentifiers.requireInvocationId(invocationId);
            traceId = ProcessIdentifiers.optionalTraceId(traceId);
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }

        @Override
        public Type getType() {
            return Type.EXECUTION_STARTED;
        }
    }

    /**
     * Reports successful completion of a ProcessEngine process execution.
     *
     * @param execution  semantic execution facts
     * @param attribution operational event attribution
     * @param durationMs monotonic elapsed duration in milliseconds
     * @param occurredAt event time
     */
    record ExecutionCompleted(ProcessExecution execution, ExecutionAttribution attribution, long durationMs,
            Instant occurredAt) implements ProcessEvent {
        /**
         * Validates one execution-completion event.
         *
         * @param execution   semantic execution facts
         * @param attribution operational event attribution
         * @param durationMs  monotonic elapsed duration in milliseconds
         * @param occurredAt  event time
         */
        public ExecutionCompleted {
            execution = Objects.requireNonNull(execution, "execution");
            attribution = requireAttribution(execution, attribution);
            durationMs = requireDuration(durationMs);
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }

        @Override
        public String traceId() {
            return execution.getTraceId();
        }

        @Override
        public Type getType() {
            return Type.EXECUTION_COMPLETED;
        }
    }

    /**
     * Reports failure of a ProcessEngine process execution.
     *
     * @param execution   semantic execution facts
     * @param attribution operational event attribution
     * @param durationMs  monotonic elapsed duration in milliseconds
     * @param error       stable, caller-safe process error
     * @param occurredAt  event time
     */
    record ExecutionFailed(ProcessExecution execution, ExecutionAttribution attribution, long durationMs,
            ProcessError error, Instant occurredAt) implements ProcessEvent {
        /**
         * Validates one execution-failure event.
         *
         * @param execution   semantic execution facts
         * @param attribution operational event attribution
         * @param durationMs  monotonic elapsed duration in milliseconds
         * @param error       stable process error
         * @param occurredAt  event time
         */
        public ExecutionFailed {
            execution = Objects.requireNonNull(execution, "execution");
            attribution = requireAttribution(execution, attribution);
            durationMs = requireDuration(durationMs);
            error = Objects.requireNonNull(error, "error");
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }

        @Override
        public String traceId() {
            return execution.getTraceId();
        }

        @Override
        public Type getType() {
            return Type.EXECUTION_FAILED;
        }
    }

    /**
     * Reports the start of a trigger operation, which starts a new execution.
     *
     * @param namespace    process namespace
     * @param processCode  process code
     * @param invocationId caller-provided or engine-generated invocation identifier
     * @param trigger      requested trigger entry
     * @param traceId      correlated trace identifier, or {@code null}
     * @param occurredAt   event time
     */
    record TriggerStarted(String namespace, String processCode, String invocationId, ProcessTrigger trigger,
            String traceId, Instant occurredAt) implements ProcessEvent {
        /**
         * Validates one trigger-start event.
         *
         * @param namespace    process namespace
         * @param processCode  process code
         * @param invocationId invocation identifier
         * @param trigger      requested trigger entry
         * @param traceId      correlated trace identifier, or {@code null}
         * @param occurredAt   event time
         */
        public TriggerStarted {
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            processCode = ProcessIdentifiers.requireCode(processCode);
            invocationId = ProcessIdentifiers.requireInvocationId(invocationId);
            trigger = Objects.requireNonNull(trigger, "trigger");
            traceId = ProcessIdentifiers.optionalTraceId(traceId);
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }

        @Override
        public Type getType() {
            return Type.TRIGGER_STARTED;
        }
    }

    /**
     * Reports successful completion of a trigger execution.
     *
     * @param execution   semantic execution facts
     * @param attribution operational event attribution
     * @param trigger     requested trigger entry
     * @param durationMs  monotonic elapsed duration in milliseconds
     * @param occurredAt  event time
     */
    record TriggerCompleted(ProcessExecution execution, ExecutionAttribution attribution, ProcessTrigger trigger,
            long durationMs, Instant occurredAt) implements ProcessEvent {
        /**
         * Validates one trigger-completion event.
         *
         * @param execution   semantic execution facts
         * @param attribution operational event attribution
         * @param trigger     requested trigger entry
         * @param durationMs  monotonic elapsed duration in milliseconds
         * @param occurredAt  event time
         */
        public TriggerCompleted {
            execution = Objects.requireNonNull(execution, "execution");
            attribution = requireAttribution(execution, attribution);
            trigger = Objects.requireNonNull(trigger, "trigger");
            durationMs = requireDuration(durationMs);
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }

        @Override
        public String traceId() {
            return execution.getTraceId();
        }

        @Override
        public Type getType() {
            return Type.TRIGGER_COMPLETED;
        }
    }

    /**
     * Reports failure of a trigger execution.
     *
     * @param execution   semantic execution facts
     * @param attribution operational event attribution
     * @param trigger     requested trigger entry
     * @param durationMs  monotonic elapsed duration in milliseconds
     * @param error       stable, caller-safe process error
     * @param occurredAt  event time
     */
    record TriggerFailed(ProcessExecution execution, ExecutionAttribution attribution, ProcessTrigger trigger,
            long durationMs, ProcessError error, Instant occurredAt) implements ProcessEvent {
        /**
         * Validates one trigger-failure event.
         *
         * @param execution   semantic execution facts
         * @param attribution operational event attribution
         * @param trigger     requested trigger entry
         * @param durationMs  monotonic elapsed duration in milliseconds
         * @param error       stable process error
         * @param occurredAt  event time
         */
        public TriggerFailed {
            execution = Objects.requireNonNull(execution, "execution");
            attribution = requireAttribution(execution, attribution);
            trigger = Objects.requireNonNull(trigger, "trigger");
            durationMs = requireDuration(durationMs);
            error = Objects.requireNonNull(error, "error");
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }

        @Override
        public String traceId() {
            return execution.getTraceId();
        }

        @Override
        public Type getType() {
            return Type.TRIGGER_FAILED;
        }
    }
}
