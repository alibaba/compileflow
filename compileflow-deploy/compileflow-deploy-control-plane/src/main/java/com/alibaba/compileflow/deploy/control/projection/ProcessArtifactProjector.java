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
package com.alibaba.compileflow.deploy.control.projection;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.protocol.ProcessArtifactKeys;
import com.alibaba.compileflow.deploy.protocol.ProcessArtifactCodec;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes immutable version projections to a deployment projection store.
 *
 * <p>The database version record is authoritative. This projector accepts only that persisted
 * winner and refuses to overwrite an existing projection store key with a different or corrupt payload.
 *
 * @author yusu
 */
public final class ProcessArtifactProjector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessArtifactProjector.class);
    private final DeploymentProjectionStore projectionStore;
    private final String artifactPrefix;
    private final Duration operationTimeout;

    public ProcessArtifactProjector(DeploymentProjectionStore projectionStore, String artifactPrefix,
            Duration operationTimeout) {
        this.projectionStore = Objects.requireNonNull(projectionStore, "projectionStore");
        String configuredPrefix = ProcessText.requireNonBlank(artifactPrefix, "artifactPrefix");
        this.artifactPrefix = ProcessArtifactKeys.requirePrefix(configuredPrefix);
        this.operationTimeout = Objects.requireNonNull(operationTimeout, "operationTimeout");
        if (operationTimeout.isZero() || operationTimeout.isNegative()) {
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
    }

    private static void verifyExisting(ProcessArtifact expected, String payload) {
        ProcessArtifact existing = ProcessArtifactCodec.parse(payload);
        if (!expected.equals(existing)) {
            throw DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH,
                    "Artifact projection store key already contains a different immutable version", expected.getRef());
        }
    }

    /**
     * Inspects one immutable projection without creating it when absent.
     *
     * @param ref           exact published-version reference
     * @param definition    persisted inline definition
     * @param digest        persisted artifact digest
     * @return bounded inspection result
     * @throws Exception when the projection store cannot be read or updated
     */
    public ArtifactProjectionStatus inspect(ProcessRef.Version ref, ProcessDefinition.Inline definition, String digest)
            throws Exception {
        return inspect(ref, definition, digest, List.of());
    }

    public ArtifactProjectionStatus repair(ProcessRef.Version ref, ProcessDefinition.Inline definition, String digest)
            throws Exception {
        return repair(ref, definition, digest, List.of());
    }

    public ArtifactProjectionStatus inspect(ProcessRef.Version ref, ProcessDefinition.Inline definition, String digest,
            List<ProcessCallBinding> callBindings) throws Exception {
        return project(ref, definition, digest, callBindings, false);
    }

    public ArtifactProjectionStatus repair(ProcessRef.Version ref, ProcessDefinition.Inline definition, String digest,
            List<ProcessCallBinding> callBindings) throws Exception {
        return project(ref, definition, digest, callBindings, true);
    }

    private ArtifactProjectionStatus project(ProcessRef.Version ref, ProcessDefinition.Inline definition, String digest,
            List<ProcessCallBinding> callBindings, boolean repairMissing) throws Exception {
        ProcessArtifact artifact = new ProcessArtifact(ref, definition, digest, callBindings);
        ProcessRef.Version versionRef = artifact.getRef();

        String key = ProcessArtifactKeys.versioned(artifactPrefix, versionRef.namespace(), versionRef.code(),
                versionRef.version());
        String payload = ProcessArtifactCodec.artifactJson(artifact);
        String existingPayload = projectionStore.read(key, operationTimeout);
        if (existingPayload != null) {
            verifyExisting(artifact, existingPayload);
            LOGGER.debug("Process artifact projection already exists: key={}", key);
            return ArtifactProjectionStatus.PRESENT;
        }
        if (!repairMissing) {
            return ArtifactProjectionStatus.MISSING;
        }

        if (projectionStore.compareAndSet(key, null, payload, "application/json", operationTimeout)) {
            LOGGER.debug("Process artifact projection created: key={}", key);
            return ArtifactProjectionStatus.REPAIRED;
        }

        String concurrentWinner = projectionStore.read(key, operationTimeout);
        if (concurrentWinner == null) {
            throw new IllegalStateException(
                    "Artifact projection store key changed concurrently but no winner is readable: key=" + key);
        }
        verifyExisting(artifact, concurrentWinner);
        LOGGER.debug("Concurrent process artifact projection is identical: key={}", key);
        return ArtifactProjectionStatus.PRESENT;
    }
}
