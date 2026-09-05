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
package com.alibaba.compileflow.engine.spi;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;

/**
 * Collects the typed capabilities contributed by one {@link ProcessEnginePlugin}.
 * <p>
 * The context deliberately exposes only supported extension capabilities. Engine execution,
 * cache, compilation, and class-loader settings remain application configuration and cannot be
 * changed silently by a classpath plugin.
 * <p>
 * Contributed instances may be retained by an immutable configuration and shared by engines built
 * from that configuration. They must therefore be thread-safe. The engine does not close supplied
 * collaborators; applications and dependency-injection containers retain resource ownership.
 * Service-loaded plugins should contribute resource-free objects.
 *
 * @author yusu
 */
public interface ProcessEnginePluginContext {
    /**
     * Returns the model type of the engine being configured.
     *
     * @return target process model type
     */
    ProcessModelType getModelType();

    /**
     * Appends an event listener to the plugin contribution.
     *
     * @param listener thread-safe event listener
     * @return this contribution context
     */
    ProcessEnginePluginContext eventListener(ProcessEventListener listener);

    /**
     * Registers a script executor by its case-insensitive language name.
     *
     * <p>Language names are globally unique across discovered plugins, explicit plugins, direct
     * registrations, and enabled bundled providers. Priority never replaces an executor with the
     * same language name; duplicate registration is a configuration error.
     *
     * @param executor thread-safe script executor
     * @return this contribution context
     */
    ProcessEnginePluginContext scriptExecutor(ScriptExecutor executor);

    /**
     * Registers a named Alias targeting policy.
     *
     * <p>The policy remains inert unless an authoritative route explicitly references its name.
     * Policy names are globally unique across plugins and direct registrations; duplicates fail
     * configuration regardless of plugin priority.
     *
     * @param policy thread-safe targeting policy
     * @return this contribution context
     */
    ProcessEnginePluginContext aliasTargetingPolicy(ProcessAliasTargetingPolicy policy);

    /**
     * Registers a named retry policy referenced by an invocation policy's {@code retryOn} value.
     *
     * <p>Retry policy names are unique across plugins; duplicates fail configuration regardless of
     * plugin priority. A direct application registration may explicitly replace the resulting
     * plugin contribution.
     *
     * @param name   stable non-blank policy name
     * @param policy thread-safe retry policy
     * @return this contribution context
     */
    ProcessEnginePluginContext retryPolicy(String name, RetryPolicy policy);

    /**
     * Registers a named failure handler referenced by an invocation policy's {@code onFailure} value.
     *
     * <p>Failure-handler names are unique across plugins; duplicates fail configuration regardless
     * of plugin priority. A direct application registration may explicitly replace the resulting
     * plugin contribution.
     *
     * @param name    stable non-blank handler name
     * @param handler thread-safe terminal failure handler
     * @return this contribution context
     */
    ProcessEnginePluginContext failureHandler(String name, FailureHandler handler);
}
