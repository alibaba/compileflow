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
package com.alibaba.compileflow.examples.order.api;

import com.alibaba.compileflow.engine.ProcessError;
import com.alibaba.compileflow.engine.ProcessExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts controlled process failures into a stable HTTP contract.
 *
 * @author yusu
 */
@RestControllerAdvice(assignableTypes = OrderFulfillmentController.class)
public class OrderFulfillmentExceptionHandler {
    @ExceptionHandler(ProcessExecutionException.class)
    public ResponseEntity<OrderErrorResponse> handleProcessFailure(ProcessExecutionException exception) {
        ProcessError error = exception.getError();
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_CONTENT)
            .body(new OrderErrorResponse(error.getCode(), error.getMessage()));
    }
}
