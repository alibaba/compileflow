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
package com.alibaba.compileflow.deploy.protocol;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Jackson wire DTO for immutable process-artifact payloads.
 *
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
final class ProcessArtifactPayload {
    private final int schemaVersion = 1;
    private final String namespace;
    private final String code;
    private final String version;
    private final ProcessModelType modelType;
    private final String content;
    private final String artifactDigest;
    private final List<ProcessCallBindingPayload> callBindings;

    ProcessArtifactPayload(String namespace, String code, String version, ProcessModelType modelType, String content,
            String artifactDigest, List<ProcessCallBindingPayload> callBindings) {
        this.namespace = namespace;
        this.code = code;
        this.version = version;
        this.modelType = modelType;
        this.content = content;
        this.artifactDigest = artifactDigest;
        this.callBindings = callBindings;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getCode() {
        return code;
    }

    public String getVersion() {
        return version;
    }

    public ProcessModelType getModelType() {
        return modelType;
    }

    public String getContent() {
        return content;
    }

    public String getArtifactDigest() {
        return artifactDigest;
    }

    public List<ProcessCallBindingPayload> getCallBindings() {
        return callBindings;
    }
}
