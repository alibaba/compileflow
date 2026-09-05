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
package com.alibaba.compileflow.durable.runtime.machine;

import java.util.List;
import java.util.Map;

/**
 * Compile-toolchain validator for one typed Process control expression.
 *
 * @author yusu
 */
@FunctionalInterface
interface DurableExpressionValidator {
    List<String> validate(String expression, Map<String, String> visibleTypes, TargetType targetType);

    enum TargetType {
        BOOLEAN("boolean"),
        DURATION("java.time.Duration"),
        INSTANT("java.time.Instant");
        private final String javaType;

        TargetType(String javaType) {
            this.javaType = javaType;
        }

        String javaType() {
            return javaType;
        }
    }
}
