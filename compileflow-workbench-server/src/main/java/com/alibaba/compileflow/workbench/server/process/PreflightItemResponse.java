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

import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * HTTP representation of one preflight stage result.
 *
 * @param type       preflight stage type
 * @param status     stage outcome
 * @param durationMs stage duration in milliseconds
 * @param message    optional human-readable stage detail
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PreflightItemResponse(@JsonProperty(required = true) ItemType type,
        @JsonProperty(required = true) ItemStatus status, @JsonProperty(required = true) long durationMs,
        String message) {
    public PreflightItemResponse {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        if (durationMs < 0L) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
    }

    static PreflightItemResponse from(ProcessPreflightReport.Item item) {
        Objects.requireNonNull(item, "item");
        return new PreflightItemResponse(switch (item.getType()) {
                    case LINT -> ItemType.LINT;
                    case COMPILE -> ItemType.COMPILE;
                },
                switch (item.getStatus()) {
                    case PASS -> ItemStatus.PASS;
                    case FAIL -> ItemStatus.FAIL;
                    case TIMEOUT -> ItemStatus.TIMEOUT;
                }, item.getDurationMs(), item.getMessage());
    }

    /**
     * Supported preflight stage types in the Workbench wire contract.
     */
    public enum ItemType {
        /**
         * Static process-definition linting.
         */
        LINT,
        /**
         * Generated Java compilation.
         */
        COMPILE
    }

    /**
     * Supported preflight stage outcomes in the Workbench wire contract.
     */
    public enum ItemStatus {
        /**
         * Stage completed successfully.
         */
        PASS,
        /**
         * Stage completed with a validation failure.
         */
        FAIL,
        /**
         * Stage exceeded its configured deadline.
         */
        TIMEOUT
    }
}
