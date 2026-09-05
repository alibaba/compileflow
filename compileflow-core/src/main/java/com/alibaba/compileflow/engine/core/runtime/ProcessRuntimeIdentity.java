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
package com.alibaba.compileflow.engine.core.runtime;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import java.util.Objects;

/**
 * Exact identity of a locally executable process runtime.
 *
 * <p>This key is deliberately local to one engine. It is not a portable artifact identifier and
 * must never be persisted or transported between nodes.
 *
 * @author yusu
 */
public final class ProcessRuntimeIdentity {
    private final ProcessModelType modelType;
    private final String code;
    private final String sourceDigest;
    private final PipelineIdentity pipelineIdentity;
    private final ClassLoader classLoader;
    private final String digest;
    private final int hashCode;

    private ProcessRuntimeIdentity(ProcessDefinitionSnapshot definition, ProcessModelType modelType,
            PipelineIdentity pipelineIdentity, ClassLoader classLoader) {
        ProcessDefinitionSnapshot source = Objects.requireNonNull(definition, "definition");
        this.modelType = Objects.requireNonNull(modelType, "modelType");
        this.code = source.getCode();
        this.sourceDigest = source.getSourceDigest();
        this.pipelineIdentity = Objects.requireNonNull(pipelineIdentity, "pipelineIdentity");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.digest = diagnosticDigest();
        this.hashCode = computeHashCode();
    }

    /**
     * Creates a local runtime identity from exact runtime-construction inputs.
     *
     * @param definition             exact source snapshot
     * @param modelType              engine model type
     * @param pipelineIdentity identity of the immutable engine runtime-construction pipeline
     * @param classLoader            exact application class-loader scope
     * @return local runtime identity
     */
    public static ProcessRuntimeIdentity of(ProcessDefinitionSnapshot definition, ProcessModelType modelType,
            PipelineIdentity pipelineIdentity, ClassLoader classLoader) {
        return new ProcessRuntimeIdentity(definition, modelType, pipelineIdentity, classLoader);
    }

    /**
     * Creates an opaque identity for one immutable engine runtime-construction pipeline.
     *
     * <p>Each engine owns its runtime cache, so identity is deliberately stronger than a derived
     * string that could omit a plugin's internal state. The value is local-only and nonportable.
     *
     * @return new engine-local pipeline identity
     */
    public static PipelineIdentity newPipelineIdentity() {
        return new PipelineIdentity();
    }

    private static String describeIdentity(Object value) {
        return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    public String getDigest() {
        return digest;
    }

    public ProcessModelType getModelType() {
        return modelType;
    }

    public String getCode() {
        return code;
    }

    public String getSourceDigest() {
        return sourceDigest;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    private String diagnosticDigest() {
        return "FMT:" + modelType + "@COD:" + code + "@SHA256:"
                + sourceDigest.substring(0, Math.min(12, sourceDigest.length())) + "@PIPELINE:"
                + describeIdentity(pipelineIdentity) + "@CL:" + describeIdentity(classLoader);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProcessRuntimeIdentity that)) {
            return false;
        }
        return modelType == that.modelType && pipelineIdentity == that.pipelineIdentity
                && classLoader == that.classLoader && code.equals(that.code) && sourceDigest.equals(that.sourceDigest);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int computeHashCode() {
        int result = Objects.hash(modelType, code, sourceDigest);
        result = 31 * result + System.identityHashCode(pipelineIdentity);
        return 31 * result + System.identityHashCode(classLoader);
    }

    @Override
    public String toString() {
        return digest;
    }

    /**
     * Opaque engine-local identity for the complete immutable runtime-construction pipeline.
     */
    public static final class PipelineIdentity {
        private PipelineIdentity() {
        }
    }
}
