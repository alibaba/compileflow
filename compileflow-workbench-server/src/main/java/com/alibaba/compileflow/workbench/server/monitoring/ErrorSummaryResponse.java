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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Aggregate execution failure group.
 *
 * @param processCode         process code
 * @param errorCode        stable error code
 * @param errorMessage     redacted error message
 * @param namespace        effective namespace
 * @param requestedVersion explicitly requested version
 * @param effectiveVersion version actually executed
 * @param routingSource    routing source
 * @param routeAlias       Alias route
 * @param routeRevision    authoritative alias revision
 * @param count            occurrence count
 * @param lastOccurred     latest occurrence timestamp
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorSummaryResponse(@JsonProperty(required = true) String processCode,
        @JsonProperty(required = true) String errorCode, @JsonProperty(required = true) String errorMessage,
        String namespace, String requestedVersion, String effectiveVersion, String routingSource, String routeAlias,
        Long routeRevision, @JsonProperty(required = true) long count,
        @JsonProperty(required = true) String lastOccurred) {
    public ErrorSummaryResponse {
        Objects.requireNonNull(processCode, "processCode");
        Objects.requireNonNull(errorCode, "errorCode");
        Objects.requireNonNull(errorMessage, "errorMessage");
        Objects.requireNonNull(lastOccurred, "lastOccurred");
    }
}
