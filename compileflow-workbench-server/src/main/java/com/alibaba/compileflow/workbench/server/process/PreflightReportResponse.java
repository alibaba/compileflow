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
import java.util.List;
import java.util.Objects;

/**
 * Stable HTTP representation of a process preflight report.
 *
 * @param code            process code
 * @param overallStatus   aggregate preflight outcome
 * @param totalDurationMs total preflight duration in milliseconds
 * @param items           immutable per-stage results
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PreflightReportResponse(@JsonProperty(required = true) String code,
        @JsonProperty(required = true) OverallStatus overallStatus, @JsonProperty(required = true) long totalDurationMs,
        @JsonProperty(required = true) List<PreflightItemResponse> items) {
    public PreflightReportResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(overallStatus, "overallStatus");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (totalDurationMs < 0L) {
            throw new IllegalArgumentException("totalDurationMs must not be negative");
        }
    }

    /**
     * Maps an engine-owned preflight result to the stable HTTP contract.
     *
     * @param report engine preflight report
     * @return Workbench transport response
     */
    public static PreflightReportResponse from(ProcessPreflightReport report) {
        Objects.requireNonNull(report, "report");
        return new PreflightReportResponse(report.getCode(),
                switch (report.getOverallStatus()) {
                    case PASS -> OverallStatus.PASS;
                    case FAIL -> OverallStatus.FAIL;
                }, report.getTotalDurationMs(), report.getItems().stream().map(PreflightItemResponse::from).toList());
    }

    /**
     * Aggregate preflight outcomes in the Workbench wire contract.
     */
    public enum OverallStatus {
        /**
         * Every required preflight stage passed.
         */
        PASS,
        /**
         * At least one required preflight stage failed.
         */
        FAIL
    }
}
