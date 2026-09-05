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
package com.alibaba.compileflow.workbench.server.execution;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Public outcomes of one physical asynchronous invocation attempt.
 *
 * @author yusu
 */
public enum AsyncInvocationAttemptOutcome implements ApiEnumValue {
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    LEASE_EXPIRED("lease_expired");
    private final String value;

    AsyncInvocationAttemptOutcome(String value) {
        this.value = value;
    }

    @JsonCreator
    public static AsyncInvocationAttemptOutcome fromValue(String value) {
        return ApiEnumValue.parse(values(), value, "async attempt outcome");
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
