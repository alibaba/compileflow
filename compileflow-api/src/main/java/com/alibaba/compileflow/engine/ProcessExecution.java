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

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable facts about one completed process invocation.
 *
 * <p>This value provides caller-facing invocation attribution, not the complete semantic identity
 * of the executed definition. Alias routing inputs and source metadata end at their owning
 * admission, publication or runtime boundaries. An exact Version is retained only as execution
 * attribution.
 *
 * @author yusu
 */
public final class ProcessExecution {
    private final String traceId;
    private final String invocationId;
    private final String namespace;
    private final String processCode;
    private final ProcessRef.Version processVersion;
    private final Instant startedAt;
    private final Instant completedAt;

    private ProcessExecution(Builder builder) {
        this.traceId = ProcessIdentifiers.requireTraceId(builder.traceId);
        this.invocationId = ProcessIdentifiers.requireInvocationId(builder.invocationId);
        this.namespace = ProcessIdentifiers.requireNamespace(builder.namespace);
        this.processCode = ProcessIdentifiers.requireCode(builder.processCode);
        this.processVersion = validateProcessVersion(builder.processVersion, namespace, processCode);
        this.startedAt = Objects.requireNonNull(builder.startedAt, "startedAt");
        this.completedAt = Objects.requireNonNull(builder.completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static ProcessRef.Version validateProcessVersion(ProcessRef.Version version, String namespace,
            String processCode) {
        if (version != null && (!namespace.equals(version.namespace()) || !processCode.equals(version.code()))) {
            throw new IllegalArgumentException("processVersion must identify the executed process");
        }
        return version;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getInvocationId() {
        return invocationId;
    }

    /**
     * Returns the logical namespace of the process that ran.
     *
     * @return process namespace
     */
    public String getNamespace() {
        return namespace;
    }

    public String getProcessCode() {
        return processCode;
    }

    /**
     * Returns the exact Version that selected this invocation.
     *
     * @return exact version, or {@code null} for unversioned execution
     */
    public ProcessRef.Version getProcessVersion() {
        return processVersion;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public String toString() {
        return "ProcessExecution{traceId='" + traceId + "', invocationId='" + invocationId + "', namespace='"
                + namespace + "', processCode='" + processCode + "', processVersion=" + processVersion + ", startedAt="
                + startedAt + ", completedAt=" + completedAt + '}';
    }

    /**
     * Builds one immutable execution value.
     */
    public static final class Builder {
        private String traceId;
        private String invocationId;
        private String namespace;
        private String processCode;
        private ProcessRef.Version processVersion;
        private Instant startedAt;
        private Instant completedAt;

        private Builder() {
        }

        public Builder traceId(String value) {
            this.traceId = value;
            return this;
        }

        public Builder invocationId(String value) {
            this.invocationId = value;
            return this;
        }

        public Builder namespace(String value) {
            this.namespace = value;
            return this;
        }

        public Builder processCode(String value) {
            this.processCode = value;
            return this;
        }

        public Builder processVersion(ProcessRef.Version value) {
            this.processVersion = value;
            return this;
        }

        public Builder startedAt(Instant value) {
            this.startedAt = value;
            return this;
        }

        public Builder completedAt(Instant value) {
            this.completedAt = value;
            return this;
        }

        public ProcessExecution build() {
            return new ProcessExecution(this);
        }
    }
}
