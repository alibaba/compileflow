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
import jakarta.validation.constraints.NotBlank;

/**
 * Request for one bounded execution-log purge.
 *
 * @param before exclusive ISO-8601 cutoff
 * @author yusu
 */
public record PurgeExecutionLogsRequest(@NotBlank String before) {
    public PurgeExecutionLogsRequest {
        before = StringUtils.trimToNull(before);
    }
}
