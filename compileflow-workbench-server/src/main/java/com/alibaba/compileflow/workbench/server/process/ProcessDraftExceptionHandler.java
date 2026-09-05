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

import com.alibaba.compileflow.workbench.server.api.problem.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps process draft domain failures to stable HTTP error semantics.
 *
 * @author yusu
 */
@RestControllerAdvice(assignableTypes = ProcessController.class)
public final class ProcessDraftExceptionHandler {
    @ExceptionHandler(ProcessRevisionConflictException.class)
    ResponseEntity<ProblemDetail> handleRevisionConflict(ProcessRevisionConflictException failure) {
        return ApiProblems.response(HttpStatus.PRECONDITION_FAILED, "PROCESS_REVISION_CONFLICT",
                "Process revision conflict", failure.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrentMutation(ObjectOptimisticLockingFailureException failure) {
        return ApiProblems.response(HttpStatus.PRECONDITION_FAILED, "PROCESS_REVISION_CONFLICT",
                "Process revision conflict", "Process changed concurrently; reload it before retrying");
    }

    @ExceptionHandler(ProcessAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> handleIdentityConflict(ProcessAlreadyExistsException failure) {
        return ApiProblems.response(HttpStatus.CONFLICT, "PROCESS_ALREADY_EXISTS", "Process already exists",
                "Process code already exists");
    }
}
