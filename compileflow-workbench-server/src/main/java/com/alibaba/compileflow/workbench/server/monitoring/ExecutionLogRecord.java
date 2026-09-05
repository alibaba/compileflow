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
package com.alibaba.compileflow.workbench.server.monitoring;

import com.alibaba.compileflow.engine.ProcessModelType;

/**
 * Immutable terminal execution observation persisted for operational queries.
 *
 * @param processCode        executed process code
 * @param status             terminal execution status
 * @param durationMs         execution duration in milliseconds
 * @param errorCode          stable failure code, or {@code null}
 * @param errorMessage       safe failure message, or {@code null}
 * @param loggedAt           completion timestamp in epoch milliseconds
 * @param invocationId       invocation identifier
 * @param parentInvocationId direct synchronous caller invocation, or {@code null}
 * @param callDepth          zero-based synchronous process-call depth
 * @param traceId            correlated trace identifier
 * @param namespace          process namespace
 * @param modelType          process definition format
 * @param sourceDigest       exact source SHA-256 digest, or {@code null}
 * @param requestedVersion   explicitly requested version, or {@code null}
 * @param effectiveVersion   executed version, or {@code null}
 * @param routingSource      routing source, or {@code null}
 * @param routeAlias         effective alias, or {@code null}
 * @param routeRevision      effective alias revision, or {@code null}
 * @author yusu
 */
public record ExecutionLogRecord(String processCode, String status, long durationMs, String errorCode,
        String errorMessage, long loggedAt, String invocationId, String parentInvocationId, int callDepth,
        String traceId, String namespace, ProcessModelType modelType, String sourceDigest, String requestedVersion,
        String effectiveVersion, String routingSource, String routeAlias, Long routeRevision) {}
