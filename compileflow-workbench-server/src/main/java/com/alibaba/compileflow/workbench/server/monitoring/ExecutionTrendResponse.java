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
import java.util.Objects;

/**
 * One execution monitoring time bucket.
 *
 * @param time       bucket start timestamp
 * @param executions total executions
 * @param success    successful executions
 * @param failed     failed executions
 * @author yusu
 */
public record ExecutionTrendResponse(@JsonProperty(required = true) String time,
        @JsonProperty(required = true) long executions, @JsonProperty(required = true) long success,
        @JsonProperty(required = true) long failed) {
    public ExecutionTrendResponse {
        Objects.requireNonNull(time, "time");
    }
}
