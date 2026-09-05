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
import java.util.Map;

/**
 * Synchronous process execution request.
 *
 * @param invocationId optional caller-supplied correlation id
 * @param params       dynamic process variables
 * @param routing      fixed process routing envelope
 * @author yusu
 */
public record ProcessExecutionRequest(String invocationId, Map<String, Object> params, ExecutionRoutingRequest routing) {
    public ProcessExecutionRequest {
        try {
            invocationId = RequestValueParser.optionalInvocationId(invocationId);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException(exception.getMessage(), exception);
        }
        params = RequestValueParser.immutableMap(params);
    }

    /**
     * Creates an empty request for an omitted JSON body.
     *
     * @return request with no variables or routing
     */
    public static ProcessExecutionRequest empty() {
        return new ProcessExecutionRequest(null, Map.of(), null);
    }
}
