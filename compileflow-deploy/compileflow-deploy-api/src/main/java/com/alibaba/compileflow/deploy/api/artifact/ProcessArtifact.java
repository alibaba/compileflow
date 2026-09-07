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
package com.alibaba.compileflow.deploy.api.artifact;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable published Version, exact definition, and source-derived direct Process calls.
 *
 * @author yusu
 */
public final class ProcessArtifact {
    private final ProcessRef.Version ref;
    private final ProcessDefinition.Inline definition;
    private final String artifactDigest;
    private final Map<String, ProcessCallBinding> callBindings;

    /**
     * Creates an immutable artifact value.
     *
     * <p>This value may be created before a trust boundary verifies the declared digest. Protocol
     * parsers and runtime loaders remain responsible for comparing it with the exact definition.
     *
     * @param ref          immutable process-version identity
     * @param definition   exact inline process definition
     * @param artifactDigest lowercase SHA-256 digest of the executable artifact
     */
    public ProcessArtifact(ProcessRef.Version ref, ProcessDefinition.Inline definition, String artifactDigest) {
        this(ref, definition, artifactDigest, List.of());
    }

    /**
     * Creates an immutable artifact with exact direct call-site bindings.
     *
     * @param ref immutable process-version identity
     * @param definition exact inline process definition
     * @param artifactDigest lowercase SHA-256 digest of the executable artifact
     * @param callBindings source-derived exact direct calls
     */
    public ProcessArtifact(ProcessRef.Version ref, ProcessDefinition.Inline definition, String artifactDigest,
            List<ProcessCallBinding> callBindings) {
        this.ref = Objects.requireNonNull(ref, "ref");
        this.definition = Objects.requireNonNull(definition, "definition");
        if (!ref.code().equals(definition.code())) {
            throw new IllegalArgumentException("Artifact reference code and definition code must match");
        }
        this.artifactDigest = requireSha256(artifactDigest);
        Map<String, ProcessCallBinding> bindings = new LinkedHashMap<>();
        for (ProcessCallBinding binding : Objects.requireNonNull(callBindings, "callBindings")) {
            ProcessCallBinding value = Objects.requireNonNull(binding, "callBindings contains null");
            if (!ref.namespace().equals(value.target().namespace())) {
                throw new IllegalArgumentException("Call target must inherit the artifact namespace");
            }
            if (bindings.putIfAbsent(value.callSiteId(), value) != null) {
                throw new IllegalArgumentException("Call bindings must have unique callSiteId values");
            }
        }
        this.callBindings = Collections.unmodifiableMap(bindings);
    }

    private static String requireSha256(String value) {
        String digestValue = Objects.requireNonNull(value, "digest");
        if (!digestValue.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a lowercase SHA-256 hexadecimal value");
        }
        return digestValue;
    }

    /**
     * Returns the immutable process-version identity.
     *
     * @return process-version reference
     */
    public ProcessRef.Version getRef() {
        return ref;
    }

    /**
     * Returns the exact inline process definition.
     *
     * @return immutable definition value
     */
    public ProcessDefinition.Inline getDefinition() {
        return definition;
    }

    /**
     * Returns the declared content digest.
     *
     * @return lowercase SHA-256 hexadecimal digest
     */
    public String getArtifactDigest() {
        return artifactDigest;
    }

    /**
     * Returns source-derived exact call-site bindings keyed by call ID.
     *
     * @return immutable call-site binding map
     */
    public Map<String, ProcessCallBinding> getCallBindings() {
        return callBindings;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProcessArtifact that)) {
            return false;
        }
        return ref.equals(that.ref) && definition.equals(that.definition) && artifactDigest.equals(that.artifactDigest)
                && callBindings.equals(that.callBindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ref, definition, artifactDigest, callBindings);
    }

    @Override
    public String toString() {
        return "ProcessArtifact{ref=" + ref + ", definition=" + definition + ", callSiteIds=" + callBindings.keySet()
                + '}';
    }
}
