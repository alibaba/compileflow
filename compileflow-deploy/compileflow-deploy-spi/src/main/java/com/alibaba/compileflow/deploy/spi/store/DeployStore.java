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
package com.alibaba.compileflow.deploy.spi.store;

/**
 * Complete persistence authority for one Deploy bounded-context instance.
 *
 * <p>Composition must bind all capabilities through one implementation so immutable versions,
 * Alias state, rollout transitions, and routing outbox delivery cannot be split across providers.
 * Implementations own transaction algorithms and physical database resources; this contract never
 * exposes JDBC objects or vendor-specific error codes.
 *
 * @author yusu
 */
public interface DeployStore extends ProcessVersionStore, ProcessAliasStore, RolloutStore, RoutingOutboxStore {
}
