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

import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Projects the persisted version winner to the configured artifact transport.
 *
 * @author yusu
 */
public final class ArtifactProjectionCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactProjectionCoordinator.class);
    private final boolean projectionRequired;
    private final ArtifactProjection inspectProjection;
    private final ArtifactProjection repairProjection;

    private ArtifactProjectionCoordinator(boolean projectionRequired, ArtifactProjection inspectProjection,
            ArtifactProjection repairProjection) {
        this.projectionRequired = projectionRequired;
        this.inspectProjection = Objects.requireNonNull(inspectProjection, "inspectProjection");
        this.repairProjection = Objects.requireNonNull(repairProjection, "repairProjection");
    }

    public static ArtifactProjectionCoordinator source() {
        ArtifactProjection notRequired =
                version -> {
            LOGGER.debug("Artifact projection skipped (SOURCE mode): ns={} code={} version={}", version.getNamespace(),
                    version.getCode(), version.getVersion());
            return ArtifactProjectionStatus.NOT_REQUIRED;
        };
        return new ArtifactProjectionCoordinator(false, notRequired, notRequired);
    }

    public static ArtifactProjectionCoordinator projectionStore(ProcessArtifactProjector projector) {
        ProcessArtifactProjector artifactProjector =
                Objects.requireNonNull(projector, "ProcessArtifactProjector is required for PROJECTION_STORE mode");
        ArtifactProjection inspectProjection =
                version -> artifactProjector.inspect(version.getRef(), version.getProcessDefinition(),
                        version.getArtifactDigest(), version.getCallBindings());
        ArtifactProjection repairProjection =
                version -> artifactProjector.repair(version.getRef(), version.getProcessDefinition(),
                        version.getArtifactDigest(), version.getCallBindings());
        return new ArtifactProjectionCoordinator(true, inspectProjection, repairProjection);
    }

    public boolean isProjectionRequired() {
        return projectionRequired;
    }

    public void ensureProjected(ProcessVersionRecord version) {
        repair(version);
    }

    public ArtifactProjectionStatus inspect(ProcessVersionRecord version) {
        return project(version, inspectProjection);
    }

    public ArtifactProjectionStatus repair(ProcessVersionRecord version) {
        return project(version, repairProjection);
    }

    private ArtifactProjectionStatus project(ProcessVersionRecord version, ArtifactProjection projection) {
        ProcessVersionRecord published = Objects.requireNonNull(version, "version");
        try {
            return projection.project(published);
        } catch (DeploymentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw DeploymentException
                .builder(DeploymentErrorCode.ARTIFACT_PROJECTION_FAILED,
                        "Failed to reconcile persisted process artifact", failure)
                .namespace(published.getNamespace())
                .code(published.getCode())
                .version(published.getVersion())
                .build();
        }
    }

    @FunctionalInterface
    private interface ArtifactProjection {
        ArtifactProjectionStatus project(ProcessVersionRecord version) throws Exception;
    }
}
