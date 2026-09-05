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
package com.alibaba.compileflow.engine.preflight;

import java.time.Duration;
import java.util.Objects;

/**
 * Options controlling preflight validation before process deployment or warm-up.
 *
 * <p>Preflight can run lightweight structural linting, compilation checks, or both.
 *
 * @author yusu
 */
public final class ProcessPreflightOptions {
    /**
     * Whether structural linting is enabled.
     */
    private final boolean lintEnabled;
    /**
     * Whether generated-code compilation is enabled.
     */
    private final boolean compileEnabled;
    /**
     * Maximum duration allowed for one preflight operation.
     */
    private final Duration timeout;

    private ProcessPreflightOptions(Builder builder) {
        this.lintEnabled = builder.lintEnabled;
        this.compileEnabled = builder.compileEnabled;
        this.timeout = builder.timeout;
    }

    /**
     * Creates a builder with strict defaults.
     *
     * @return new preflight options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates strict preflight options that lint and compile definitions.
     *
     * @return strict preflight options
     */
    public static ProcessPreflightOptions strict() {
        return builder().build();
    }

    /**
     * Creates fast preflight options that lint definitions without compilation.
     *
     * @return fast preflight options
     */
    public static ProcessPreflightOptions fast() {
        return builder().compileEnabled(false).build();
    }

    /**
     * Returns whether structural lint checks are enabled.
     *
     * @return {@code true} when lint checks should run
     */
    public boolean isLintEnabled() {
        return lintEnabled;
    }

    /**
     * Returns whether compilation checks are enabled.
     *
     * @return {@code true} when preflight should compile the process definition
     */
    public boolean isCompileEnabled() {
        return compileEnabled;
    }

    /**
     * Returns the maximum duration allowed for preflight.
     *
     * @return positive whole-millisecond timeout
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Builder for immutable {@link ProcessPreflightOptions}.
     */
    public static final class Builder {
        private boolean lintEnabled = true;
        private boolean compileEnabled = true;
        private Duration timeout = Duration.ofMinutes(1);

        private Builder() {
        }

        private static boolean isPositiveWholeMilliseconds(Duration value) {
            if (value.isZero() || value.isNegative()) {
                return false;
            }
            try {
                return value.equals(Duration.ofMillis(value.toMillis()));
            } catch (ArithmeticException overflow) {
                return false;
            }
        }

        /**
         * Enables or disables structural lint checks.
         *
         * @param enabled {@code true} to run lint checks
         * @return this builder
         */
        public Builder lintEnabled(boolean enabled) {
            this.lintEnabled = enabled;
            return this;
        }

        /**
         * Enables or disables compilation checks.
         *
         * @param enabled {@code true} to compile during preflight
         * @return this builder
         */
        public Builder compileEnabled(boolean enabled) {
            this.compileEnabled = enabled;
            return this;
        }

        /**
         * Sets the timeout for preflight.
         *
         * @param value positive whole-millisecond timeout
         * @return this builder
         */
        public Builder timeout(Duration value) {
            this.timeout = Objects.requireNonNull(value, "timeout");
            return this;
        }

        /**
         * Builds immutable preflight options.
         *
         * @return preflight options
         */
        public ProcessPreflightOptions build() {
            if (!lintEnabled && !compileEnabled) {
                throw new IllegalArgumentException("At least one preflight stage must be enabled");
            }
            if (!isPositiveWholeMilliseconds(timeout)) {
                throw new IllegalArgumentException("timeout must be a positive whole-millisecond duration");
            }
            return new ProcessPreflightOptions(this);
        }
    }
}
