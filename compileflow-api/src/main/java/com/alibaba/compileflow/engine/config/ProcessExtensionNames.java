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
package com.alibaba.compileflow.engine.config;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Canonical validation for named execution-policy extension capabilities.
 *
 * @author yusu
 */
final class ProcessExtensionNames {
    static final int MAX_LENGTH = 256;
    private static final Set<String> BUILT_IN_RETRY_POLICIES = Set.of("never", "transient", "always");
    private static final Set<String> BUILT_IN_FAILURE_HANDLERS = Set.of("propagate", "continue");
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

    private ProcessExtensionNames() {
    }

    static String retryPolicy(String name) {
        return requireAvailableName(name, "retry policy", BUILT_IN_RETRY_POLICIES);
    }

    static String failureHandler(String name) {
        return requireAvailableName(name, "failure handler", BUILT_IN_FAILURE_HANDLERS);
    }

    private static String requireAvailableName(String name, String capability, Set<String> reservedNames) {
        String exact = Objects.requireNonNull(name, capability + " name");
        if (exact.isBlank()) {
            throw new IllegalArgumentException(capability + " name must not be blank");
        }
        if (exact.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(capability + " name must not exceed " + MAX_LENGTH + " characters");
        }
        if (!NAME_PATTERN.matcher(exact).matches()) {
            throw new IllegalArgumentException(capability + " name must use lowercase kebab-case: " + exact);
        }
        if (reservedNames.contains(exact)) {
            throw new IllegalArgumentException(capability + " name is reserved by CompileFlow: " + exact);
        }
        return exact;
    }
}
