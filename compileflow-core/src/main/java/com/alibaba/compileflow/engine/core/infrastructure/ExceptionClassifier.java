/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.infrastructure;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Exception classifier for CompileFlow framework.
 *
 * @author yusu
 */
public class ExceptionClassifier {

    /**
     * Converts any exception to CompileFlowException.
     */
    public static CompileFlowException classify(Exception exception) {
        if (exception instanceof CompileFlowException) {
            return (CompileFlowException) exception;
        }

        return classifyByType(exception);
    }

    private static CompileFlowException classifyByType(Exception exception) {
        String message = exception.getMessage();

        if (exception instanceof TimeoutException) {
            return new CompileFlowException.SystemException(
                    ErrorCode.CF_EXEC_004,
                    "Operation timeout: " + message,
                    exception
            );
        }

        if (exception instanceof RejectedExecutionException) {
            return new CompileFlowException.SystemException(
                    ErrorCode.CF_EXEC_005,
                    "Thread pool rejected execution: " + message,
                    exception
            );
        }

        if (exception instanceof InterruptedException) {
            return new CompileFlowException.SystemException(
                    ErrorCode.CF_EXEC_007,
                    "Operation interrupted: " + message,
                    exception
            );
        }

        // Fallback: unexpected error
        return new CompileFlowException.SystemException(
                ErrorCode.CF_EXEC_001,
                "Unexpected error: " + message,
                exception
        );
    }

}
