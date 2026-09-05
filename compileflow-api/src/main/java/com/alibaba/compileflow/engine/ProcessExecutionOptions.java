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
package com.alibaba.compileflow.engine;

import java.util.Objects;

/**
 * Immutable inputs for one root process invocation.
 *
 * <p>Alias routing is consumed only by {@link ProcessRef.Alias} admission and never becomes
 * process variables, execution metadata or recovery state.
 *
 * @author yusu
 */
public final class ProcessExecutionOptions {
    private static final ProcessExecutionOptions DEFAULTS = new Builder().build();
    private final String invocationId;
    private final AliasRoutingOptions aliasRouting;

    private ProcessExecutionOptions(Builder builder) {
        this.invocationId = ProcessIdentifiers.optionalInvocationId(builder.invocationId);
        this.aliasRouting = builder.aliasRouting;
    }

    public static ProcessExecutionOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getInvocationId() {
        return invocationId;
    }

    public AliasRoutingOptions getAliasRouting() {
        return aliasRouting;
    }

    /**
     * Builds immutable execution options.
     */
    public static final class Builder {
        private String invocationId;
        private AliasRoutingOptions aliasRouting = AliasRoutingOptions.defaults();

        private Builder() {
        }

        public Builder invocationId(String value) {
            this.invocationId = value;
            return this;
        }

        public Builder aliasRouting(AliasRoutingOptions options) {
            this.aliasRouting = Objects.requireNonNull(options, "aliasRouting");
            return this;
        }

        public ProcessExecutionOptions build() {
            return new ProcessExecutionOptions(this);
        }
    }
}
