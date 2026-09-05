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
package com.alibaba.compileflow.deploy.runtime.demand;

import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Demand decision listing the immutable versions required for one alias.
 *
 * @author yusu
 */
public final class AliasVersionDemand {
    private static final int MAX_DEMANDED_VERSIONS = 2;
    private final ProcessRef.Alias aliasRef;
    private final Set<ProcessRef.Version> demandedVersions;

    private AliasVersionDemand(ProcessRef.Alias aliasRef, Set<ProcessRef.Version> demandedVersions) {
        this.aliasRef = aliasRef;
        this.demandedVersions = demandedVersions;
    }

    public static AliasVersionDemand forAlias(String namespace, String code, String alias,
            Set<ProcessRef.Version> demandedVersions) {
        ProcessRef.Alias aliasRef = ProcessRef.alias(namespace, code, alias);
        Set<ProcessRef.Version> versions =
                new LinkedHashSet<>(Objects.requireNonNull(demandedVersions, "demandedVersions"));
        if (versions.size() > MAX_DEMANDED_VERSIONS) {
            throw new IllegalArgumentException("An alias may demand at most one stable and one candidate version");
        }
        for (ProcessRef.Version versionRef : versions) {
            Objects.requireNonNull(versionRef, "demandedVersions must not contain null");
            if (!aliasRef.namespace().equals(versionRef.namespace()) || !aliasRef.code().equals(versionRef.code())) {
                throw new IllegalArgumentException(
                        "Alias demand contains a version from another process: " + versionRef);
            }
        }
        return new AliasVersionDemand(aliasRef, Collections.unmodifiableSet(versions));
    }

    public String getNamespace() {
        return aliasRef.namespace();
    }

    public String getCode() {
        return aliasRef.code();
    }

    public String getAlias() {
        return aliasRef.alias();
    }

    public Set<ProcessRef.Version> getDemandedVersions() {
        return demandedVersions;
    }
}
