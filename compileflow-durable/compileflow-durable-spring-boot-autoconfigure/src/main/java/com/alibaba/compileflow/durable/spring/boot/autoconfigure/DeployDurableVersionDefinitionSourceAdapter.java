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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure;

import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring composition adapter that imports verified exact Process facts from Deploy.
 *
 * @author yusu
 */
final class DeployDurableVersionDefinitionSourceAdapter implements DurableVersionDefinitionSource {
    private final ProcessArtifactSource source;

    DeployDurableVersionDefinitionSourceAdapter(ProcessArtifactSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Optional<VersionDefinition> find(ProcessRef.Version version) {
        ProcessRef.Version requested = Objects.requireNonNull(version, "version");
        Optional<ProcessArtifact> result =
                Objects.requireNonNull(source.find(requested), "Deploy Artifact source returned null lookup");
        return result.map(artifact -> adapt(requested, artifact));
    }

    private static VersionDefinition adapt(ProcessRef.Version requested, ProcessArtifact artifact) {
        if (!requested.equals(artifact.getRef())) {
            throw new IllegalStateException("Deploy Artifact source returned a different Process Version");
        }
        Map<String, ProcessRef.Version> targets = artifact
            .getCallBindings()
            .values()
            .stream()
            .collect(Collectors.toMap(ProcessCallBinding::callSiteId, ProcessCallBinding::target));
        String computedDigest =
                ProcessArtifactDigest.compute(artifact.getModelType(), artifact.getDefinition(), targets);
        if (!artifact.getArtifactDigest().equals(computedDigest)) {
            throw DurableProcessException.of(DurableErrorCode.ARTIFACT_DIGEST_MISMATCH,
                    "Deploy Process Artifact content does not match its declared digest");
        }
        return new VersionDefinition(artifact.getModelType(), artifact.getDefinition(), targets);
    }
}
