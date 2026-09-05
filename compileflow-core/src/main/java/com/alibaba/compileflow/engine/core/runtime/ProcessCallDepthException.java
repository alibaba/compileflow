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
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.ArrayList;
import java.util.List;

/**
 * Caller-safe implementation failure raised before an excessive nested process invocation runs.
 *
 * <p>The message contains only process codes and numeric limits. Process
 * variables, routing keys, source content, and application exception messages
 * are never included. Callers observe its stable error code and bounded context through
 * {@link CompileFlowException}, not through this implementation type.
 *
 * @author yusu
 */
public final class ProcessCallDepthException extends CompileFlowException {
    private static final long serialVersionUID = 1L;
    /**
     * Private serializable snapshot of the root-to-called-Process code path.
     *
     * <p>The concrete type is intentional: this exception is serializable, while
     * the {@link List} interface does not itself declare that contract. The
     * mutable representation is never exposed.
     */
    private final ArrayList<String> processCallPath;
    /**
     * Configured maximum nested process-call depth.
     */
    private final int maxProcessCallDepth;

    /**
     * Creates a caller-safe process-call depth failure.
     *
     * @param processCallPath     root-to-called-Process code path, including the rejected call target
     * @param maxProcessCallDepth configured positive depth limit
     */
    public ProcessCallDepthException(List<String> processCallPath, int maxProcessCallDepth) {
        this(validatedPath(processCallPath), requirePositiveDepth(maxProcessCallDepth));
    }

    private ProcessCallDepthException(ArrayList<String> processCallPath, int maxProcessCallDepth) {
        super(ErrorCode.CF_EXEC_013, safeMessage(processCallPath, maxProcessCallDepth));
        this.processCallPath = processCallPath;
        this.maxProcessCallDepth = maxProcessCallDepth;
        withContext("processCallDepth", processCallPath.size());
        withContext("maxProcessCallDepth", maxProcessCallDepth);
        withContext("processCallPath", String.join(" -> ", processCallPath));
    }

    private static ArrayList<String> validatedPath(List<String> processCallPath) {
        if (processCallPath == null) {
            throw new NullPointerException("processCallPath");
        }
        ArrayList<String> path = new ArrayList<>(processCallPath);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("processCallPath must not be empty");
        }
        for (String processCode : path) {
            ProcessIdentifiers.requireCode(processCode);
        }
        return path;
    }

    private static int requirePositiveDepth(int maxProcessCallDepth) {
        if (maxProcessCallDepth < 1) {
            throw new IllegalArgumentException("maxProcessCallDepth must be positive");
        }
        return maxProcessCallDepth;
    }

    private static String safeMessage(List<String> processCallPath, int maxProcessCallDepth) {
        return "Process call depth " + processCallPath.size() + " exceeds configured maximum " + maxProcessCallDepth
                + ": " + String.join(" -> ", processCallPath);
    }

    /**
     * Returns the immutable process code path that exceeded the limit.
     *
     * @return root-to-called-Process code path
     */
    public List<String> getProcessCallPath() {
        return List.copyOf(processCallPath);
    }

    /**
     * Returns the configured maximum process-call depth.
     *
     * @return positive process-call depth limit
     */
    public int getMaxProcessCallDepth() {
        return maxProcessCallDepth;
    }
}
