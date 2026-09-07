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

import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutEvent;
import java.util.List;
import java.util.Optional;

/**
 * Authoritative persistence boundary for rollout operations and route mutations.
 *
 * <p>Every mutating method commits the rollout state, append-only audit event,
 * route snapshot, and routing outbox record in one transaction.</p>
 *
 * @author yusu
 */
public interface RolloutStore {
    ProcessRollout create(RolloutCreateRequest request);

    Optional<ProcessRollout> find(String rolloutId);

    List<ProcessRollout> list(RolloutStoreQuery query);

    List<RolloutEvent> listEvents(String rolloutId);

    ProcessRollout updateCanary(UpdateCanaryWeightCommand command);

    ProcessRollout promote(PromoteRolloutCommand command);

    ProcessRollout abort(AbortRolloutCommand command);
}
