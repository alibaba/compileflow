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
package com.alibaba.compileflow.deploy.spi.store;

import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.api.release.ReleaseMetadata;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable persistence model for an exact published process version.
 *
 * <p>Existence of this record is the publication fact. Runtime readiness is tracked by each
 * execution node and is not a property of the published version.
 *
 * @author yusu
 */
public final class ProcessVersionRecord {
    private final ProcessRef.Version ref;
    private final ProcessDefinition.Inline processDefinition;
    private final String artifactDigest;
    private final List<ProcessCallBinding> callBindings;
    private final Map<String, String> metadata;
    private final String actor;
    private final long createdAt;

    private ProcessVersionRecord(Builder builder) {
        ref = ProcessRef.version(builder.namespace, builder.code, builder.version);
        processDefinition = Objects.requireNonNull(builder.processDefinition, "processDefinition");
        if (!ref.code().equals(processDefinition.code())) {
            throw new IllegalArgumentException("Record reference and process definition code must match");
        }
        ProcessArtifact artifact =
                new ProcessArtifact(ref, processDefinition, builder.artifactDigest, builder.callBindings);
        artifactDigest = artifact.getArtifactDigest();
        callBindings = List.copyOf(artifact.getCallBindings().values());
        Map<String, ProcessRef.Version> callTargets = artifact
            .getCallBindings()
            .values()
            .stream()
            .collect(java.util.stream.Collectors.toMap(ProcessCallBinding::callSiteId, ProcessCallBinding::target));
        String computedDigest = ProcessArtifactDigest.compute(processDefinition, callTargets);
        if (!artifactDigest.equals(computedDigest)) {
            throw new IllegalArgumentException("artifactDigest does not match the executable process artifact");
        }
        metadata = ReleaseMetadata.immutableCopy(builder.metadata);
        actor = DeploymentAudit.requireActor(builder.actor);
        if (builder.createdAt <= 0L) {
            throw new IllegalArgumentException("createdAt must be positive");
        }
        createdAt = builder.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ProcessRef.Version getRef() {
        return ref;
    }

    public String getCode() {
        return ref.code();
    }

    public String getVersion() {
        return ref.version();
    }

    public String getNamespace() {
        return ref.namespace();
    }

    public ProcessModelType getModelType() {
        return processDefinition.modelType();
    }

    public ProcessDefinition.Inline getProcessDefinition() {
        return processDefinition;
    }

    public String getArtifactDigest() {
        return artifactDigest;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public List<ProcessCallBinding> getCallBindings() {
        return callBindings;
    }

    public String getActor() {
        return actor;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the content-only view consumed by deployment runtimes.
     *
     * @return immutable artifact without control-plane audit fields
     */
    public ProcessArtifact toArtifact() {
        return new ProcessArtifact(ref, processDefinition, artifactDigest, callBindings);
    }

    @Override
    public String toString() {
        return "ProcessVersionRecord{ref=" + ref + ", modelType=" + processDefinition.modelType() + ", metadataKeys="
                + metadata.keySet() + ", actor='" + actor + '\'' + ", createdAt=" + createdAt + '}';
    }

    /**
     * Builder for immutable version records.
     */
    public static final class Builder {
        private String code;
        private String version;
        private String namespace;
        private ProcessDefinition.Inline processDefinition;
        private String artifactDigest;
        private List<ProcessCallBinding> callBindings = List.of();
        private Map<String, String> metadata = Collections.emptyMap();
        private String actor;
        private long createdAt;

        private Builder() {
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder processDefinition(ProcessDefinition.Inline processDefinition) {
            this.processDefinition = processDefinition;
            return this;
        }

        public Builder artifactDigest(String value) {
            this.artifactDigest = value;
            return this;
        }

        public Builder callBindings(List<ProcessCallBinding> value) {
            this.callBindings = value;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ProcessVersionRecord build() {
            return new ProcessVersionRecord(this);
        }
    }
}
