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

/**
 * Indicates that a complete execution-log query exceeds the configured in-memory row limit.
 *
 * @author yusu
 */
public final class ExecutionLogQueryLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int maxRows;

    public ExecutionLogQueryLimitExceededException(int maxRows) {
        super(
                "Execution-log query exceeds the configured limit of " + maxRows
                + " rows; narrow the filters or increase compileflow.workbench.server.execution-log.max-query-rows");
        this.maxRows = maxRows;
    }

    public int getMaxRows() {
        return maxRows;
    }
}
