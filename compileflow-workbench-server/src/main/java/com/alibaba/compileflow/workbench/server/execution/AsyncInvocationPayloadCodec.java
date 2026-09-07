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
package com.alibaba.compileflow.workbench.server.execution;

import com.alibaba.compileflow.engine.ProcessAliasTarget;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Encodes persisted async payloads and reconstructs their public representation.
 *
 * @author yusu
 */
final class AsyncInvocationPayloadCodec {
    private static final int MAX_DOCUMENT_CHARACTERS = 4 * 1024 * 1024;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };
    private static final ObjectMapper MAPPER = JsonMapper
        .builder(JsonFactory
            .builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints
                .builder()
                .maxDocumentLength(MAX_DOCUMENT_CHARACTERS)
                .maxNestingDepth(64)
                .maxTokenCount(200_000)
                .maxStringLength(MAX_DOCUMENT_CHARACTERS)
                .maxNameLength(512)
                .maxNumberLength(1_000)
                .build())
            .build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    AsyncInvocationPayloadCodec() {
    }

    static String stringValue(Object value) {
        return value instanceof String text ? StringUtils.trimToNull(text) : null;
    }

    private static ExecutionRoutingResponse routingSnapshot(PersistedInvocationRouting storedRouting,
            ProcessExecutionResponse response) {
        ExecutionRoutingResponse responseRouting = response == null ? null : response.routing();
        String namespace = responseRouting == null
                ? storedRouting.namespace()
                : firstPresent(responseRouting.namespace(), storedRouting.namespace());
        String storedVersion = storedRouting.version();
        String storedAlias = storedRouting.alias();
        boolean hasPersistedRequest = storedVersion != null || storedAlias != null;
        String requestedVersion = storedVersion;
        String requestedAlias = storedAlias;
        if (!hasPersistedRequest && responseRouting != null) {
            requestedVersion = responseRouting.requestedVersion();
            requestedAlias = responseRouting.requestedAlias();
        }
        String effectiveVersion = firstPresent(responseRouting == null ? null : responseRouting.effectiveVersion(),
                storedRouting.effectiveVersion());
        if (namespace == null) {
            return null;
        }
        return new ExecutionRoutingResponse(namespace, requestedVersion, requestedAlias, effectiveVersion,
                firstPresent(responseRouting == null ? null : responseRouting.alias(),
                        storedRouteAlias(storedRouting, storedAlias)),
                firstPresent(responseRouting == null ? null : responseRouting.routeRevision(),
                        storedRouting.routeRevision()),
                firstPresent(responseRouting == null ? null : responseRouting.target(), storedRouting.target()));
    }

    private static <T> T firstPresent(T preferred, T fallback) {
        return preferred == null ? fallback : preferred;
    }

    private static String storedRouteAlias(PersistedInvocationRouting storedRouting, String storedAlias) {
        return storedRouting.routeRevision() == null ? null : storedAlias;
    }

    static Long routeRevision(Object value) {
        if (value instanceof BigInteger integer) {
            return integer.signum() > 0 && integer.bitLength() <= 63 ? integer.longValue() : null;
        }
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            return null;
        }
        long revision = ((Number) value).longValue();
        return revision > 0L ? revision : null;
    }

    static ProcessAliasTarget routeTarget(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        try {
            return ProcessAliasTarget.valueOf(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String instant(Long epochMs) {
        return epochMs == null ? null : Instant.ofEpochMilli(epochMs).toString();
    }

    Map<String, Object> readMap(String fieldName, String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> value = MAPPER.readValue(json, MAP_TYPE);
            if (value == null) {
                throw new AsyncInvocationPayloadException(fieldName,
                        new IllegalArgumentException(fieldName + " must be a JSON object"));
            }
            return value;
        } catch (JacksonException failure) {
            throw new AsyncInvocationPayloadException(fieldName, failure);
        }
    }

    PersistedInvocationRouting readRouting(String fieldName, String json) {
        return PersistedInvocationRouting.of(readMap(fieldName, json));
    }

    String write(Object value) {
        String json;
        try {
            json = MAPPER.writeValueAsString(Objects.requireNonNull(value, "value"));
        } catch (JacksonException failure) {
            throw new IllegalStateException("Failed to serialize async invocation payload", failure);
        }
        if (json.length() > MAX_DOCUMENT_CHARACTERS) {
            throw new IllegalArgumentException("Async invocation payload exceeds the character limit");
        }
        // Anything persisted must also be readable by the worker and response codec.
        try {
            MAPPER.readTree(json);
        } catch (JacksonException failure) {
            throw new IllegalArgumentException("Async invocation payload exceeds JSON constraints", failure);
        }
        return json;
    }

    AsyncInvocationResponse toResponse(AsyncInvocationEntity invocation) {
        Map<String, String> payloadErrors = new LinkedHashMap<>();
        PersistedInvocationRouting storedRouting =
                readRoutingForRepresentation("routingJson", invocation.getRoutingJson(), payloadErrors);
        ProcessExecutionResponse response = StringUtils.isBlank(invocation.getResultJson())
                ? null
                : readResponseForRepresentation(invocation.getResultJson(), payloadErrors);
        ExecutionRoutingResponse routing = routingSnapshot(storedRouting, response);
        if (routing == null && !payloadErrors.containsKey("routingJson")) {
            payloadErrors.put("routingJson", "Invalid async invocation routingJson payload: namespace is required");
        }
        return new AsyncInvocationResponse(invocation.getInvocationId(), invocation.getProcessCode(),
                AsyncInvocationStatus.fromValue(invocation.getStatus()), invocation.getAttempts(),
                invocation.getTotalAttempts(), invocation.getRedriveCount(), invocation.getMaxAttempts(),
                invocation.getRetryDelayMs(),
                AsyncInvocationService.STATUS_QUEUED.equals(invocation.getStatus()) ? instant(invocation.getAvailableAt()) : null,
                instant(invocation.getCreatedAt()), instant(invocation.getUpdatedAt()),
                instant(invocation.getStartedAt()), instant(invocation.getCompletedAt()),
                StringUtils.trimToNull(invocation.getTraceId()), instant(invocation.getLeaseUntil()),
                StringUtils.trimToNull(invocation.getErrorMessage()),
                StringUtils.defaultIfBlank(invocation.getErrorCode(), response == null ? null : response.errorCode()),
                invocation.getDurationMs(), routing, response, payloadErrors);
    }

    ProcessExecutionResponse withRoutingSnapshot(PersistedInvocationRouting storedRouting,
            ProcessExecutionResponse response) {
        ExecutionRoutingResponse routing = Objects.requireNonNull(routingSnapshot(storedRouting, response),
                "Completed execution response must contain routing attribution");
        return response.withRouting(routing);
    }

    String routingJsonForExecutedVersion(String routingJson, PersistedInvocationRouting storedRouting,
            ProcessExecutionResponse response) {
        ExecutionRoutingResponse responseRouting = response == null ? null : response.routing();
        String effectiveVersion = responseRouting == null ? null : responseRouting.effectiveVersion();
        if (effectiveVersion == null) {
            return routingJson;
        }
        return write(storedRouting.withEffectiveVersion(effectiveVersion, responseRouting.namespace()).asMap());
    }

    private PersistedInvocationRouting readRoutingForRepresentation(String fieldName, String json,
            Map<String, String> payloadErrors) {
        try {
            return readRouting(fieldName, json);
        } catch (AsyncInvocationPayloadException failure) {
            payloadErrors.put(fieldName, failure.getMessage());
            return PersistedInvocationRouting.of(Map.of());
        }
    }

    private ProcessExecutionResponse readResponseForRepresentation(String json, Map<String, String> payloadErrors) {
        try {
            return MAPPER.readValue(json, ProcessExecutionResponse.class);
        } catch (JacksonException failure) {
            payloadErrors.put("resultJson", new AsyncInvocationPayloadException("resultJson", failure).getMessage());
            return null;
        }
    }
}
