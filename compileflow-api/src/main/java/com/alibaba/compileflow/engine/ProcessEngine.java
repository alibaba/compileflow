/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The primary, user-facing interface for the CompileFlow engine.
 * <p>
 * <b>RESOURCE WARNING:</b> Each ProcessEngine creates 4 thread pools.
 * <b>Always use singleton pattern or try-with-resources to avoid resource leaks.</b>
 * <p>
 * <b>Recommended Usage:</b>
 * <ul>
 *   <li><b>Best:</b> Spring Boot auto-configuration (singleton)</li>
 *   <li><b>Good:</b> Manual singleton with shutdown hook</li>
 *   <li><b>Limited:</b> Try-with-resources for batch jobs only</li>
 *   <li><b>Never:</b> Create per-request or in loops</li>
 * </ul>
 * <p>
 * Engine instances are thread-safe and should be obtained via {@link ProcessEngineFactory}.
 *
 * @param <T> The specific type of {@link FlowModel} this engine is configured for
 * @author yusu
 * @see ProcessEngineFactory
 * @see ProcessAdminService
 * @see ProcessToolingService
 */
public interface ProcessEngine<T extends FlowModel> extends AutoCloseable {

    /**
     * Executes a process using a type-safe Data Transfer Object (DTO) for process variables.
     * <p>
     * The engine handles the conversion between the DTO and the internal context map automatically.
     *
     * <pre>{@code
     * // 1. Define your process source
     * ProcessSource source = ProcessSource.fromClasspath("path/to/your/flow.bpmn");
     *
     * // 2. Create your input DTO
     * MyRequest request = new MyRequest();
     * request.setParam("someValue");
     *
     * // 3. Execute the process
     * ProcessResult<MyResponse> result = processEngine.execute(source, request, MyResponse.class);
     *
     * // 4. Handle the result
     * result.onSuccess(response -> System.out.println("Success! " + response.getResultData()))
     *       .onFailure(error -> System.err.println("Failed: " + error));
     * }</pre>
     *
     * @param source     The source of the process definition.
     * @param input      A Plain Old Java Object (POJO) containing the input parameters.
     * @param outputType The {@code Class} of the expected response DTO.
     * @param <I>        The type of the input object.
     * @param <O>        The type of the output object.
     * @return A {@link ProcessResult} containing the execution outcome and the typed response data.
     */
    <I, O> ProcessResult<O> execute(@NotNull ProcessSource source, @NotNull I input, @NotNull Class<O> outputType);

    /**
     * Executes a process using a generic {@code Map} for process variables.
     * <p>
     * This method offers flexibility and is useful for simpler scenarios, dynamic use cases,
     * or for testing purposes where creating a DTO is unnecessary.
     *
     * <pre>{@code
     * // 1. Define your process source
     * ProcessSource source = ProcessSource.fromCode("my.process.code");
     *
     * // 2. Prepare the context map
     * Map<String, Object> context = new HashMap<>();
     * context.put("customerLevel", "VIP");
     * context.put("orderAmount", 500.0);
     *
     * // 3. Execute the process
     * ProcessResult<Map<String, Object>> result = processEngine.execute(source, context);
     *
     * // 4. Handle the result
     * if (result.isSuccess()) {
     *     Map<String, Object> output = result.getData();
     *     System.out.println("Discount applied: " + output.get("discount"));
     * }
     * }</pre>
     *
     * @param source  The source of the process definition.
     * @param context A map containing the key-value pairs of the execution context.
     * @return A {@link ProcessResult} containing the execution outcome and a map of output variables.
     */
    ProcessResult<Map<String, Object>> execute(@NotNull ProcessSource source, @NotNull Map<String, Object> context);

