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
package com.alibaba.compileflow.deploy.control;

import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;

/**
 * Completes a route-changing command after its authoritative transaction commits.
 *
 * <p>The completion boundary is topology-specific. Embedded deployments apply the
 * committed state locally and wait until its versions are executable. Distributed
 * deployments request immediate outbox dispatch while workers converge asynchronously.
 * Implementations must tolerate replay of the same or an older alias revision.</p>
 *
 * <p>An activation failure is visible to the command caller, but cannot roll back
 * the committed control-plane state.</p>
 *
 * @author yusu
 */
@FunctionalInterface
public interface RoutingActivation {
    /**
     * Activates the latest committed state for the affected route.
     *
     * @param alias authoritative alias state read after commit
     */
    void activate(ProcessAliasState alias);
}
