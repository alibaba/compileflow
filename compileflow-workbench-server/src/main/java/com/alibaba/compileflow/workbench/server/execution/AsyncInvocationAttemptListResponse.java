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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Bounded cursor page of physical attempts for one logical invocation.
 *
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsyncInvocationAttemptListResponse(
        @JsonProperty(required = true) List<AsyncInvocationAttemptResponse> data,
        @JsonProperty(required = true) boolean hasMore, Long nextAfterSequence) {
    public AsyncInvocationAttemptListResponse {
        data = List.copyOf(Objects.requireNonNull(data, "data"));
        if (hasMore && nextAfterSequence == null) {
            throw new IllegalArgumentException("hasMore requires nextAfterSequence");
        }
        if (!hasMore && nextAfterSequence != null) {
            throw new IllegalArgumentException("nextAfterSequence requires hasMore");
        }
    }
}
