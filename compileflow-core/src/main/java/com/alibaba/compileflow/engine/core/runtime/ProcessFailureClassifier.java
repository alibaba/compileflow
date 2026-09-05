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
package com.alibaba.compileflow.engine.core.runtime;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessError;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Classifies raw failures into stable {@link CompileFlowException} / {@link ProcessError} forms.
 *
 * @author yusu
 */
public final class ProcessFailureClassifier {
    private ProcessFailureClassifier() {
    }

    public static CompileFlowException classify(Exception exception) {
        Objects.requireNonNull(exception, "exception");
        if (exception instanceof CompileFlowException classified) {
            return classified;
        }
        if (exception instanceof TimeoutException) {
            return new CompileFlowException(ErrorCode.CF_EXEC_004, ErrorCode.CF_EXEC_004.getMessage(), exception);
        }

        if (exception instanceof RejectedExecutionException) {
            return new CompileFlowException(ErrorCode.CF_EXEC_005, ErrorCode.CF_EXEC_005.getMessage(), exception);
        }

        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new CompileFlowException(ErrorCode.CF_EXEC_007, ErrorCode.CF_EXEC_007.getMessage(), exception);
        }

        return new CompileFlowException(ErrorCode.CF_EXEC_001, ErrorCode.CF_EXEC_001.getMessage(), exception);
    }

    /**
     * Converts an internal diagnostic exception into a caller-safe process error.
     *
     * <p>Internal exception messages may contain compiler diagnostics, resource locations, or
     * plugin details. The public result and event contracts expose only the stable error code and
     * its controlled default description.
     *
     * @param failure classified internal failure
     * @return caller-safe process error
     */
    public static ProcessError toProcessError(CompileFlowException failure) {
        ErrorCode errorCode = Objects.requireNonNull(failure, "failure").getErrorCode();
        if (failure instanceof ProcessCallDepthException) {
            return new ProcessError(errorCode.getCode(), failure.getMessage());
        }
        return new ProcessError(errorCode.getCode(), errorCode.getMessage());
    }
}
