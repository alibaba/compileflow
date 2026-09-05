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
package com.alibaba.compileflow.deploy.runtime.artifact;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves immutable artifacts from a control-plane {@link ProcessArtifactSource}.
 *
 * @author yusu
 */
public final class RepositoryProcessArtifactResolver implements ProcessArtifactResolver {
    private final ProcessArtifactSource source;

    public RepositoryProcessArtifactResolver(ProcessArtifactSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    private static DeploymentException identityMismatch(ProcessRef.Version key, String message) {
        return DeploymentException
            .builder(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH, message)
            .namespace(key.namespace())
            .code(key.code())
            .version(key.version())
            .build();
    }

    @Override
    public ProcessArtifact resolve(ProcessRef.Version key) {
        Objects.requireNonNull(key, "key");
        Optional<ProcessArtifact> artifact;
        try {
            artifact = source.find(key);
        } catch (DeploymentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw DeploymentException
                .builder(DeploymentErrorCode.REPOSITORY_ERROR, "Failed to read process artifact from repository",
                        failure)
                .namespace(key.namespace())
                .code(key.code())
                .version(key.version())
                .build();
        }
        ProcessArtifact resolved = artifact.orElseThrow(() -> DeploymentException
            .builder(DeploymentErrorCode.VERSION_NOT_FOUND, "Process artifact not found")
            .namespace(key.namespace())
            .code(key.code())
            .version(key.version())
            .build());
        if (!key.equals(resolved.getRef())) {
            throw identityMismatch(key, "Artifact source returned a different artifact identity");
        }
        return resolved;
    }
}
