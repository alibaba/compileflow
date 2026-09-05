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

import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunPage;
import com.alibaba.compileflow.durable.api.model.ProcessRunQuery;
import com.alibaba.compileflow.durable.api.model.ProcessRunResult;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.WaitToken;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe application entry point for CompileFlow Durable Process execution.
 *
 * <p>Mutations return authoritative committed Run state. Every Start requires a caller-allocated
 * {@link ProcessRunId}, so an ambiguous response can be resolved with {@link #getRun(ProcessRunId)}.
 * The value must be globally unique and must never be reused for another Run, including after
 * retention. Start never waits for completion. Cancellation is cooperative and may remain requested
 * while an external Effect outcome is uncertain.</p>
 *
 * <p>Start input is a closed, partial map of variables declared by the selected exact Process as
 * {@code inOutType="param"}. Implementations reject undeclared, {@code return}, and {@code inner}
 * keys before committing a Run. An omitted parameter retains its definition-owned default; a
 * present key with a null value is an explicit null.</p>
 *
 * <p>{@link #getRun(ProcessRunId)} and {@link #listRuns(ProcessRunQuery)} return
 * payload-blind Run state. Implementations must not require state, cursor,
 * Wait token, Wait completion payload, or Effect input/result materialization merely
 * to serve these queries. The default runtime fails closed when the Store
 * implementation violates requested identity, filters, bounds, keyset order,
 * or continuation semantics.</p>
 *
 * <p>Starting a Process creates a persistent Run. Subsequent mutations and queries operate on
 * persisted Run state, while independently managed Durable Workers advance eligible Runs. This
 * interface does not own Durable Worker lifecycle.</p>
 *
 * @author yusu
 * @see ProcessEngine
 */
public interface DurableProcessEngine {
    /**
     * Starts a caller-identified Run from an explicit definition source.
     *
     * <p>The definition is source-only. The receiving Engine configuration supplies its model
     * format.</p>
     *
     * <p>The caller must never reuse {@code runId} for another Run, including after retention.</p>
     *
     * @param runId caller-allocated Run identity
     * @param definition explicit definition source
     * @param input closed process input
     * @return committed Run state
     */
    ProcessRun start(ProcessRunId runId, ProcessDefinition definition, Map<String, ?> input);

    /**
     * Starts a new Run with a caller-allocated occurrence identity.
     *
     * <p>The Run ID is an identity and recovery handle, not an idempotency key or reusable name. An
     * existing ID is rejected with {@code RUN_ALREADY_EXISTS}; after an ambiguous response the caller
     * should query {@link #getRun(ProcessRunId)} rather than infer request equivalence. The caller must
     * never assign this value to another Run, even after retention removes the original Run.</p>
     *
     * @param runId caller-allocated Run identity
     * @param version exact process version
     * @param input closed process input
     * @return committed Run state
     */
    ProcessRun start(ProcessRunId runId, ProcessRef.Version version, Map<String, ?> input);

    /**
     * Starts a caller-identified Run after resolving the published Alias exactly once.
     *
     * @param runId caller-allocated Run identity
     * @param alias published process Alias
     * @param input closed process input
     * @return committed Run state
     */
    default ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input) {
        return start(runId, alias, input, AliasRoutingOptions.defaults());
    }

    /**
     * Starts a caller-identified Run after resolving the published Alias exactly once.
     *
     * <p>If the Run ID already exists, implementations must reject it before consulting mutable
     * Alias state. This keeps duplicate occurrence detection independent from later Alias movement.</p>
     *
     * @param runId caller-allocated Run identity
     * @param alias published process Alias
     * @param input closed process input
     * @param options immutable routing inputs
     * @return committed Run state
     */
    ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input, AliasRoutingOptions options);

    /**
     * Completes exactly one token-authorized active Wait by durably recording its typed result.
     *
     * <p>This operation resolves the Wait occurrence but does not synchronously advance or complete
     * the owning Process Run. Independently managed Durable Workers advance eligible Runs. The
     * integration, not the Kernel, chooses whether to carry, protect, or translate the token while
     * an external operation is outstanding.</p>
     *
     * @param waitToken one-shot active Wait capability
     * @param result typed Wait completion values
     * @return committed owning Run state
     */
    ProcessRun completeWait(WaitToken waitToken, Map<String, ?> result);

    /**
     * Requests cooperative cancellation and returns the authoritative committed Run state.
     *
     * @param runId exact Run identity
     * @return committed Run state
     */
    ProcessRun cancel(ProcessRunId runId);

    Optional<ProcessRun> getRun(ProcessRunId runId);

    ProcessRunPage listRuns(ProcessRunQuery query);

    /**
     * Returns a closed result that distinguishes missing, active, and every terminal outcome.
     *
     * @param runId exact Run identity
     * @return closed Run result
     */
    ProcessRunResult getRunResult(ProcessRunId runId);
}
