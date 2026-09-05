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
package com.alibaba.compileflow.durable.api;

import com.alibaba.compileflow.durable.api.command.PauseRunCommand;
import com.alibaba.compileflow.durable.api.command.ResolveEffectCommand;
import com.alibaba.compileflow.durable.api.command.ResolveOutboxEventCommand;
import com.alibaba.compileflow.durable.api.command.ResumeRunCommand;
import com.alibaba.compileflow.durable.api.model.ActiveWorkPage;
import com.alibaba.compileflow.durable.api.model.ActiveWorkQuery;
import com.alibaba.compileflow.durable.api.model.OutboxEventPage;
import com.alibaba.compileflow.durable.api.model.OutboxEventQuery;
import com.alibaba.compileflow.durable.api.model.OutboxEvent;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunPage;
import com.alibaba.compileflow.durable.api.model.ProcessRunQuery;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.ProcessTimelinePage;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineQuery;
import java.util.Optional;

/**
 * Application-code-independent query and recovery service for operators.
 *
 * <p>The service returns redaction-safe domain state and never loads or executes
 * process application code. A transport exposing this interface must derive
 * the actor from an authenticated principal, authorize the exact operation
 * and resource, enforce any required approval, rate-limit both mutations and
 * query traversal, and export the resulting audit evidence.</p>
 *
 * @author yusu
 */
public interface DurableOperatorService {
    /**
     * Reads one redaction-safe Run.
     *
     * @param runId exact Run identity
     * @return current Run, or empty when it does not exist
     */
    Optional<ProcessRun> getRun(ProcessRunId runId);

    /**
     * Lists a bounded, redaction-safe page of Runs.
     *
     * @param query immutable filter and typed keyset cursor
     * @return one authoritative page
     */
    ProcessRunPage listRuns(ProcessRunQuery query);

    /**
     * Lists one page from a frozen, payload-blind Run Timeline.
     *
     * <p>A first-page request captures the retained Run's current Journal
     * sequence. Continuations never include later events. A missing or
     * retained-away Run fails with {@code RUN_NOT_FOUND}; it is not reported
     * as an empty successful history.</p>
     *
     * @param query exact Run, order, and bounded page request
     * @return one authoritative redaction-safe Timeline page
     */
    ProcessTimelinePage listTimeline(ProcessTimelineQuery query);

    /**
     * Lists one bounded page of the Run's unresolved Wait, Timer, and Effect occurrences.
     *
     * @param query exact Run and bounded continuation request
     * @return one authoritative active-work page
     */
    ActiveWorkPage listActiveWork(ActiveWorkQuery query);

    /**
     * Stops admitting new business execution for one non-terminal Run.
     *
     * <p>Already-issued execution authority converges cooperatively, so the
     * returned control state may be {@code PAUSE_REQUESTED} before it becomes
     * {@code PAUSED}.</p>
     *
     * @param command audited pause command
     * @return authoritative Run after the transition or its current equivalent
     */
    ProcessRun pauseRun(PauseRunCommand command);

    /**
     * Restores business-execution admission or withdraws an outstanding Pause
     * request for one non-terminal Run.
     *
     * @param command audited resume command
     * @return authoritative Run after the transition or its current equivalent
     */
    ProcessRun resumeRun(ResumeRunCommand command);

    /**
     * Reads one redaction-safe Outbox delivery record.
     *
     * @param runId           owning Run
     * @param eventId         exact Outbox event identity
     * @return current record, or empty when it does not exist
     */
    Optional<OutboxEvent> getOutboxEvent(ProcessRunId runId, String eventId);

    /**
     * Lists a bounded, redaction-safe page of Outbox records.
     *
     * @param query immutable filter and typed keyset cursor
     * @return one authoritative page
     */
    OutboxEventPage listOutboxEvents(OutboxEventQuery query);

    /**
     * Applies an audited resolution to one uncertain Effect.
     *
     * @param command audited Effect resolution command
     * @return committed owning Run
     */
    ProcessRun resolveEffect(ResolveEffectCommand command);

    /**
     * Applies an audited {@link com.alibaba.compileflow.durable.api.command.OutboxResolutionDecision#RETRY
     * retry} or {@link com.alibaba.compileflow.durable.api.command.OutboxResolutionDecision#ABANDON abandon}
     * decision to one unresolved or retriable Outbox delivery after operator reconciliation.
     *
     * @param command audited Outbox resolution command
     * @return committed Outbox event
     */
    OutboxEvent resolveOutboxEvent(ResolveOutboxEventCommand command);
}
