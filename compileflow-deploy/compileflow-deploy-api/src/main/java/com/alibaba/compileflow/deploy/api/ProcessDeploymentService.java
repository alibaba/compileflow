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
package com.alibaba.compileflow.deploy.api;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PublishProcessVersionCommand;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutEvent;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPage;
import com.alibaba.compileflow.deploy.api.rollout.RolloutQuery;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import com.alibaba.compileflow.deploy.api.version.PublishedProcessVersion;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionPage;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionQuery;
import java.util.List;
import java.util.Optional;

/**
 * Control-plane facade for CompileFlow deployment operations.
 *
 * <h2>Operation Semantics</h2>
 * <p>The service persists authoritative state in the deployment repositories.
 * Runtime projection starts only after the Alias transaction commits. An embedded
 * composition may wait for process-local readiness before a route-changing method
 * returns; a distributed composition dispatches the committed outbox asynchronously.
 * Repository state remains the source of truth in both cases.
 *
 * <p>A local activation failure can be reported after the authoritative transaction
 * has committed and cannot roll that transaction back. Callers must retain rollout
 * idempotency keys, re-read the Alias and rollout after an ambiguous failure, and use
 * deployment diagnostics to distinguish committed intent from runtime convergence.
 *
 * <p>{@link #publish(PublishProcessVersionCommand)} validates the immutable source identity,
 * stores its exact content and digest, and projects that persisted winner to the configured
 * artifact transport. It neither compiles a node-local runtime nor changes an Alias. Every
 * production Alias mutation is represented by a rollout with a scoped idempotency key,
 * revision precondition, captured baseline, authenticated actor, and append-only audit history.
 *
 * @author yusu
 */
public interface ProcessDeploymentService {
    /**
     * Finds one immutable published process version.
     *
     * @param ref exact published-version reference
     * @return published version when present
     */
    Optional<PublishedProcessVersion> getVersion(ProcessRef.Version ref);

    /**
     * Lists immutable versions for one process identity with explicit pagination.
     *
     * @param query process identity, optional version prefix, and pagination
     * @return matching published-version page in newest-first order
     */
    PublishedVersionPage listVersions(PublishedVersionQuery query);

    /**
     * Counts distinct process identities that have at least one committed Alias.
     *
     * <p>A published version is not considered active until an Alias routes to it.
     * Multiple Aliases for the same process are counted once.
     *
     * @param namespace process namespace
     * @return number of process identities with at least one Alias in the namespace
     */
    long countProcessesWithAliases(String namespace);

    /**
     * Finds the authoritative state of one published Alias.
     *
     * @param ref exact Alias reference
     * @return Alias state when present
     */
    Optional<ProcessAliasState> getAlias(ProcessRef.Alias ref);

    /**
     * Creates an idempotent rollout and commits its initial Alias mutation.
     *
     * @param command rollout specification and idempotency key
     * @return authoritative rollout snapshot
     */
    ProcessRollout createRollout(CreateRolloutCommand command);

    /**
     * Finds a rollout by its stable operation id.
     *
     * @param rolloutId rollout id
     * @return rollout snapshot when present
     */
    Optional<ProcessRollout> getRollout(String rolloutId);

    /**
     * Lists rollout history using control-plane filters.
     *
     * @param query filters and pagination
     * @return matching rollout page
     */
    RolloutPage listRollouts(RolloutQuery query);

    /**
     * Returns append-only audit events for one rollout.
     *
     * @param rolloutId rollout id
     * @return events ordered by sequence
     */
    List<RolloutEvent> listRolloutEvents(String rolloutId);

    /**
     * Changes the candidate traffic weight using revision-based concurrency control.
     *
     * @param command canary update command
     * @return updated rollout
     */
    ProcessRollout updateCanaryWeight(UpdateCanaryWeightCommand command);

    /**
     * Promotes the candidate to the sole stable Alias version.
     *
     * @param command promotion command
     * @return completed rollout
     */
    ProcessRollout promoteRollout(PromoteRolloutCommand command);

    /**
     * Aborts a canary and restores the baseline captured at creation time.
     *
     * @param command abort command
     * @return aborted rollout
     */
    ProcessRollout abortRollout(AbortRolloutCommand command);

    /**
     * Creates a new all-at-once rollout to an earlier rollout's captured baseline.
     *
     * @param command source rollout, idempotency key, Alias revision, and actor
     * @return newly created rollback rollout
     */
    ProcessRollout rollbackRollout(RollbackRolloutCommand command);

    /**
     * Publishes a new immutable process version without changing an Alias.
     *
     * @param command publish command containing source, actor, and metadata
     * @return authoritative immutable published version
     */
    PublishedProcessVersion publish(PublishProcessVersionCommand command);
}
