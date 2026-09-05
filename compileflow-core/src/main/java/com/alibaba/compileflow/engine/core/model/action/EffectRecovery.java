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
 * Closed recovery strategy for an Effect occurrence whose outcome becomes unknown.
 *
 * @author yusu
 */
public enum EffectRecovery {
    MANUAL("manual"),
    RETRY("retry"),
    RECONCILE("reconcile");
    private final String value;

    EffectRecovery(String value) {
        this.value = value;
    }

    public static EffectRecovery of(String value) {
        if (value == null) {
            return MANUAL;
        }
        for (EffectRecovery candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported Effect recovery: " + value);
    }

    public String getValue() {
        return value;
    }
}
