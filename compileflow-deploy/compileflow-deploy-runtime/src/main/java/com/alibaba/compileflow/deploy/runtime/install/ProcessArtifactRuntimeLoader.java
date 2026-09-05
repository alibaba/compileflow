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
package com.alibaba.compileflow.deploy.runtime.install;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector.DeclaredProcessCall;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.runtime.ProcessExecutionGraphPreparer;
import com.alibaba.compileflow.engine.core.runtime.ownership.ProcessRuntimeOwnership;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
import java.util.Collections;
import java.util.EnumMap;
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
 * Verifies a resolved artifact and installs it into one model-specific process engine.
 *
 * @author yusu
 */
public final class ProcessArtifactRuntimeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessArtifactRuntimeLoader.class);
    private static final AtomicLong OWNER_SEQUENCE = new AtomicLong();
    private final Map<ProcessModelType, ProcessRuntimeOwnership> ownershipByModelType;
    private final Map<ProcessModelType, ProcessCallInspector> inspectorsByModelType;
    private final Map<ProcessModelType, ProcessExecutionGraphPreparer> graphPreparersByModelType;
    private final ConcurrentMap<ProcessRef.Version, RuntimeSlot> slotsByVersion = new ConcurrentHashMap<>();
    private final String ownerId;
    private final ProcessDeploymentMetrics metrics;

    public ProcessArtifactRuntimeLoader(ProcessEngine engine, ProcessModelType modelType) {
        this(engine, modelType, new ProcessDeploymentMetrics());
    }

    public ProcessArtifactRuntimeLoader(ProcessEngine engine, ProcessModelType modelType,
            ProcessDeploymentMetrics metrics) {
        this(singleRuntimeInterfaces(modelType, ProcessRuntimeOwnership.require(engine),
                        ProcessCallInspector.require(engine),
                        engine instanceof ProcessExecutionGraphPreparer preparer ? preparer : null), metrics);
    }

    public ProcessArtifactRuntimeLoader(ProcessRuntimeOwnership ownership, ProcessCallInspector inspector,
            ProcessModelType modelType, ProcessExecutionGraphPreparer graphPreparer, ProcessDeploymentMetrics metrics) {
        this(singleRuntimeInterfaces(modelType, ownership, inspector, graphPreparer), metrics);
    }

    /**
     * Creates a loader that dispatches immutable artifacts to format-bound engines.
     *
     * @param engines non-empty model-type keyed engine registry
     * @param metrics deployment metrics sink
     */
    public ProcessArtifactRuntimeLoader(Map<ProcessModelType, ProcessEngine> engines, ProcessDeploymentMetrics metrics) {
        this(runtimeInterfaces(engines), metrics);
    }

    private ProcessArtifactRuntimeLoader(RuntimeInterfaces runtimeInterfaces, ProcessDeploymentMetrics metrics) {
        EnumMap<ProcessModelType, ProcessRuntimeOwnership> ownershipByModelType = runtimeInterfaces.ownership();
        EnumMap<ProcessModelType, ProcessCallInspector> inspectorsByModelType = runtimeInterfaces.inspectors();
        EnumMap<ProcessModelType, ProcessExecutionGraphPreparer> graphPreparersByModelType =
                runtimeInterfaces.graphPreparers();
        this.ownershipByModelType = Collections.unmodifiableMap(new EnumMap<>(ownershipByModelType));
        this.inspectorsByModelType = Collections.unmodifiableMap(new EnumMap<>(inspectorsByModelType));
        this.graphPreparersByModelType = Collections.unmodifiableMap(new EnumMap<>(graphPreparersByModelType));
        ownerId = ProcessArtifactRuntimeLoader.class.getName() + "#" + OWNER_SEQUENCE.incrementAndGet();
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    private static RuntimeInterfaces singleRuntimeInterfaces(ProcessModelType modelType,
            ProcessRuntimeOwnership ownership, ProcessCallInspector inspector,
            ProcessExecutionGraphPreparer graphPreparer) {
        ProcessModelType type = Objects.requireNonNull(modelType, "modelType");
        EnumMap<ProcessModelType, ProcessRuntimeOwnership> ownershipByModelType = new EnumMap<>(ProcessModelType.class);
        EnumMap<ProcessModelType, ProcessCallInspector> inspectorsByModelType = new EnumMap<>(ProcessModelType.class);
        EnumMap<ProcessModelType, ProcessExecutionGraphPreparer> graphPreparersByModelType =
                new EnumMap<>(ProcessModelType.class);
        ownershipByModelType.put(type, Objects.requireNonNull(ownership, "ownership"));
        inspectorsByModelType.put(type, Objects.requireNonNull(inspector, "inspector"));
        if (graphPreparer != null) {
            graphPreparersByModelType.put(type, graphPreparer);
        }
        return new RuntimeInterfaces(ownershipByModelType, inspectorsByModelType, graphPreparersByModelType);
    }

    private static RuntimeInterfaces runtimeInterfaces(Map<ProcessModelType, ProcessEngine> engines) {
        Map<ProcessModelType, ProcessEngine> source = Objects.requireNonNull(engines, "engines");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("engines must not be empty");
        }
        EnumMap<ProcessModelType, ProcessRuntimeOwnership> ownershipByModelType = new EnumMap<>(ProcessModelType.class);
        EnumMap<ProcessModelType, ProcessCallInspector> inspectorsByModelType = new EnumMap<>(ProcessModelType.class);
        EnumMap<ProcessModelType, ProcessExecutionGraphPreparer> graphPreparersByModelType =
                new EnumMap<>(ProcessModelType.class);
        source.forEach((modelType, engine) -> {
            ProcessModelType type = Objects.requireNonNull(modelType, "engine model type");
            ProcessEngine processEngine = Objects.requireNonNull(engine, "engine");
            ownershipByModelType.put(type, ProcessRuntimeOwnership.require(processEngine));
            inspectorsByModelType.put(type, ProcessCallInspector.require(processEngine));
            graphPreparersByModelType.put(type, ProcessExecutionGraphPreparer.require(processEngine));
        });
        return new RuntimeInterfaces(ownershipByModelType, inspectorsByModelType, graphPreparersByModelType);
    }

    public void load(ProcessArtifact artifact) {
        ProcessArtifact resolved = Objects.requireNonNull(artifact, "artifact");
        ProcessRef.Version ref = resolved.getRef();
        try {
            validateDigest(resolved);
            ProcessRuntimeOwnership ownership = requireOwnership(resolved.getModelType(), ref);
            validateCallBindings(resolved,
                    requireInspector(resolved.getModelType(), ref).inspectProcessCalls(resolved.getDefinition()));
            RuntimeBinding proposed = new RuntimeBinding(resolved.getModelType(), ownership);
            RuntimeSlot slot = acquireSlot(ref);
            slot.lock();
            try {
                RuntimeBinding selected = slot.select(proposed, ref);
                selected.ownership().loadOwned(ownerId, ref, resolved.getDefinition());
                slot.publish(selected);
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
        RuntimeBinding binding = slot == null ? null : slot.bindingOr(null);
        if (binding == null) {
            throw DeploymentException.fromRef(DeploymentErrorCode.CONVERGENCE_FAILED, "Exact runtime is not installed",
                    exact);
        }
        ProcessExecutionGraphPreparer preparer = graphPreparersByModelType.get(binding.modelType());
        if (preparer == null) {
            throw DeploymentException.fromRef(DeploymentErrorCode.CONVERGENCE_FAILED,
                    "No Process call graph preparer is configured for the installed model type", exact);
        }
        try {
            preparer.prepareExact(exact);
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
        String computedDigest =
                ProcessArtifactDigest.compute(artifact.getModelType(), artifact.getDefinition(), targets);
        if (!artifact.getArtifactDigest().equals(computedDigest)) {
            throw DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH,
                    "Executable artifact digest mismatch", ref);
        }
    }

    public boolean retain(String namespace, String code, String version) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, version);
        RuntimeSlot slot = acquireSlot(ref);
        slot.lock();
        try {
            RuntimeBinding selected = slot.bindingOr(singleOwnershipBinding());
            if (selected == null) {
                return false;
            }
            boolean retained = selected.ownership().retainOwned(ownerId, ref);
            if (retained) {
                slot.publish(selected);
            }
            return retained;
        } finally {
            slot.unlock();
            releaseSlot(ref, slot);
        }
    }

    /**
     * Returns the model format bound to an installed exact Version.
     *
     * @param ref exact installed Version
     * @return model format selected when the runtime was loaded
     */
    public ProcessModelType getModelType(ProcessRef.Version ref) {
        ProcessRef.Version exact = Objects.requireNonNull(ref, "ref");
        RuntimeSlot slot = slotsByVersion.get(exact);
        RuntimeBinding binding = slot == null ? null : slot.bindingOr(null);
        if (binding == null) {
            throw DeploymentException.fromRef(DeploymentErrorCode.CONVERGENCE_FAILED, "Exact runtime is not installed",
                    exact);
        }
        return binding.modelType();
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
                RuntimeBinding selected = slot.bindingOr(singleOwnershipBinding());
                if (selected == null) {
                    return ReleaseResult.NOT_OWNED;
                }
                ReleaseResult result = switch (selected.ownership().releaseOwned(ownerId, ref)) {
                    case NOT_OWNED -> ReleaseResult.NOT_OWNED;
                    case RETAINED -> ReleaseResult.RETAINED;
                    case REMOVED -> ReleaseResult.REMOVED;
                };
                if (result != ReleaseResult.RETAINED) {
                    slot.clear();
                }
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

    private RuntimeBinding singleOwnershipBinding() {
        if (ownershipByModelType.size() != 1) {
            return null;
        }
        Map.Entry<ProcessModelType, ProcessRuntimeOwnership> entry = ownershipByModelType
            .entrySet()
            .iterator()
            .next();
        return new RuntimeBinding(entry.getKey(), entry.getValue());
    }

    private ProcessRuntimeOwnership requireOwnership(ProcessModelType modelType, ProcessRef.Version ref) {
        ProcessRuntimeOwnership ownership = ownershipByModelType.get(modelType);
        if (ownership == null) {
            throw DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH,
                    "No runtime engine is configured for artifact model type " + modelType, ref);
        }
        return ownership;
    }

    private ProcessCallInspector requireInspector(ProcessModelType modelType, ProcessRef.Version ref) {
        ProcessCallInspector inspector = inspectorsByModelType.get(modelType);
        if (inspector == null) {
            throw DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH,
                    "No Process call inspector is configured for artifact model type " + modelType, ref);
        }
        return inspector;
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

    private record RuntimeBinding(ProcessModelType modelType, ProcessRuntimeOwnership ownership) {
        private RuntimeBinding {
            Objects.requireNonNull(modelType, "modelType");
            Objects.requireNonNull(ownership, "ownership");
        }
    }

    private record RuntimeInterfaces(EnumMap<ProcessModelType, ProcessRuntimeOwnership> ownership,
            EnumMap<ProcessModelType, ProcessCallInspector> inspectors,
            EnumMap<ProcessModelType, ProcessExecutionGraphPreparer> graphPreparers) {}

    /**
     * Per-version operation sequencer. Only operations for the same exact version are serialized;
     * external engine calls never run under a {@link ConcurrentHashMap} bin lock. The reservation
     * count prevents a slot from being removed while another caller is active or waiting.
     */
    private static final class RuntimeSlot {
        private final ReentrantLock operationLock = new ReentrantLock();
        private volatile RuntimeBinding binding;
        private int reservations;

        private void reserve() {
            reservations++;
        }

        private boolean releaseReservationAndIsUnused() {
            if (--reservations < 0) {
                throw new IllegalStateException("Runtime slot reservation count underflow");
            }
            return reservations == 0 && binding == null;
        }

        private void lock() {
            operationLock.lock();
        }

        private void unlock() {
            operationLock.unlock();
        }

        private RuntimeBinding select(RuntimeBinding proposed, ProcessRef.Version ref) {
            RuntimeBinding selected = binding == null ? proposed : binding;
            if (selected.modelType() != proposed.modelType()) {
                throw modelTypeChanged(ref);
            }
            return selected;
        }

        private RuntimeBinding bindingOr(RuntimeBinding fallback) {
            return binding == null ? fallback : binding;
        }

        private void publish(RuntimeBinding installed) {
            binding = installed;
        }

        private void clear() {
            binding = null;
        }

        private static DeploymentException modelTypeChanged(ProcessRef.Version ref) {
            return DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH,
                    "Published version model type changed after local installation", ref);
        }
    }
}
