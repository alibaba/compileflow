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

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates configuration validation errors for engine config builders.
 *
 * @author yusu
 */
final class ValidationResult {
    private final List<String> errors;

    private ValidationResult(List<String> errors) {
        this.errors = List.copyOf(errors);
    }

    static ValidationResult success() {
        return new ValidationResult(List.of());
    }

    static ValidationResult failure(String error) {
        return new ValidationResult(List.of(error));
    }

    ValidationResult merge(ValidationResult other) {
        List<String> es = new ArrayList<>(this.errors);
        es.addAll(other.errors);
        return new ValidationResult(es);
    }

    ValidationResult addError(String error) {
        List<String> es = new ArrayList<>(this.errors);
        es.add(error);
        return new ValidationResult(es);
    }

    void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join("; ", errors));
        }
    }
}
