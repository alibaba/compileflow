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
import java.util.Objects;

/**
 * Immutable published process version metadata.
 *
 * @param processCode     process code
 * @param version      opaque immutable version
 * @param modelType    process model format
 * @param createdAt    publication timestamp
 * @param changelog    optional release note
 * @param publishedBy  publishing principal
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcessVersionResponse(@JsonProperty(required = true) String processCode,
        @JsonProperty(required = true) String version, @JsonProperty(required = true) ProcessModelType modelType,
        @JsonProperty(required = true) String createdAt, String changelog,
        @JsonProperty(required = true) String publishedBy) {
    public ProcessVersionResponse {
        Objects.requireNonNull(processCode, "processCode");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(modelType, "modelType");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(publishedBy, "publishedBy");
    }
}
