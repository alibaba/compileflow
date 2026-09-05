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
package com.alibaba.compileflow.engine.spi.routing;

import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Optional;

/**
 * Supplies the authoritative serving route for published Alias execution.
 *
 * <p>An engine has exactly one route source. Implementations must return only routes whose exact
 * stable and candidate runtimes can be acquired by that engine, must be thread-safe, and must not
 * silently fall back to another authority on absence or failure. This authority is configured
 * explicitly; plugin discovery does not contribute it. Implementations that perform external I/O
 * must enforce bounded deadlines and preserve interruption. Source failures fail Alias admission.
 * The application or dependency-injection container owns the source lifecycle.
 *
 * @author yusu
 */
@FunctionalInterface
public interface ProcessAliasRouteSource {
    /**
     * Finds the current immutable serving route for an Alias.
     *
     * @param alias published Alias
     * @return serving route, or empty when the Alias is authoritatively unavailable
     */
    Optional<ProcessAliasRoute> find(ProcessRef.Alias alias);
}
