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

import java.time.Duration;
import java.util.Set;

/**
 * Bounded batch-renewal authority used by the shared lease heartbeat component.
 *
 * <p>A successful call returns exactly the input authorities that remain current after the
 * transaction. Missing authorities are permanently lost; an exception leaves their status unknown
 * to the caller.</p>
 *
 * @author yusu
 */
public interface DurableLeaseStore {
    int MAX_RENEWAL_BATCH_SIZE = 256;

    /**
     * Renews one bounded snapshot of Run authorities.
     *
     * @param leases     immutable authority snapshot
     * @param leaseDuration positive whole-millisecond duration, at most one hour, measured from authoritative Store
     *                      time
     * @return authorities that were still current and renewed
     */
    Set<DurableStore.RunLease> renewRunLeases(Set<DurableStore.RunLease> leases, Duration leaseDuration);

    /**
     * Renews one bounded snapshot of Effect authorities.
     *
     * @param leases     immutable authority snapshot
     * @param leaseDuration positive whole-millisecond duration, at most one hour, measured from authoritative Store
     *                      time
     * @return authorities that were still current and renewed
     */
    Set<DurableStore.EffectLease> renewEffectLeases(Set<DurableStore.EffectLease> leases, Duration leaseDuration);

    /**
     * Renews one bounded snapshot of Outbox authorities.
     *
     * @param leases     immutable authority snapshot
     * @param leaseDuration positive whole-millisecond duration, at most one hour, measured from authoritative Store
     *                      time
     * @return authorities that were still current and renewed
     */
    Set<DurableStore.OutboxLease> renewOutboxLeases(Set<DurableStore.OutboxLease> leases, Duration leaseDuration);
}
