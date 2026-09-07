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
package com.alibaba.compileflow.engine;

import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import java.util.Map;

/**
 * Thread-safe CompileFlow execution engine for all installed semantic frontends.
 *
 * <p>The canonical execution model is a string-keyed variable map. Typed object methods are thin
 * adapters over the same map pipeline through the engine's configured {@link ProcessDataMapper}.
 * Process execution accepts a closed, partial map of variables declared by the exact Process as
 * {@code inOutType="param"}; undeclared, {@code return}, and {@code inner} keys are rejected.
 * An omitted parameter keeps its definition-owned default, while a present key with a null value
 * is an explicit null.
 * Map execution treats the supplied top-level map and every object reachable from its values as
 * borrowed, read-only input. A runtime may pass values by reference or materialize detached
 * values; neither grants an Action mutation rights. Process-owned state changes only through
 * declared outputs and explicit Process constructs. Callers must not mutate or reuse input values
 * concurrently with execution. A successful map execution returns a separate top-level map
 * containing the process's declared output variables.
 * <p>
 * Each engine owns executors, runtime caches, and a class-loader scope, so applications should
 * create one long-lived instance per required configuration and close it during shutdown.
 *
 * @author yusu
 * @see ProcessEngineFactory
 * @see ProcessRuntimeManager
 * @see ProcessToolingService
 */
public interface ProcessEngine extends AutoCloseable {
    /**
     * Executes an existing process reference through the canonical variable-map pipeline.
     *
     * @param ref       exact version or Alias reference
     * @param variables process input variables
     * @param options   request-scoped execution options
     * @return process outcome with output variables and controlled attribution
     */
    ProcessResult<Map<String, Object>> execute(ProcessRef ref, Map<String, Object> variables,
            ProcessExecutionOptions options);

    /**
     * Executes an explicit process definition through the canonical variable-map pipeline.
     *
     * <p>This runs real process actions with the host application's privileges. It is not a
     * sandbox or a side-effect-free validation operation. Use
     * {@link ProcessToolingService#preflight(ProcessDefinition, ProcessPreflightOptions)} when the
     * definition must be checked without execution.
     *
     * @param definition explicit definition source
     * @param variables  process input variables
     * @param options    request-scoped execution options
     * @return process outcome with output variables and controlled attribution
     */
    ProcessResult<Map<String, Object>> execute(ProcessDefinition definition, Map<String, Object> variables,
            ProcessExecutionOptions options);

    /**
     * Executes an existing process reference with default options.
     *
     * @param ref       process reference
     * @param variables process input variables
     * @return process outcome
     */
    default ProcessResult<Map<String, Object>> execute(ProcessRef ref, Map<String, Object> variables) {
        return execute(ref, variables, ProcessExecutionOptions.defaults());
    }

    /**
     * Executes an explicit process definition with default options.
     *
     * <p>This has the same real-action and side-effect boundary as
     * {@link #execute(ProcessDefinition, Map, ProcessExecutionOptions)}.
     *
     * @param definition explicit process definition
     * @param variables  process input variables
     * @return process outcome
     */
    default ProcessResult<Map<String, Object>> execute(ProcessDefinition definition, Map<String, Object> variables) {
        return execute(definition, variables, ProcessExecutionOptions.defaults());
    }

    /**
     * Maps typed input into variables, executes an existing reference, and maps the output.
     *
     * @param ref        process reference
     * @param input      typed process input
     * @param outputType requested output type
     * @param options    request-scoped execution options
     * @param <I>        input object type
     * @param <O>        output object type
     * @return typed process outcome from the canonical map pipeline
     */
    <I, O> ProcessResult<O> execute(ProcessRef ref, I input, Class<O> outputType, ProcessExecutionOptions options);

    /**
     * Maps typed input into variables, executes an existing reference with default options, and
     * maps the output.
     *
     * @param ref        process reference
     * @param input      typed process input
     * @param outputType requested output type
     * @param <I>        input object type
     * @param <O>        output object type
     * @return typed process outcome from the canonical map pipeline
     */
    default <I, O> ProcessResult<O> execute(ProcessRef ref, I input, Class<O> outputType) {
        return execute(ref, input, outputType, ProcessExecutionOptions.defaults());
    }

    /**
     * Maps typed input into variables, executes an explicit definition, and maps the output.
     *
     * <p>This has the same real-action and side-effect boundary as
     * {@link #execute(ProcessDefinition, Map, ProcessExecutionOptions)}.
     *
     * @param definition explicit process definition
     * @param input      typed process input
     * @param outputType requested output type
     * @param options    request-scoped execution options
     * @param <I>        input object type
     * @param <O>        output object type
     * @return typed process outcome from the canonical map pipeline
     */
    <I, O> ProcessResult<O> execute(ProcessDefinition definition, I input, Class<O> outputType,
            ProcessExecutionOptions options);

    /**
     * Maps typed input into variables, executes an explicit definition with default options, and
     * maps the output.
     *
     * @param definition explicit process definition
     * @param input      typed process input
     * @param outputType requested output type
     * @param <I>        input object type
     * @param <O>        output object type
     * @return typed process outcome from the canonical map pipeline
     */
    default <I, O> ProcessResult<O> execute(ProcessDefinition definition, I input, Class<O> outputType) {
        return execute(definition, input, outputType, ProcessExecutionOptions.defaults());
    }

