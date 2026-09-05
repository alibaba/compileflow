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
 * Public lifecycle states of a persisted asynchronous invocation.
 *
 * @author yusu
 */
public enum AsyncInvocationStatus implements ApiEnumValue {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    DEAD_LETTER("dead_letter");
    private final String value;

    AsyncInvocationStatus(String value) {
        this.value = value;
    }

    @JsonCreator
    public static AsyncInvocationStatus fromValue(String value) {
        return ApiEnumValue.parse(values(), value, "async invocation status");
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
