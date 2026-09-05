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
package com.alibaba.compileflow.deploy.api.command;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.api.release.ReleaseMetadata;
import java.util.Map;
import java.util.Objects;

/**
 * Requests publication of exact inline content under an immutable version identity.
 *
 * @author yusu
 */
public final class PublishProcessVersionCommand {
    private final ProcessRef.Version ref;
    private final ProcessModelType modelType;
    private final ProcessDefinition.Inline definition;
    private final String expectedArtifactDigest;
    private final String actor;
    private final Map<String, String> metadata;

    /**
     * Creates a publication command for exact inline source bytes.
     *
     * @param ref        immutable published-version identity
     * @param modelType  process definition format
     * @param definition exact inline definition to publish
     * @param actor      authenticated publication actor
     * @param metadata   bounded publication metadata
     */
    public PublishProcessVersionCommand(ProcessRef.Version ref, ProcessModelType modelType,
            ProcessDefinition.Inline definition, String actor, Map<String, String> metadata) {
        this(ref, modelType, definition, null, actor, metadata);
    }

    /**
     * Creates a publication command with an optional executable-artifact integrity precondition.
     *
     * @param ref                    immutable published-version identity
     * @param modelType              process definition format
     * @param definition             exact inline definition to publish
     * @param expectedArtifactDigest optional lowercase SHA-256 digest expected after call binding
     *                               resolution
     * @param actor                  authenticated publication actor
     * @param metadata               bounded descriptive publication metadata
     */
    public PublishProcessVersionCommand(ProcessRef.Version ref, ProcessModelType modelType,
            ProcessDefinition.Inline definition, String expectedArtifactDigest, String actor,
            Map<String, String> metadata) {
        this.ref = Objects.requireNonNull(ref, "ref");
        this.modelType = Objects.requireNonNull(modelType, "modelType");
        this.definition = Objects.requireNonNull(definition, "definition");
        if (!ref.code().equals(definition.code())) {
            throw new IllegalArgumentException("Version reference and definition code must match");
        }
        this.expectedArtifactDigest = ProcessIdentifiers.optionalSha256(expectedArtifactDigest, "expectedArtifactDigest");
        this.actor = DeploymentAudit.requireActor(actor);
        this.metadata = ReleaseMetadata.immutableCopy(metadata);
    }

    /**
     * Returns the immutable version identity.
     *
     * @return version reference
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
     * Returns the exact inline definition.
     *
     * @return immutable inline definition
     */
    public ProcessDefinition.Inline getDefinition() {
        return definition;
    }

    /**
     * Returns the optional executable-artifact digest precondition.
     *
     * @return expected lowercase SHA-256 digest, or {@code null}
     */
    public String getExpectedArtifactDigest() {
        return expectedArtifactDigest;
    }

    /**
     * Returns the authenticated actor.
     *
     * @return actor identity
     */
    public String getActor() {
        return actor;
    }

    /**
     * Returns immutable publication metadata.
     *
     * @return metadata snapshot
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }
}
