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

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable stored-Process catalog and local process-runtime demand projection.
 *
 * @author yusu
 */
public interface DurableCatalogStore {
    /**
     * Registers one immutable Process definition and returns the stored winner.
     *
     * @param registration exact Process registration fact
     * @return newly committed Process or the identical stored winner
     */
    DurableStore.StoredProcess registerProcess(DurableStore.ProcessRegistration registration);

    /**
     * Finds an immutable stored Process by its Store identity.
     *
     * @param processId Store Process identity
     * @return stored Process, or empty when absent
     */
    Optional<DurableStore.StoredProcess> findProcess(UUID processId);

    /**
     * Lists committed process-runtime demand using bounded keyset pagination.
     *
     * @param query bounded process-runtime demand query
     * @return immutable demand page
     */
    DurableStore.ProcessRuntimeDemandPage listProcessRuntimeDemand(DurableStore.ProcessRuntimeDemandQuery query);
}
