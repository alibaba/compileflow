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
package com.alibaba.compileflow.deploy.runtime.version;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector.DeclaredProcessCall;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.runtime.ProcessExecutionGraphPreparer;
import com.alibaba.compileflow.engine.core.runtime.ownership.ProcessRuntimeOwnership;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies resolved artifacts and installs them into one process engine.
 *
 * @author yusu
 */
public final class ProcessArtifactRuntimeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessArtifactRuntimeLoader.class);
    private static final AtomicLong OWNER_SEQUENCE = new AtomicLong();
    private final ProcessRuntimeOwnership ownership;
    private final ProcessCallInspector inspector;
    private final ProcessExecutionGraphPreparer graphPreparer;
    private final ConcurrentMap<ProcessRef.Version, RuntimeSlot> slotsByVersion = new ConcurrentHashMap<>();
    private final String ownerId;
    private final DeploymentRuntimeMetrics metrics;

    public ProcessArtifactRuntimeLoader(ProcessEngine engine) {
        this(engine, new DeploymentRuntimeMetrics());
    }

    public ProcessArtifactRuntimeLoader(ProcessEngine engine, DeploymentRuntimeMetrics metrics) {
        this(ProcessRuntimeOwnership.require(engine), ProcessCallInspector.require(engine),
                ProcessExecutionGraphPreparer.require(engine), metrics);
    }

    public ProcessArtifactRuntimeLoader(ProcessRuntimeOwnership ownership, ProcessCallInspector inspector,
            ProcessExecutionGraphPreparer graphPreparer, DeploymentRuntimeMetrics metrics) {
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.inspector = Objects.requireNonNull(inspector, "inspector");
        this.graphPreparer = Objects.requireNonNull(graphPreparer, "graphPreparer");
        ownerId = ProcessArtifactRuntimeLoader.class.getName() + "#" + OWNER_SEQUENCE.incrementAndGet();
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public void load(ProcessArtifact artifact) {
        ProcessArtifact resolved = Objects.requireNonNull(artifact, "artifact");
        ProcessRef.Version ref = resolved.getRef();
        try {
            validateDigest(resolved);
            validateCallBindings(resolved, inspector.inspectProcessCalls(resolved.getDefinition()));
            RuntimeSlot slot = acquireSlot(ref);
            slot.lock();
            try {
                ownership.loadOwned(ownerId, ref, resolved.getDefinition());
                slot.installed = true;
            } finally {
                slot.unlock();
                releaseSlot(ref, slot);
            }
            LOGGER.info("Process runtime loaded successfully: ns={} code={} version={}", ref.namespace(), ref.code(),
                    ref.version());
        } catch (RuntimeException failure) {
            DeploymentException deploymentFailure = failure instanceof DeploymentException exception
                    ? exception
                    : DeploymentException.fromRef(DeploymentErrorCode.INTERNAL_ERROR, "runtime load failed", ref,
                            failure);
            metrics.recordError(deploymentFailure.getErrorCode());
            throw deploymentFailure;
        }
    }

    /**
     * Validates the complete exact Process call graph after all artifact runtimes are installed.
     *
     * @param ref exact installed root Version
     */
    public void prepare(ProcessRef.Version ref) {
        ProcessRef.Version exact = Objects.requireNonNull(ref, "ref");
        RuntimeSlot slot = slotsByVersion.get(exact);
        if (slot == null || !slot.installed) {
            throw DeploymentException.fromRef(DeploymentErrorCode.CONVERGENCE_FAILED, "Exact runtime is not installed",
                    exact);
        }
        try {
            graphPreparer.prepareExact(exact);
        } catch (DeploymentException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw DeploymentException.fromRef(DeploymentErrorCode.CONVERGENCE_FAILED,
                    "Exact Process call graph preparation failed", exact, failure);
        }
    }

    private static void validateDigest(ProcessArtifact artifact) {
        ProcessRef.Version ref = artifact.getRef();
        Map<String, ProcessRef.Version> targets = artifact
            .getCallBindings()
            .values()
            .stream()
            .collect(Collectors.toMap(binding -> binding.callSiteId(), binding -> binding.target()));
        String computedDigest = ProcessArtifactDigest.compute(artifact.getDefinition(), targets);
        if (!artifact.getArtifactDigest().equals(computedDigest)) {
            throw DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH,
                    "Executable artifact digest mismatch", ref);
        }
    }

    private static void validateCallBindings(ProcessArtifact artifact, List<DeclaredProcessCall> sourceCalls) {
        Map<String, ProcessRef.Version> expected = artifact
            .getCallBindings()
            .values()
            .stream()
            .collect(Collectors.toMap(binding -> binding.callSiteId(), binding -> binding.target()));
        Map<String, ProcessRef.Version> actual;
        try {
            actual = Objects
                .requireNonNull(sourceCalls, "sourceCalls")
                .stream()
                .collect(Collectors.toMap(DeclaredProcessCall::callSiteId, call -> exactTarget(artifact.getRef(), call)));
        } catch (IllegalArgumentException failure) {
            throw DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH, failure.getMessage(),
                    artifact.getRef(), failure);
        }
        if (!expected.equals(actual)) {
            throw DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH,
                    "Published call bindings do not match the Process definition", artifact.getRef());
        }
    }

    private static ProcessRef.Version exactTarget(ProcessRef.Version caller, DeclaredProcessCall call) {
        if (!(call.target() instanceof ProcessCallTarget.Version version)) {
            throw new IllegalArgumentException(
                    "Published Process call '" + call.callSiteId() + "' must declare an exact called-Process version");
        }
        return ProcessRef.version(caller.namespace(), call.code(), version.version());
    }

    public ReleaseResult release(String namespace, String code, String version) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, version);
        try {
            RuntimeSlot slot = acquireSlot(ref);
            slot.lock();
            try {
                ReleaseResult result = switch (ownership.releaseOwned(ownerId, ref)) {
                    case NOT_OWNED -> ReleaseResult.NOT_OWNED;
                    case RETAINED -> ReleaseResult.RETAINED;
                    case REMOVED -> ReleaseResult.REMOVED;
                };
                slot.installed = false;
                LOGGER.info("Process runtime ownership released: ns={} code={} version={} result={}", ref.namespace(),
                        ref.code(), ref.version(), result);
                return result;
            } finally {
                slot.unlock();
                releaseSlot(ref, slot);
            }
        } catch (RuntimeException failure) {
            DeploymentException releaseFailure = failure instanceof DeploymentException exception
                    ? exception
                    : DeploymentException
                .builder(DeploymentErrorCode.INTERNAL_ERROR, "runtime ownership release failed", failure)
                .namespace(ref.namespace())
                .code(ref.code())
                .version(ref.version())
                .build();
            metrics.recordError(releaseFailure.getErrorCode());
            LOGGER.error("Process runtime ownership release failed: ns={} code={} version={}", ref.namespace(),
                    ref.code(), ref.version(), failure);
            return ReleaseResult.FAILED;
        }
    }

    private RuntimeSlot acquireSlot(ProcessRef.Version ref) {
        return slotsByVersion.compute(ref, (ignored, current) -> {
            RuntimeSlot selected = current == null ? new RuntimeSlot() : current;
            selected.reserve();
            return selected;
        });
    }

    private void releaseSlot(ProcessRef.Version ref, RuntimeSlot slot) {
        slotsByVersion.compute(ref, (ignored, current) -> {
            if (current != slot) {
                throw new IllegalStateException("Runtime slot changed while it was reserved: " + ref);
            }
            return slot.releaseReservationAndIsUnused() ? null : slot;
        });
    }

    public enum ReleaseResult {
        NOT_OWNED,
        RETAINED,
        REMOVED,
        FAILED
    }

    /**
     * Per-version operation sequencer. Only operations for the same exact version are serialized;
     * external engine calls never run under a {@link ConcurrentHashMap} bin lock. The reservation
     * count prevents a slot from being removed while another caller is active or waiting.
     */
    private static final class RuntimeSlot {
        private final ReentrantLock operationLock = new ReentrantLock();
        private volatile boolean installed;
        private int reservations;

        private void reserve() {
            reservations++;
        }

        private boolean releaseReservationAndIsUnused() {
            if (--reservations < 0) {
                throw new IllegalStateException("Runtime slot reservation count underflow");
            }
            return reservations == 0 && !installed;
        }

        private void lock() {
            operationLock.lock();
        }

        private void unlock() {
            operationLock.unlock();
        }
    }
}
