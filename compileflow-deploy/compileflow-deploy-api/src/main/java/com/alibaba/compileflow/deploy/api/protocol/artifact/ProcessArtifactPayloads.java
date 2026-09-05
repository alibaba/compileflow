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
package com.alibaba.compileflow.deploy.api.protocol.artifact;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.protocol.json.DeploymentProtocolJson;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;

/**
 * Factory for serializing immutable artifact payloads to the JSON wire format.
 *
 * @author yusu
 */
public final class ProcessArtifactPayloads {
    private ProcessArtifactPayloads() {
    }

    /**
     * Serializes one verified immutable artifact payload.
     *
     * <p>The supplied digest is recomputed and compared before any payload is emitted.
     *
     * @param namespace    process namespace
     * @param code         process code
     * @param version      immutable process version
     * @param modelType    source model format
     * @param content      exact process-definition content
     * @param artifactDigest lowercase SHA-256 digest of the executable published artifact
     * @return compact schema-versioned JSON payload
     * @throws IllegalArgumentException if identity, content, or digest is invalid
     * @throws IllegalStateException    if the validated payload cannot be serialized
     */
    public static String artifactJson(String namespace, String code, String version, ProcessModelType modelType,
            String content, String artifactDigest) {
        return artifactJson(namespace, code, version, modelType, content, artifactDigest, List.of());
    }

    /**
     * Serializes one verified artifact together with its exact direct call-site bindings.
     *
     * <p>The executable artifact digest is recomputed from the definition and bindings before any
     * payload is emitted.
     *
     * @param namespace      process namespace
     * @param code           process code
     * @param version        immutable process version
     * @param modelType      source model format
     * @param content        exact process-definition content
     * @param artifactDigest lowercase SHA-256 digest of the executable published artifact
     * @param callBindings   exact source-derived direct call-site bindings
     * @return compact schema-versioned JSON payload
     * @throws IllegalArgumentException if identity, content, bindings, or digest is invalid
     * @throws IllegalStateException    if the validated payload cannot be serialized
     */
    public static String artifactJson(String namespace, String code, String version, ProcessModelType modelType,
            String content, String artifactDigest, List<ProcessCallBinding> callBindings) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, version);
        ProcessDefinition.Inline definition = ProcessDefinition.inline(ref.code(), content);
        return artifactJson(new ProcessArtifact(ref, modelType, definition, artifactDigest, callBindings));
    }

    /**
     * Serializes one immutable process artifact after verifying its executable digest.
     *
     * @param artifact immutable process artifact
     * @return compact schema-versioned JSON payload
     */
    public static String artifactJson(ProcessArtifact artifact) {
        ProcessArtifact verified = Objects.requireNonNull(artifact, "artifact");
        Map<String, ProcessRef.Version> targets = verified
            .getCallBindings()
            .values()
            .stream()
            .collect(java.util.stream.Collectors.toMap(ProcessCallBinding::callSiteId, ProcessCallBinding::target));
        if (!ProcessArtifactDigest
            .compute(verified.getModelType(), verified.getDefinition(), targets)
            .equals(verified.getArtifactDigest())) {
            throw new IllegalArgumentException("artifactDigest does not match the executable process artifact");
        }
        List<ProcessCallBindingPayload> payloadBindings = verified
            .getCallBindings()
            .values()
            .stream()
            .sorted(java.util.Comparator.comparing(ProcessCallBinding::callSiteId))
            .map(binding -> new ProcessCallBindingPayload(binding.callSiteId(), binding.code(),
                    binding.target().namespace(), binding.target().version()))
            .toList();
        ProcessRef.Version ref = verified.getRef();
        ProcessArtifactPayload payload = new ProcessArtifactPayload(ref.namespace(), ref.code(), ref.version(),
                verified.getModelType(), verified.getDefinition().content(), verified.getArtifactDigest(),
                payloadBindings);
        try {
            return DeploymentProtocolJson.write(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize process artifact payload", exception);
        }
    }
}
