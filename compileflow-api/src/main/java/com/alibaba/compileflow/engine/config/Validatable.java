/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

/**
 * An interface for configuration objects that support validation.
 * Implementations should provide logic to check their own consistency and correctness.
 *
 * @author yusu
 */
public interface Validatable {

    /**
     * Validates this configuration object.
     *
     * @return a {@link ValidationResult} containing any errors or warnings found during validation.
     */
    ValidationResult validate();

    /**
     * Validates this configuration and throws an {@link IllegalArgumentException} if the configuration is invalid.
     * Any warnings discovered during validation will be logged.
     *
     * @throws IllegalArgumentException if validation fails.
     */
    default void validateAndThrow() {
        validate().throwIfInvalid();
    }

}
