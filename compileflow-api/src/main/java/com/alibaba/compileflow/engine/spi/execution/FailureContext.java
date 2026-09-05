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
package com.alibaba.compileflow.engine.spi.execution;

import java.util.Objects;

/**
 * Immutable terminal action-failure details passed to a {@link FailureHandler}.
 *
 * @author yusu
 */
public final class FailureContext {
    private final ActionExecutionContext actionExecution;
    private final Throwable cause;

    /**
     * Creates a failure context.
     *
     * @param actionExecution terminal action-attempt context
     * @param cause           non-null terminal action failure
     */
    public FailureContext(ActionExecutionContext actionExecution, Throwable cause) {
        this.actionExecution = Objects.requireNonNull(actionExecution, "actionExecution");
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    /**
     * Returns the terminal action-attempt context.
     *
     * @return action execution context
     */
    public ActionExecutionContext getActionExecution() {
        return actionExecution;
    }

    /**
     * Returns the failed process node identifier.
     *
     * @return process node identifier
     */
    public String getNodeId() {
        return actionExecution.getNodeId();
    }

    /**
     * Returns the stable logical action invocation key.
     *
     * @return stable invocation key
     */
    public String getInvocationKey() {
        return actionExecution.getInvocationKey();
    }

    /**
     * Returns the terminal action failure.
     *
     * @return terminal failure
     */
    public Throwable getCause() {
        return cause;
    }

    /**
     * Returns the total number of action attempts that were made.
     *
     * @return positive attempt count
     */
    public int getAttemptCount() {
        return actionExecution.getAttemptNumber();
    }

    @Override
    public String toString() {
        return "FailureContext{nodeId='" + getNodeId() + "', invocationKey='" + getInvocationKey() + "', attemptCount="
                + getAttemptCount() + ", causeType=" + cause.getClass().getName() + "}";
    }
}
