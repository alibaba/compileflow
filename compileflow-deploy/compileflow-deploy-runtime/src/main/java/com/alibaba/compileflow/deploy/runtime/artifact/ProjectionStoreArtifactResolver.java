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
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.protocol.ProcessArtifactKeys;
import com.alibaba.compileflow.deploy.protocol.ProcessArtifactCodec;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import java.time.Duration;
import java.util.Objects;

/**
 * Resolves immutable artifacts from a distributed deployment projection store.
 *
 * @author yusu
 */
public final class ProjectionStoreArtifactResolver implements ProcessArtifactResolver {
    private final DeploymentProjectionStore projectionStore;
    private final String keyPrefix;
    private final Duration operationTimeout;

    public ProjectionStoreArtifactResolver(DeploymentProjectionStore projectionStore, String keyPrefix,
            Duration operationTimeout) {
        this.projectionStore = Objects.requireNonNull(projectionStore, "projectionStore");
        String configuredPrefix = ProcessText.requireNonBlank(keyPrefix, "keyPrefix");
        this.keyPrefix = ProcessArtifactKeys.requirePrefix(configuredPrefix);
        this.operationTimeout = Objects.requireNonNull(operationTimeout, "operationTimeout");
        if (operationTimeout.isZero() || operationTimeout.isNegative()) {
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
    }

    @Override
    public ProcessArtifact resolve(ProcessRef.Version key) throws Exception {
        Objects.requireNonNull(key, "key");
        String contentKey = ProcessArtifactKeys.versioned(keyPrefix, key.namespace(), key.code(), key.version());
        String payload = projectionStore.read(contentKey, operationTimeout);
        if (payload == null || payload.isBlank()) {
            throw DeploymentException
                .builder(DeploymentErrorCode.VERSION_NOT_FOUND,
                        "Process artifact missing in projection store: key=" + contentKey)
                .namespace(key.namespace())
                .code(key.code())
                .version(key.version())
                .build();
        }
        ProcessArtifact parsed = ProcessArtifactCodec.parse(payload);
        ProcessRef.Version ref = parsed.getRef();

        if (!key.equals(ref)) {
            throw DeploymentException
                .builder(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH,
                        "Process artifact identity mismatch: want=" + key + " got=" + ref.namespace() + "/" + ref.code()
                        + "/" + ref.version())
                .namespace(key.namespace())
                .code(key.code())
                .version(key.version())
                .build();
        }

        return parsed;
    }
}
