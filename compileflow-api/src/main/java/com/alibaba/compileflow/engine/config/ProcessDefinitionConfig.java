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

/**
 * Immutable size limit for loading process definitions.
 *
 * @author yusu
 */
public final class ProcessDefinitionConfig {
    private static final int DEFAULT_MAX_BYTES = 4 * 1024 * 1024;
    /**
     * Hard safety ceiling for one process definition: 100 MiB.
     */
    public static final int MAX_BYTES = 100 * 1024 * 1024;
    private final int maxBytes;

    private ProcessDefinitionConfig(Builder builder) {
        this.maxBytes = builder.maxBytes;
    }

    /**
     * Creates a builder initialized with secure production defaults.
     *
     * @return definition configuration builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns secure production defaults.
     *
     * @return default definition configuration
     */
    public static ProcessDefinitionConfig defaults() {
        return builder().build();
    }

    /**
     * Creates a builder initialized from this immutable snapshot.
     *
     * @return mutable builder carrying all current values
     */
    public Builder toBuilder() {
        return new Builder().maxBytes(maxBytes);
    }

    /**
     * Returns the maximum UTF-8 or binary size accepted for one process definition.
     *
     * @return positive maximum definition size in bytes
     */
    public int getMaxBytes() {
        return maxBytes;
    }

    ValidationResult validate() {
        ValidationResult result = ValidationResult.success();
        if (maxBytes <= 0 || maxBytes > MAX_BYTES) {
            result = result.addError("definition.maxBytes must be between 1 and " + MAX_BYTES);
        }
        return result;
    }

    @Override
    public String toString() {
        return "ProcessDefinitionConfig{maxBytes=" + maxBytes + '}';
    }

    /**
     * Builder for {@link ProcessDefinitionConfig}.
     */
    public static final class Builder {
        private int maxBytes = DEFAULT_MAX_BYTES;

        private Builder() {
        }

        /**
         * Sets the maximum accepted size of one process definition.
         *
         * @param value maximum size in bytes, from 1 through
         *              {@link ProcessDefinitionConfig#MAX_BYTES}
         * @return this builder
         */
        public Builder maxBytes(int value) {
            this.maxBytes = value;
            return this;
        }

        /**
         * Builds and validates the immutable definition configuration.
         *
         * @return validated definition configuration
         */
        public ProcessDefinitionConfig build() {
            ProcessDefinitionConfig config = new ProcessDefinitionConfig(this);
            config.validate().throwIfInvalid();
            return config;
        }
    }
}
