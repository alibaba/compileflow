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
package com.alibaba.compileflow.engine.core.source;

import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.engine.ProcessModelType;
import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * Exact immutable UTF-8 process-definition snapshot used by digesting, parsing, and runtime loading.
 *
 * @author yusu
 */
public final class ProcessDefinitionSnapshot {
    private final ProcessModelType modelType;
    private final String namespace;
    private final String code;
    private final String version;
    private final byte[] bytes;
    private final String content;
    private final String sourceDigest;
    private final String sourceDescription;

    private ProcessDefinitionSnapshot(ProcessModelType modelType, String namespace, String code, String version,
            byte[] bytes, String content, String sourceDigest, String sourceDescription) {
        this.modelType = Objects.requireNonNull(modelType, "modelType");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.code = code;
        this.version = version;
        this.bytes = bytes;
        this.content = content;
        this.sourceDigest = sourceDigest;
        this.sourceDescription = sourceDescription;
    }

    /**
     * Creates an immutable snapshot from bounded bytes.
     *
     * @param modelType process definition format
     * @param namespace process namespace
     * @param code      process code
     * @param version   exact version, or {@code null}
     * @param bytes     exact bounded source bytes
     * @param sourceDescription safe source description
     * @return immutable process-definition snapshot
     */
    public static ProcessDefinitionSnapshot of(ProcessModelType modelType, String namespace, String code, String version,
            byte[] bytes, String sourceDescription) {
        String processCode = Objects.requireNonNull(code, "code");
        byte[] snapshot = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        String content = ProcessText.decodeUtf8(snapshot, "Process definition for code=" + processCode);
        return new ProcessDefinitionSnapshot(modelType, namespace, processCode, version, snapshot, content,
                DigestUtils.sha256Hex(snapshot), Objects.requireNonNull(sourceDescription, "sourceDescription"));
    }

    /**
     * Returns the process namespace.
     *
     * @return logical process namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the declared semantic format of the exact source.
     * @return process definition format
     */
    public ProcessModelType getModelType() {
        return modelType;
    }

    /**
     * Returns the process code.
     *
     * @return process code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the exact version binding.
     *
     * @return version, or {@code null}
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns a defensive copy of exact source bytes.
     *
     * @return exact UTF-8 source bytes
     */
    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Returns source content decoded strictly from the exact byte snapshot.
     *
     * @return process-definition content
     */
    public String getContent() {
        return content;
    }

    /**
     * Returns the lowercase SHA-256 digest of exact source bytes.
     *
     * @return source digest
     */
    public String getSourceDigest() {
        return sourceDigest;
    }

    /**
     * Returns a safe source description.
     *
     * @return source description without inline content or a full file path
     */
    public String getSourceDescription() {
        return sourceDescription;
    }

    @Override
    public String toString() {
        return "ProcessDefinitionSnapshot{modelType=" + modelType + ", namespace='" + namespace + "', code='" + code
                + "', version='" + version + "', sourceDigest='" + sourceDigest + "', sourceDescription='"
                + sourceDescription + "'}";
    }
}
