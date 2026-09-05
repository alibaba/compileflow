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
package com.alibaba.compileflow.durable.spi.admission;

import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Optional;

/**
 * Reads authoritative committed Alias state without acquiring Artifacts or executable Runtime.
 *
 * <p>Implementations must be thread-safe. External I/O must be bounded and preserve interruption.
 * Absence or failure fails Alias admission without fallback to another authority. The application
 * or dependency-injection container owns the source lifecycle.
 *
 * @author yusu
 */
@FunctionalInterface
public interface DurableAliasStateSource {
    /**
     * Finds the current committed state for one exact Alias.
     *
     * @param alias exact Alias identity
     * @return non-null optional containing authoritative state when present
     */
    Optional<DurableAliasState> find(ProcessRef.Alias alias);
}
