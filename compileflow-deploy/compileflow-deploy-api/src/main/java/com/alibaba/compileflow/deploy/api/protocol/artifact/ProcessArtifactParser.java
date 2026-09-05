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
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.protocol.json.DeploymentProtocolJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Parses and verifies artifact protocol payloads at the runtime trust boundary.
 *
 * @author yusu
 */
public final class ProcessArtifactParser {
    private static final int SCHEMA_VERSION = 1;
    private static final Set<String> ALLOWED_FIELDS = Set.of("schemaVersion", "namespace", "code", "version",
            "modelType", "content", "artifactDigest", "callBindings");

    private ProcessArtifactParser() {
    }

    /**
     * Parses and verifies one untrusted artifact payload.
     *
     * <p>The parser rejects unknown fields, unsupported schema versions, malformed identities,
     * and content whose SHA-256 digest differs from the declared digest.
     *
     * @param json non-blank JSON object
     * @return verified immutable artifact
     * @throws DeploymentException with a bounded error code when the payload is invalid
     */
    public static ProcessArtifact parse(String json) {
        if (StringUtils.isBlank(json)) {
            throw invalid("Process artifact payload must be non-blank JSON");
        }
        String trimmed = StringUtils.trimToEmpty(json);
        if (!trimmed.startsWith("{")) {
            throw invalid("Process artifact payload must be a JSON object");
        }
        try {
            Map<String, Object> payload = DeploymentProtocolJson.readObject(json);
            if (payload == null || payload.isEmpty()) {
                throw invalid("Process artifact payload must not be empty");
            }
            for (String field : payload.keySet()) {
                if (!ALLOWED_FIELDS.contains(field)) {
                    throw invalid("Process artifact payload contains unsupported field: " + field);
                }
            }
            int schemaVersion = readRequiredInt(payload, "schemaVersion");
            if (schemaVersion != SCHEMA_VERSION) {
                throw invalid("Unsupported process artifact schemaVersion: " + schemaVersion);
            }

            ProcessRef.Version ref = ProcessRef.version(readRequiredTrimmedText(payload, "namespace"),
                    readRequiredTrimmedText(payload, "code"), readRequiredTrimmedText(payload, "version"));
            ProcessModelType modelType = ProcessModelType.valueOf(readRequiredTrimmedText(payload, "modelType"));
            String content = readRequiredContent(payload);
            String artifactDigest = readRequiredTrimmedText(payload, "artifactDigest");
            ProcessDefinition.Inline definition = ProcessDefinition.inline(ref.code(), content);
            List<ProcessCallBinding> callBindings = readCallBindings(payload);
            Map<String, ProcessRef.Version> targets =
                    callBindings
                .stream()
                .collect(Collectors.toMap(ProcessCallBinding::callSiteId, ProcessCallBinding::target));
            String actualDigest = ProcessArtifactDigest.compute(modelType, definition, targets);
            if (!actualDigest.equals(artifactDigest)) {
                throw DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH,
                        "Process artifact does not match its declared artifactDigest", ref);
            }
            return new ProcessArtifact(ref, modelType, definition, artifactDigest, callBindings);
        } catch (DeploymentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Process artifact payload is invalid", exception);
        }
    }

    private static String readRequiredTrimmedText(Map<String, Object> payload, String field) {
        Object raw = payload.get(field);
        if (!(raw instanceof String text)) {
            throw invalid(field + " must be a string");
        }
        String value = ProcessText.strip(ProcessText.requireUnicode(text, field));
        if (value.isEmpty() || !value.equals(text)) {
            throw invalid(field + " must be non-blank and trimmed");
        }
        return value;
    }

    private static String readRequiredContent(Map<String, Object> payload) {
        Object raw = payload.get("content");
        if (!(raw instanceof String content) || StringUtils.isBlank(content)) {
            throw invalid("content must be a non-blank string");
        }
        return content;
    }

    private static int readRequiredInt(Map<String, Object> payload, String field) {
        Object raw = payload.get(field);
        if (!(raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long)) {
            throw invalid(field + " must be an integer");
        }
        long value = ((Number) raw).longValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid(field + " is outside the integer range");
        }
        return (int) value;
    }

    private static List<ProcessCallBinding> readCallBindings(Map<String, Object> payload) {
        Object raw = payload.get("callBindings");
        if (!(raw instanceof List<?> list)) {
            throw invalid("callBindings must be an array");
        }
        List<ProcessCallBinding> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> binding) || binding.size() != 4
                    || !binding.keySet().equals(Set.of("callSiteId", "code", "namespace", "version"))) {
                throw invalid("callBindings entries must contain callSiteId, code, namespace, and version");
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            binding.forEach((key, value) -> fields.put((String) key, value));
            String code = readRequiredTrimmedText(fields, "code");
            result.add(
                    new ProcessCallBinding(readRequiredTrimmedText(fields, "callSiteId"),
                            ProcessRef.version(readRequiredTrimmedText(fields, "namespace"), code,
                                    readRequiredTrimmedText(fields, "version"))));
        }
        return List.copyOf(result);
    }

    private static DeploymentException invalid(String message) {
        return DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT, message);
    }

    private static DeploymentException invalid(String message, Exception cause) {
        return DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT, message, cause);
    }
}
