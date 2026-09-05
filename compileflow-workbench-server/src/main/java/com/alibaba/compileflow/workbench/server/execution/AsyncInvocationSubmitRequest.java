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

import com.alibaba.compileflow.workbench.server.api.validation.RequestValidationException;
import com.alibaba.compileflow.workbench.server.api.validation.RequestValueParser;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Submission request for one persisted asynchronous invocation.
 *
 * @param invocationId optional idempotent invocation id
 * @param params       dynamic process variables
 * @param routing      persisted routing envelope
 * @param maxAttempts  optional positive attempt limit
 * @param retryDelayMs optional non-negative retry delay
 * @author yusu
 */
public record AsyncInvocationSubmitRequest(String invocationId, Map<String, Object> params,
        @NotNull ExecutionRoutingRequest routing, Integer maxAttempts, Long retryDelayMs) {
    public AsyncInvocationSubmitRequest {
        try {
            invocationId = RequestValueParser.optionalInvocationId(invocationId);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException(exception.getMessage(), exception);
        }
        params = RequestValueParser.immutableMap(params);
    }
}
