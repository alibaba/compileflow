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
package com.alibaba.compileflow.engine.core.runtime.execution;

/**
 * Defines the truth contract for generated workflow conditions.
 *
 * <p>Only Java {@code boolean} and {@link Boolean} expressions are accepted by
 * generated code. A nullable wrapper evaluates to {@code false}; this keeps
 * gateway selection, loops, and abrupt loop control consistent without
 * weakening javac's type checking to arbitrary objects.
 *
 * @author yusu
 */
public final class ConditionSemantics {
    private ConditionSemantics() {
    }

    public static boolean isTrue(boolean condition) {
        return condition;
    }

    public static boolean isTrue(Boolean condition) {
        return Boolean.TRUE.equals(condition);
    }
}
