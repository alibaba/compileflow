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

import org.apache.commons.lang3.StringUtils;

/**
 * Execution log export filter.
 *
 * @param processCode           optional process code
 * @param status             optional execution status
 * @param keyword            optional search term
 * @param startTime          optional inclusive ISO-8601 start
 * @param endTime            optional inclusive ISO-8601 end
 * @param invocationId       optional invocation id
 * @param parentInvocationId optional direct parent invocation id
 * @param traceId            optional trace id
 * @param callDepth          optional zero-based process-call depth
 * @param namespace          optional runtime namespace
 * @param requestedVersion   optional requested version
 * @param effectiveVersion   optional effective version
 * @param routingSource      optional routing source
 * @param routeAlias         optional route alias
 * @param routeRevision      optional positive route revision
 * @author yusu
 */
public record ExecutionLogExportRequest(String processCode, String status, String keyword, String startTime,
        String endTime, String invocationId, String parentInvocationId, String traceId, Integer callDepth,
        String namespace, String requestedVersion, String effectiveVersion, String routingSource, String routeAlias,
        Long routeRevision) {
    public ExecutionLogExportRequest {
        processCode = StringUtils.trimToNull(processCode);
        status = StringUtils.trimToNull(status);
        keyword = StringUtils.trimToNull(keyword);
        startTime = StringUtils.trimToNull(startTime);
        endTime = StringUtils.trimToNull(endTime);
        invocationId = StringUtils.trimToNull(invocationId);
        parentInvocationId = StringUtils.trimToNull(parentInvocationId);
        traceId = StringUtils.trimToNull(traceId);
        if (callDepth != null && callDepth < 0) {
            throw new IllegalArgumentException("callDepth must not be negative");
        }
        namespace = StringUtils.trimToNull(namespace);
        requestedVersion = StringUtils.trimToNull(requestedVersion);
        effectiveVersion = StringUtils.trimToNull(effectiveVersion);
        routingSource = StringUtils.trimToNull(routingSource);
        routeAlias = StringUtils.trimToNull(routeAlias);
    }

    /**
     * Creates an empty export filter.
     *
     * @return empty filter
     */
    public static ExecutionLogExportRequest empty() {
        return new ExecutionLogExportRequest(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
