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
package com.alibaba.compileflow.deploy.runtime.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Point-in-time diagnostics for desired and node-local-ready alias state.
 *
 * @author yusu
 */
public final class RoutingConvergenceSnapshot {
    private final int desiredAliasCount;
    private final int localReadyAliasCount;
    private final int pendingAliasCount;
    private final int failedAliasCount;
    private final List<AliasState> aliases;

    public RoutingConvergenceSnapshot(int desiredAliasCount, int localReadyAliasCount, int pendingAliasCount,
            int failedAliasCount, List<AliasState> aliases) {
        this.desiredAliasCount = desiredAliasCount;
        this.localReadyAliasCount = localReadyAliasCount;
        this.pendingAliasCount = pendingAliasCount;
        this.failedAliasCount = failedAliasCount;
        this.aliases = aliases == null || aliases.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(aliases));
    }

    public int getDesiredAliasCount() {
        return desiredAliasCount;
    }

    public int getLocalReadyAliasCount() {
        return localReadyAliasCount;
    }

    public int getPendingAliasCount() {
        return pendingAliasCount;
    }

    public int getFailedAliasCount() {
        return failedAliasCount;
    }

    public List<AliasState> getAliases() {
        return aliases;
    }

    public enum ConvergenceState {
        LOCAL_READY,
        PENDING,
        FAILED
    }

    public static final class AliasState {
        private final String namespace;
        private final String code;
        private final String alias;
        private final long desiredRevision;
        private final boolean desiredDeleted;
        private final long localReadyRevision;
        private final boolean localReadyDeleted;
        private final ConvergenceState convergenceState;
        private final String failureReason;

        public AliasState(String namespace, String code, String alias, long desiredRevision, boolean desiredDeleted,
                long localReadyRevision, boolean localReadyDeleted, ConvergenceState convergenceState,
                String failureReason) {
            this.namespace = Objects.requireNonNull(namespace, "namespace");
            this.code = Objects.requireNonNull(code, "code");
            this.alias = Objects.requireNonNull(alias, "alias");
            this.desiredRevision = desiredRevision;
            this.desiredDeleted = desiredDeleted;
            this.localReadyRevision = localReadyRevision;
            this.localReadyDeleted = localReadyDeleted;
            this.convergenceState = Objects.requireNonNull(convergenceState, "convergenceState");
            this.failureReason = failureReason;
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

        public long getDesiredRevision() {
            return desiredRevision;
        }

        public boolean isDesiredDeleted() {
            return desiredDeleted;
        }

        public long getLocalReadyRevision() {
            return localReadyRevision;
        }

        public boolean isLocalReadyDeleted() {
            return localReadyDeleted;
        }

        public ConvergenceState getConvergenceState() {
            return convergenceState;
        }

        public String getFailureReason() {
            return failureReason;
        }
    }
}
