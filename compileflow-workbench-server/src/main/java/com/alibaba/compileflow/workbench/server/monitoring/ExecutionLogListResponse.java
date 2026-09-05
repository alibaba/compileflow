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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * One page of persisted execution logs.
 *
 * @param data     immutable execution log page
 * @param total    total matching logs
 * @param page     one-based page number
 * @param pageSize effective page size
 * @author yusu
 */
public record ExecutionLogListResponse(@JsonProperty(required = true) List<ExecutionLogResponse> data,
        @JsonProperty(required = true) long total, @JsonProperty(required = true) int page,
        @JsonProperty(required = true) int pageSize) {
    public ExecutionLogListResponse {
        data = List.copyOf(Objects.requireNonNull(data, "data"));
    }
}
