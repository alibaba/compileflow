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
package com.alibaba.compileflow.durable.spi.store;

import com.alibaba.compileflow.durable.api.model.RunRetryCode;
import java.time.Duration;
import java.util.Optional;

/**
 * Run Turn claim and token-fenced completion transitions.
 *
 * @author yusu
 */
public interface DurableTurnStore {
    Optional<DurableStore.RunClaim> claimRun(DurableStore.RunClaimRequest request);

    /**
     * One token-fenced Run advancement linearization point.
     *
     * @param lease current fenced Run lease
     * @param commit atomic turn commit
     * @return {@code true} when the current lease committed the transition
     */
    boolean commitTurn(DurableStore.RunLease lease, DurableStore.TurnCommit commit);

    boolean commitRunCancelled(DurableStore.RunLease lease);

    boolean releaseRunFault(DurableStore.RunLease lease, Duration delay, RunRetryCode code);

    boolean releaseRunAfterCapabilityLoss(DurableStore.RunLease lease, Duration delay);
}
