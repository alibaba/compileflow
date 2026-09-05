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
package com.alibaba.compileflow.engine.core.semantic;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessText;

/**
 * Exact validation shared by source-neutral semantic values.
 *
 * @author yusu
 */
public final class SemanticText {
    private SemanticText() {
    }

    public static String requireIdentity(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return ProcessIdentifiers.requireExactIdentity(value, name, Integer.MAX_VALUE);
    }

    public static String optionalIdentity(String value, String name) {
        return value == null ? null : requireIdentity(value, name);
    }

    public static String requireExpression(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return ProcessText.requireNonBlank(value, name);
    }

    public static String optionalExpression(String value, String name) {
        return value == null ? null : requireExpression(value, name);
    }
}
