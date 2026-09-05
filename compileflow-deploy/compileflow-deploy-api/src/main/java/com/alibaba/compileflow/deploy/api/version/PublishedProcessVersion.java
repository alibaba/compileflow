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
package com.alibaba.compileflow.deploy.api.version;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.api.release.ReleaseMetadata;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable public view of one published process version.
 *
 * @author yusu
 */
public final class PublishedProcessVersion {
    private final ProcessRef.Version ref;
    private final ProcessModelType modelType;
    private final String artifactDigest;
    private final Map<String, String> metadata;
    private final String actor;
    private final Instant createdAt;

    /**
     * Creates an immutable published-version view.
     *
     * @param ref          exact version reference
     * @param modelType    process definition format
     * @param artifactDigest lowercase SHA-256 digest of the executable artifact
     * @param metadata     immutable publication metadata
     * @param actor        authenticated publication actor
     * @param createdAt    authority timestamp in epoch milliseconds
     */
    public PublishedProcessVersion(ProcessRef.Version ref, ProcessModelType modelType, String artifactDigest,
            Map<String, String> metadata, String actor, Instant createdAt) {
        this.ref = Objects.requireNonNull(ref, "ref");
        this.modelType = Objects.requireNonNull(modelType, "modelType");
        this.artifactDigest = requireDigest(artifactDigest);
        this.metadata = ReleaseMetadata.immutableCopy(metadata);
        this.actor = DeploymentAudit.requireActor(actor);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!createdAt.isAfter(Instant.EPOCH)) {
            throw new IllegalArgumentException("createdAt must be after the epoch");
        }
    }

    private static String requireDigest(String value) {
        String digest = Objects.requireNonNull(value, "artifactDigest");
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("artifactDigest must be a lowercase SHA-256 hexadecimal value");
        }
        return digest;
    }

    /**
     * Returns the exact published-version reference.
     *
     * @return immutable version reference
     */
    public ProcessRef.Version getRef() {
        return ref;
    }

    /**
     * Returns the process definition format.
     *
     * @return model type
     */
    public ProcessModelType getModelType() {
        return modelType;
    }

    /**
     * Returns the SHA-256 digest of the executable artifact.
     *
     * @return lowercase hexadecimal artifact digest
     */
    public String getArtifactDigest() {
        return artifactDigest;
    }

    /**
     * Returns immutable publication metadata.
     *
     * @return metadata snapshot
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Returns the authenticated publication actor.
     *
     * @return actor identity
     */
    public String getActor() {
        return actor;
    }

    /**
     * Returns the authority timestamp.
     *
     * @return epoch milliseconds
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
