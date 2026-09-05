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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/**
 * One page of immutable published process versions.
 *
 * @param data     immutable version page
 * @param nextCursor opaque continuation cursor, or {@code null}
 * @param hasMore  whether another page exists
 * @author yusu
 */
public record ProcessVersionListResponse(@JsonProperty(required = true) List<ProcessVersionResponse> data,
        @JsonProperty(required = true) @Schema(nullable = true) String nextCursor,
        @JsonProperty(required = true) boolean hasMore) {
    public ProcessVersionListResponse {
        data = List.copyOf(Objects.requireNonNull(data, "data"));
        if (hasMore != (nextCursor != null)) {
            throw new IllegalArgumentException("hasMore must match nextCursor presence");
        }
    }
}
