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
package com.alibaba.compileflow.deploy.api.rollout;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque continuation cursor for rollout-history traversal.
 *
 * @param value opaque cursor value
 *
 * @author yusu
 */
public record RolloutCursor(String value) {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]+");

    /**
     * Creates a bounded opaque cursor value.
     */
    public RolloutCursor {
        value = Objects.requireNonNull(value, "value");
        if (value.length() > 1024 || !TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("value must be a Base64 URL token of at most 1024 characters");
        }
    }
}