    /**
     * Triggers an event on a stateful process instance using a type-safe DTO as the event payload.
     *
     * <pre>{@code
     * // Assuming a process is waiting at a node with tag "paymentWaitingNode"
     * ProcessSource source = ProcessSource.fromCode("order.process.stateful");
     * String waitingNodeTag = "paymentWaitingNode";
     *
     * // Create event payload DTO
     * PaymentEvent eventPayload = new PaymentEvent("payment_success");
     *
     * // Trigger the event
     * ProcessResult<OrderState> result = processEngine.trigger(source, waitingNodeTag,
     *                                                           "paymentReceived", eventPayload,
     *                                                           OrderState.class);
     *
     * result.onSuccess(newState -> System.out.println("Process advanced to state: " + newState));
     * }</pre>
     *
     * @param source       The source of the stateful process definition.
     * @param tag          The unique tag of the waiting node to be triggered.
     * @param event        The specific event identifier to trigger on the node (can be {@code null}).
     * @param eventPayload A POJO representing the event data.
     * @param outputType   The {@code Class} of the expected response DTO.
     * @param <E>          The type of the event payload object.
     * @param <O>          The type of the output object.
     * @return A {@link ProcessResult} containing the outcome and the typed response data.
     */
    <E, O> ProcessResult<O> trigger(@NotNull ProcessSource source, @NotNull String tag, @Nullable String event,
                                    @NotNull E eventPayload, @NotNull Class<O> outputType);

    /**
     * Triggers an event on a stateful process instance using a generic {@code Map} for the event payload.
     *
     * @param source  The source of the stateful process definition.
     * @param tag     The unique tag of the waiting node to be triggered.
     * @param event   The specific event identifier to trigger on the node (can be {@code null}).
     * @param context A map containing the event data to be merged into the process context.
     * @return A {@link ProcessResult} containing the outcome and a map of output variables.
     */
    ProcessResult<Map<String, Object>> trigger(@NotNull ProcessSource source, @NotNull String tag, @Nullable String event,
                                               @NotNull Map<String, Object> context);

    /**
     * A convenience overload for {@link #trigger(ProcessSource, String, String, Object, Class)} that omits the event name.
     *
     * @param source       The source of the stateful process definition.
     * @param tag          The unique tag of the waiting node to be triggered.
     * @param eventPayload A POJO representing the event data.
     * @param outputType   The {@code Class} of the expected response DTO.
     * @param <E>          The type of the event payload object.
     * @param <O>          The type of the output object.
     * @return A {@link ProcessResult} containing the outcome and the typed response data.
     */
    <E, O> ProcessResult<O> trigger(@NotNull ProcessSource source, @NotNull String tag,
                                    @NotNull E eventPayload, @NotNull Class<O> outputType);

    /**
     * A convenience overload for {@link #trigger(ProcessSource, String, String, Map)} that omits the event name.
     *
     * @param source  The source of the stateful process definition.
     * @param tag     The unique tag of the waiting node to be triggered.
     * @param context A map containing the event data.
     * @return A {@link ProcessResult} containing the outcome and a map of output variables.
     */
    ProcessResult<Map<String, Object>> trigger(@NotNull ProcessSource source, @NotNull String tag,
                                               @NotNull Map<String, Object> context);

    /**
     * Returns the administrative view of this engine.
     * <p>
     * This service provides access to lifecycle management operations such as pre-compiling flows
     * (cache warming) and hot-deploying process definitions. It is typically used during
     * application startup or for operational management.
     *
     * @return The {@link ProcessAdminService} for this engine, never {@code null}.
     */
    @NotNull
    ProcessAdminService admin();

    /**
     * Returns the tooling and introspection view of this engine.
     * <p>
     * This service provides access to development and debugging operations, such as retrieving the
     * parsed flow model or generating Java source code. It is primarily intended for use in
     * development tools, IDE plugins, or testing environments.
     *
     * @return The type-safe {@link ProcessToolingService} for this engine, never {@code null}.
     */
    @NotNull
    ProcessToolingService<T> tooling();

    @Override
    void close();

}
