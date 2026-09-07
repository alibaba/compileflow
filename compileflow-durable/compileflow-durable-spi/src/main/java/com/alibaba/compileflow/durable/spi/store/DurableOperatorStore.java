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

import com.alibaba.compileflow.durable.api.model.OutboxEvent;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import java.util.Optional;
import java.util.UUID;

/**
 * Atomic operator-control and redaction-safe diagnostic projection authority.
 *
 * @author yusu
 */
public interface DurableOperatorStore {
    Optional<ProcessRun> findRun(ProcessRunId runId);

    DurableStore.RunPage listRuns(DurableStore.RunQuery query);

    DurableStore.TimelinePage listTimeline(DurableStore.TimelineQuery query);

    DurableStore.ActiveWorkPage listActiveWork(DurableStore.ActiveWorkQuery query);

    ProcessRun control(DurableStore.ControlCommand command);

    ProcessRun resolveEffect(DurableStore.EffectResolution command);

    OutboxEvent resolveOutbox(DurableStore.OutboxResolution command);

    Optional<DurableStore.EffectTarget> findEffectTarget(ProcessRunId runId, UUID effectId);

    Optional<OutboxEvent> findOutbox(ProcessRunId runId, UUID eventId);

    DurableStore.OutboxPage listOutbox(DurableStore.OutboxQuery query);
}
