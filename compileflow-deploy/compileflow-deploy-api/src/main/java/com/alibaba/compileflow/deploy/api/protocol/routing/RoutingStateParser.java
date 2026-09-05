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
package com.alibaba.compileflow.deploy.api.protocol.routing;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.api.protocol.json.DeploymentProtocolJson;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Parses and verifies untrusted alias-routing payloads at the runtime trust boundary.
 *
 * @author yusu
 */
public final class RoutingStateParser {
    private static final int SCHEMA_VERSION = 1;
    private static final Set<String> ALLOWED_FIELDS = Set.of("schemaVersion", "kind", "namespace", "code", "alias",
            "stableVersion", "candidateVersion", "candidateWeightBps", "targetingPolicy", "targetingParameters",
            "deleted", "revision", "actor", "updatedAt");

    private RoutingStateParser() {
    }

    /**
     * Parses one alias-routing payload.
     *
     * <p>The parser rejects unknown fields, unsupported schema versions, malformed identities,
     * inconsistent candidate state, and invalid revisions. Blank input represents no published
     * value and returns {@code null}.
     *
     * @param json routing-state JSON, or blank when no value exists
     * @return validated routing update, or {@code null} for blank input
     * @throws DeploymentException with {@link DeploymentErrorCode#INVALID_ARGUMENT} for invalid input
     */
    public static RoutingStateUpdate parse(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        String trimmed = StringUtils.trimToEmpty(json);
        if (!trimmed.startsWith("{")) {
            throw invalid("Routing state payload must be a JSON object");
        }
        try {
            Map<String, Object> payload = DeploymentProtocolJson.readObject(json);
            if (payload == null || payload.isEmpty()) {
                throw invalid("Routing state payload must not be empty");
            }
            for (String field : payload.keySet()) {
                if (!ALLOWED_FIELDS.contains(field)) {
                    throw invalid("Routing state payload contains unsupported field: " + field);
                }
            }
            int schemaVersion = readRequiredInt(payload, "schemaVersion");
            if (schemaVersion != SCHEMA_VERSION) {
                throw invalid("Unsupported routing state schemaVersion: " + schemaVersion);
            }
            String kind = readRequiredText(payload, "kind");
            if (!AliasStatePayload.KIND.equals(kind)) {
                throw invalid("Unsupported routing state kind: " + kind);
            }
            ProcessRef.Alias ref = ProcessRef.alias(readRequiredText(payload, "namespace"),
                    readRequiredText(payload, "code"), readRequiredText(payload, "alias"));
            String actor = DeploymentAudit.requireActor(readRequiredText(payload, "actor"));
            long revision = readRequiredLong(payload, "revision");
            if (revision <= 0) {
                throw invalid("Routing state payload requires a positive revision");
            }
            long updatedAt = readRequiredLong(payload, "updatedAt");
            if (updatedAt <= 0) {
                throw invalid("Routing state payload requires a positive updatedAt");
            }
            boolean deleted = readOptionalBoolean(payload, "deleted");
            if (deleted) {
                rejectPresent(payload, "stableVersion");
                rejectPresent(payload, "candidateVersion");
                rejectPresent(payload, "candidateWeightBps");
                rejectPresent(payload, "targetingPolicy");
                rejectPresent(payload, "targetingParameters");
                return new RoutingStateUpdate(ref.namespace(), ref.code(), ref.alias(), null, null, 0, null, true,
                        revision, actor, updatedAt);
            }

            String stableVersion =
                    ProcessRef
                .version(ref.namespace(), ref.code(), readRequiredText(payload, "stableVersion"))
                .version();
            String candidateVersion = readOptionalText(payload, "candidateVersion");
            int candidateWeightBps = 0;
            AliasTargeting targeting;
            if (candidateVersion == null) {
                rejectPresent(payload, "candidateWeightBps");
                rejectPresent(payload, "targetingPolicy");
                rejectPresent(payload, "targetingParameters");
                targeting = null;
            } else {
                candidateVersion = ProcessRef.version(ref.namespace(), ref.code(), candidateVersion).version();
                candidateWeightBps = readRequiredInt(payload, "candidateWeightBps");
                if (stableVersion.equals(candidateVersion)) {
                    throw invalid("stableVersion and candidateVersion must differ");
                }
                if (candidateWeightBps <= 0 || candidateWeightBps >= 10_000) {
                    throw invalid("candidateWeightBps must be between 1 and 9999");
                }
                targeting = readTargeting(payload);
            }
            return new RoutingStateUpdate(ref.namespace(), ref.code(), ref.alias(), stableVersion, candidateVersion,
                    candidateWeightBps, targeting, false, revision, actor, updatedAt);
        } catch (DeploymentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Routing state payload is invalid", exception);
        }
    }

    private static DeploymentException invalid(String message) {
        return DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT, message);
    }

    private static DeploymentException invalid(String message, Exception cause) {
        return DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT, message, cause);
    }

    private static String readRequiredText(Map<String, Object> payload, String field) {
        String value = readOptionalText(payload, field);
        if (value == null) {
            throw invalid("Routing state payload requires " + field);
        }
        return value;
    }

    private static String readOptionalText(Map<String, Object> payload, String field) {
        if (!payload.containsKey(field)) {
            return null;
        }
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

    private static int readRequiredInt(Map<String, Object> payload, String field) {
        long value = readRequiredLong(payload, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid(field + " is outside the integer range");
        }
        return (int) value;
    }

    private static long readRequiredLong(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger integer) {
            try {
                return integer.longValueExact();
            } catch (ArithmeticException exception) {
                throw invalid(field + " is outside the long range", exception);
            }
        }
        throw invalid(field + " must be an integer");
    }

    private static boolean readOptionalBoolean(Map<String, Object> payload, String field) {
        if (!payload.containsKey(field)) {
            return false;
        }
        Object value = payload.get(field);
        if (!(value instanceof Boolean flag)) {
            throw invalid(field + " must be a boolean");
        }
        return flag;
    }

    private static AliasTargeting readTargeting(Map<String, Object> payload) {
        String policy = readOptionalText(payload, "targetingPolicy");
        if (policy == null) {
            rejectPresent(payload, "targetingParameters");
            return null;
        }
        Object rawParameters = payload.get("targetingParameters");
        if (rawParameters == null) {
            return new AliasTargeting(policy);
        }
        if (!(rawParameters instanceof Map<?, ?> values)) {
            throw invalid("targetingParameters must be an object");
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof String value)) {
                throw invalid("targetingParameters names and values must be strings");
            }
            parameters.put(name, value);
        }
        return new AliasTargeting(policy, parameters);
    }

    private static void rejectPresent(Map<String, Object> payload, String field) {
        if (payload.containsKey(field)) {
            throw invalid(field + " is not allowed for this alias state");
        }
    }
}
