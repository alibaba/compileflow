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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure;

import com.alibaba.compileflow.durable.spi.admission.DurableAliasState;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasStateSource;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import java.util.Objects;
import java.util.Optional;

/**
 * Spring composition adapter from committed Deploy Alias state to Durable admission.
 *
 * @author yusu
 */
final class DeployAliasStateSourceAdapter implements DurableAliasStateSource {
    private final ProcessDeploymentService deployments;

    DeployAliasStateSourceAdapter(ProcessDeploymentService deployments) {
        this.deployments = Objects.requireNonNull(deployments, "deployments");
    }

    @Override
    public Optional<DurableAliasState> find(ProcessRef.Alias alias) {
        ProcessRef.Alias requested = Objects.requireNonNull(alias, "alias");
        Optional<ProcessAliasState> result =
                Objects.requireNonNull(deployments.getAlias(requested), "Deploy service returned null Alias lookup");
        return result.map(state -> adapt(requested, state));
    }

    private static DurableAliasState adapt(ProcessRef.Alias requested, ProcessAliasState state) {
        if (!requested.equals(state.getRef())) {
            throw new IllegalStateException("Deploy service returned a different Alias identity");
        }
        Integer candidateWeight = state.getCandidateWeightBps();
        return new DurableAliasState(requested, state.getStableVersion(), state.getCandidateVersion(),
                candidateWeight == null ? 0 : candidateWeight, state.getTargeting(), state.getAliasRevision());
    }
}
