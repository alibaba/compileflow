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
import com.alibaba.compileflow.deploy.runtime.state.DesiredRoutingState;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Derives runtime demand from one already-ordered authoritative alias state.
 *
 * @author yusu
 */
public final class VersionDemandPlanner {
    private static Set<ProcessRef.Version> computeDemand(DesiredRoutingState change) {
        if (change.isDeleted()) {
            return Collections.emptySet();
        }
        Set<ProcessRef.Version> demanded = new LinkedHashSet<>();
        demanded.add(ProcessRef.version(change.getNamespace(), change.getCode(), change.getStableVersion()));
        if (change.getCandidateVersion() != null) {
            demanded.add(ProcessRef.version(change.getNamespace(), change.getCode(), change.getCandidateVersion()));
        }
        return demanded;
    }

    public AliasVersionDemand plan(DesiredRoutingState change) {
        Objects.requireNonNull(change, "change");
        String namespace = change.getNamespace();
        String code = change.getCode();
        String alias = change.getAlias();
        return AliasVersionDemand.forAlias(namespace, code, alias, computeDemand(change));
    }
}
