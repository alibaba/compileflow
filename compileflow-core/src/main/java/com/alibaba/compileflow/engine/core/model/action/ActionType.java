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
 * Supported action implementation types.
 *
 * @author yusu
 */
public enum ActionType {
    JAVA("java"),
    SPRING_BEAN("spring-bean"),
    SCRIPT("script");
    private final String value;

    ActionType(String value) {
        this.value = value;
    }

    public static ActionType of(String value) {
        for (ActionType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported Action type '" + value + "'");
    }

    public String getValue() {
        return value;
    }
}
