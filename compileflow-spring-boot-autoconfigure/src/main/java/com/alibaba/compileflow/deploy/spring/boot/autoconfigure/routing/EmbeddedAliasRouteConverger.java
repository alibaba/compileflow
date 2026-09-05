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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.AliasRouteConverger;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRepository;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.state.DesiredRoutingState;
import java.time.Duration;
import java.util.Objects;

/**
 * Converges an embedded authoritative alias through the node-local readiness pipeline.
 *
 * @author yusu
 */
public final class EmbeddedAliasRouteConverger implements AliasRouteConverger {
    private final ProcessAliasRepository aliasRepository;
    private final LocalRoutingReconciler localRoutingReconciler;
    private final Duration convergenceTimeout;

    public EmbeddedAliasRouteConverger(ProcessAliasRepository aliasRepository,
            LocalRoutingReconciler localRoutingReconciler, Duration convergenceTimeout) {
        this.aliasRepository = Objects.requireNonNull(aliasRepository, "aliasRepository");
        this.localRoutingReconciler = Objects.requireNonNull(localRoutingReconciler, "localRoutingReconciler");
        this.convergenceTimeout = Objects.requireNonNull(convergenceTimeout, "convergenceTimeout");
    }

    private static DesiredRoutingState toDesiredRoutingState(ProcessAliasRecord record) {
        return DesiredRoutingState
            .builder()
            .namespace(record.getNamespace())
            .code(record.getCode())
            .alias(record.getAlias())
            .stableVersion(record.getStableVersion())
            .candidateVersion(record.getCandidateVersion())
            .candidateWeightBps(record.getCandidateWeightBps() == null ? 0 : record.getCandidateWeightBps().intValue())
            .revision(record.getRevision())
            .actor(record.getUpdatedBy())
            .updatedAt(record.getUpdatedAt())
            .build();
    }

    @Override
    public void converge(ProcessRef.Alias alias) {
        ProcessRef.Alias requested = Objects.requireNonNull(alias, "alias");
        aliasRepository
            .resolve(requested.namespace(), requested.code(), requested.alias())
            .map(EmbeddedAliasRouteConverger::toDesiredRoutingState)
            .ifPresent(change -> localRoutingReconciler.applyAndAwait(change, convergenceTimeout));
    }
}
