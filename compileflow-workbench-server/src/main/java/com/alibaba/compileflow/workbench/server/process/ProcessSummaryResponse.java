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
package com.alibaba.compileflow.workbench.server.process;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Process summary metadata returned by list endpoints.
 *
 * @param code        process code
 * @param name        display name
 * @param type        process model format
 * @param createdAt   creation timestamp
 * @param updatedAt   last update timestamp
 * @param revision    draft optimistic-lock revision
 * @param description optional description
 * @param tags        immutable classification tags
 * @param createdBy   creating principal
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcessSummaryResponse(@JsonProperty(required = true) String code,
        @JsonProperty(required = true) String name, @JsonProperty(required = true) ProcessModelType type,
        @JsonProperty(required = true) String createdAt, @JsonProperty(required = true) String updatedAt,
        @JsonProperty(required = true) long revision, String description,
        @JsonProperty(required = true) List<String> tags, @JsonProperty(required = true) String createdBy) {
    public ProcessSummaryResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        Objects.requireNonNull(createdBy, "createdBy");
    }
}
