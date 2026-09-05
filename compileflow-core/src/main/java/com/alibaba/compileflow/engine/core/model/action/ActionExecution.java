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
package com.alibaba.compileflow.engine.core.model.action;

/**
 * Declares the replay and side-effect contract of an {@link Action}.
 *
 * <p>This is deliberately an Action property rather than an implementation
 * property. The same bean, method, or script can be used with either semantic
 * in different process definitions. ProcessEngine runtimes execute both values locally;
 * DurableProcessEngine runtimes lower Effects to their recoverable Effect protocol.
 *
 * @author yusu
 */
public enum ActionExecution {
    REPLAYABLE("replayable"),
    EFFECT("effect");
    private final String value;

    ActionExecution(String value) {
        this.value = value;
    }

    /**
     * Parses an explicitly declared execution value.
     *
     * @param value model value; {@code null} means unspecified
     * @return the parsed value, or {@code null} when unspecified
     */
    public static ActionExecution of(String value) {
        if (value == null) {
            return null;
        }
        for (ActionExecution candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Action execution must be one of: replayable, effect");
    }

    public String getValue() {
        return value;
    }
}
