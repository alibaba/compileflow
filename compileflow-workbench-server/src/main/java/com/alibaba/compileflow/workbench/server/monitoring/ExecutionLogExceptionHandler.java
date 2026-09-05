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

import com.alibaba.compileflow.workbench.server.api.problem.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps bounded execution-log query failures to a stable HTTP response.
 *
 * @author yusu
 */
@RestControllerAdvice
public final class ExecutionLogExceptionHandler {
    @ExceptionHandler(ExecutionLogQueryLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleQueryLimit(ExecutionLogQueryLimitExceededException failure) {
        ProblemDetail problem = ApiProblems.create(HttpStatus.UNPROCESSABLE_CONTENT,
                "EXECUTION_LOG_QUERY_LIMIT_EXCEEDED", "Execution log query limit exceeded", failure.getMessage());
        problem.setProperty("maxRows", failure.getMaxRows());
        return ResponseEntity.unprocessableContent().body(problem);
    }
}
