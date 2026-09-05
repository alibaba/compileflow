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

import java.util.Arrays;

/**
 * Shared parsing contract for API enums whose wire value differs from the Java constant name.
 *
 * @author yusu
 */
interface ApiEnumValue {
    static <E extends Enum<E> & ApiEnumValue> E parse(E[] values, String value, String description) {
        return Arrays
            .stream(values)
            .filter(candidate -> candidate.value().equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown " + description + ": " + value));
    }

    String value();
}
