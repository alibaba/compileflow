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
package com.alibaba.compileflow.workbench.server.api.problem;

import java.util.Objects;

/**
 * Preserves an unexpected failure's stack location without logging its message or cause chain.
 *
 * @author yusu
 */
public final class RedactedFailure {
    private RedactedFailure() {
    }

    /**
     * Creates a throwable that is safe to pass to a boundary logger.
     *
     * @param failure original unexpected failure
     * @return throwable containing only the original type and stack trace
     */
    public static RuntimeException forLogging(Throwable failure) {
        Throwable source = Objects.requireNonNull(failure, "failure");
        RuntimeException redacted = new RuntimeException("Original exception type: " + source.getClass().getName());
        redacted.setStackTrace(source.getStackTrace());
        return redacted;
    }
}
