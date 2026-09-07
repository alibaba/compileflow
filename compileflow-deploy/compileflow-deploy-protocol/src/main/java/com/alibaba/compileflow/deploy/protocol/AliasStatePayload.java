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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Jackson wire DTO for alias-routing state payloads.
 *
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
final class AliasStatePayload {
    static final String KIND = "aliasState";
    private final int schemaVersion = 1;
    private final String kind = KIND;
    private final String namespace;
    private final String code;
    private final String alias;
    private final String stableVersion;
    private final String candidateVersion;
    private final Integer candidateWeightBps;
    private final String targetingPolicy;
    private final Map<String, String> targetingParameters;
    private final Boolean deleted;
    private final long revision;
    private final String actor;
    private final long updatedAt;

    AliasStatePayload(String namespace, String code, String alias, String stableVersion, String candidateVersion,
            Integer candidateWeightBps, String targetingPolicy, Map<String, String> targetingParameters, Boolean deleted,
            long revision, String actor, long updatedAt) {
        this.namespace = namespace;
        this.code = code;
        this.alias = alias;
        this.stableVersion = stableVersion;
        this.candidateVersion = candidateVersion;
        this.candidateWeightBps = candidateWeightBps;
        this.targetingPolicy = targetingPolicy;
        this.targetingParameters = targetingParameters;
        this.deleted = deleted;
        this.revision = revision;
        this.actor = actor;
        this.updatedAt = updatedAt;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getKind() {
        return kind;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getCode() {
        return code;
    }

    public String getAlias() {
        return alias;
    }

    public String getStableVersion() {
        return stableVersion;
    }

    public String getCandidateVersion() {
        return candidateVersion;
    }

    public Integer getCandidateWeightBps() {
        return candidateWeightBps;
    }

    public String getTargetingPolicy() {
        return targetingPolicy;
    }

    public Map<String, String> getTargetingParameters() {
        return targetingParameters;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public long getRevision() {
        return revision;
    }

    public String getActor() {
        return actor;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
