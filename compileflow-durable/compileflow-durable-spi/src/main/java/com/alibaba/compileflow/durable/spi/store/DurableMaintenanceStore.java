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

/**
 * Database-time recovery, timer, and retention maintenance transitions.
 *
 * @author yusu
 */
public interface DurableMaintenanceStore {
    int reclaimExpiredRuns(int limit);

    /**
     * Resolves due Timer occurrences and expired Event Wait occurrences using Store time.
     *
     * @param limit maximum occurrences to resolve
     * @return resolved occurrence count
     */
    int resolveDueWaits(int limit);

    int reclaimExpiredEffects(int limit);

    int reclaimExpiredOutbox(int limit);

    /**
     * Purges terminal Runs older than the retention boundary.
     *
     * @param retention minimum terminal retention
     * @param limit maximum Runs to purge
     * @return purged Run count
     */
    int purgeTerminalRuns(Duration retention, int limit);

    /**
     * Purges consumed Wait/Effect occurrences whose required Outbox obligation has settled.
     *
     * @param retention minimum consumed-occurrence retention
     * @param limit maximum occurrences to purge
     * @return purged occurrence count
     */
    int purgeConsumedOccurrences(Duration retention, int limit);

    int purgeUnusedProcesses(Duration retention, int limit);
}
