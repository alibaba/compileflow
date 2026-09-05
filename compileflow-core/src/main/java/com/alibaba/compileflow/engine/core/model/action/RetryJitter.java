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
 * Randomization strategy applied to a computed retry backoff delay.
 *
 * @author yusu
 */
public enum RetryJitter {
    /**
     * Sleep for the full computed backoff delay.
     */
    NONE("none"),
    /**
     * Select a uniformly distributed delay from zero through the backoff cap.
     */
    FULL("full");
    private final String value;

    RetryJitter(String value) {
        this.value = value;
    }

    public static RetryJitter from(String value) {
        if (value == null) {
            return FULL;
        }
        for (RetryJitter jitter : values()) {
            if (jitter.value.equals(value)) {
                return jitter;
            }
        }
        throw new IllegalArgumentException("jitter must be one of [none, full], got: " + value);
    }

    public String getValue() {
        return value;
    }
}