    /**
     * Starts a new process execution from the specified trigger entry.
     *
     * <p>This method does not resume a persisted process instance and does not provide durable
     * event delivery, external message correlation, or workflow state recovery. It uses the same
     * reference resolution, routing, runtime, and result pipeline as {@link #execute(ProcessRef,
     * Map, ProcessExecutionOptions)}. Unlike {@code execute} input, {@code variables} is a
     * partial state seed for the new downstream invocation: it may contain any root variable
     * declared by the exact Process, including {@code return} and {@code inner}, but no undeclared
     * keys. Durable wait/event completion uses its own occurrence protocol and never reconstructs
     * continuation state through this method.
     *
     * @param ref       process reference
     * @param trigger   named trigger entry and optional event selector
     * @param variables declared root-state values supplied to this downstream invocation
     * @param options   request-scoped execution options
     * @return trigger outcome with output variables and controlled attribution
     */
    ProcessResult<Map<String, Object>> trigger(ProcessRef ref, ProcessTrigger trigger, Map<String, Object> variables,
            ProcessExecutionOptions options);

    /**
     * Starts a new execution from a trigger entry in an explicit definition source.
     *
     * @param definition explicit process definition
     * @param trigger    named trigger entry and optional event selector
     * @param variables  declared root-state values supplied to this downstream invocation
     * @param options    request-scoped execution options
     * @return trigger outcome with output variables and controlled attribution
     */
    ProcessResult<Map<String, Object>> trigger(ProcessDefinition definition, ProcessTrigger trigger,
            Map<String, Object> variables, ProcessExecutionOptions options);

    /**
     * Starts a new process execution from a trigger entry with default options.
     *
     * @param ref       process reference
     * @param trigger   named trigger entry and optional event selector
     * @param variables declared root-state values supplied to this downstream invocation
     * @return trigger outcome
     */
    default ProcessResult<Map<String, Object>> trigger(ProcessRef ref, ProcessTrigger trigger,
            Map<String, Object> variables) {
        return trigger(ref, trigger, variables, ProcessExecutionOptions.defaults());
    }

    /**
     * Starts a new execution from a trigger entry in an explicit definition source with default
     * options.
     *
     * @param definition explicit process definition
     * @param trigger    named trigger entry and optional event selector
     * @param variables  declared root-state values supplied to this downstream invocation
     * @return trigger outcome with output variables and controlled attribution
     */
    default ProcessResult<Map<String, Object>> trigger(ProcessDefinition definition, ProcessTrigger trigger,
            Map<String, Object> variables) {
        return trigger(definition, trigger, variables, ProcessExecutionOptions.defaults());
    }

    /**
     * Maps typed input into variables, starts a new execution at a trigger entry, and maps output.
     *
     * @param ref        process reference
     * @param trigger    named trigger entry and optional event selector
     * @param input      typed declared root-state seed for the downstream invocation
     * @param outputType requested output type
     * @param options    request-scoped execution options
     * @param <I>        input object type
     * @param <O>        output object type
     * @return typed trigger outcome from the canonical map pipeline
     */
    <I, O> ProcessResult<O> trigger(ProcessRef ref, ProcessTrigger trigger, I input, Class<O> outputType,
            ProcessExecutionOptions options);

    /**
     * Maps typed input, starts an explicit definition at a trigger entry, and maps the output.
     *
     * @param definition explicit process definition
     * @param trigger    named trigger entry and optional event selector
     * @param input      typed declared root-state seed for the downstream invocation
     * @param outputType requested output type
     * @param options    request-scoped execution options
     * @param <I>        input object type
     * @param <O>        output object type
     * @return typed trigger outcome from the canonical map pipeline
     */
    <I, O> ProcessResult<O> trigger(ProcessDefinition definition, ProcessTrigger trigger, I input, Class<O> outputType,
            ProcessExecutionOptions options);

    /**
     * Maps typed input into variables, starts a new execution at a trigger entry with default
     * options, and maps output.
     *
     * @param ref        process reference
     * @param trigger    named trigger entry and optional event selector
     * @param input      typed declared root-state seed for the downstream invocation
     * @param outputType requested output type
     * @param <I>        input object type
     * @param <O>        output object type
     * @return typed trigger outcome from the canonical map pipeline
     */
    default <I, O> ProcessResult<O> trigger(ProcessRef ref, ProcessTrigger trigger, I input, Class<O> outputType) {
        return trigger(ref, trigger, input, outputType, ProcessExecutionOptions.defaults());
    }

    /**
     * Maps typed input, starts an explicit definition at a trigger entry with default options, and
     * maps the output.
     *
     * @param definition explicit process definition
     * @param trigger    named trigger entry and optional event selector
     * @param input      typed declared root-state seed for the downstream invocation
     * @param outputType requested output type
     * @param <I>        input object type
     * @param <O>        output object type
     * @return typed trigger outcome from the canonical map pipeline
     */
    default <I, O> ProcessResult<O> trigger(ProcessDefinition definition, ProcessTrigger trigger, I input,
            Class<O> outputType) {
        return trigger(definition, trigger, input, outputType, ProcessExecutionOptions.defaults());
    }

    /**
     * Returns this engine's local runtime lifecycle manager.
     *
     * @return engine-local runtime manager
     */
    ProcessRuntimeManager runtime();

    /**
     * Returns this engine's non-executing development tooling view.
     *
     * @return engine tooling service
     */
    ProcessToolingService tooling();

    /**
     * Rejects new work, drains active public operations within the configured grace period, and
     * releases engine-owned resources.
     * <p>
     * The application lifecycle owner must invoke this method from outside engine operations and
     * callbacks. Calling it from generated process code, a listener, or another engine-owned task
     * would make that task wait for its own executor to terminate and is therefore rejected.
     *
     * @throws IllegalStateException if called from an active operation or engine-owned callback
     *                               or if engine-owned resources cannot terminate within the configured shutdown budget
     */
    @Override
    void close();
}
