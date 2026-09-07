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

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Aggregated extension entry point applied while a process engine configuration is built.
 * <p>
 * Plugins register composable typed capabilities: event listeners and named script, retry,
 * failure, and Alias-targeting implementations. Singleton application authorities such as
 * component resolution and trace correlation remain direct configuration. Each plugin receives
 * an isolated {@link ProcessEnginePluginContext}; explicit application configuration may replace
 * a named retry or failure capability. Drop-in JARs declare implementations under
 * {@code META-INF/services/com.alibaba.compileflow.engine.spi.ProcessEnginePlugin}.
 * <p>
 * Plugin application happens during configuration construction and fails fast on error.
 * Runtime listener failures remain isolated by the event publisher.
 * Plugin implementations must not retain the short-lived contribution context.
 * Service-loaded plugins execute as fully trusted application code with the host process's
 * permissions; discovery is not a sandbox or an untrusted-code boundary.
 *
 * @author yusu
 */
public interface ProcessEnginePlugin {
    /**
     * Creates a named plugin with the default priority.
     *
     * @param id           stable non-blank plugin identifier
     * @param contribution plugin contribution callback
     * @return named process engine plugin
     */
    static ProcessEnginePlugin of(String id, Consumer<ProcessEnginePluginContext> contribution) {
        return of(id, 100, contribution);
    }

    /**
     * Creates a named plugin with an explicit priority.
     *
     * @param id           stable non-blank plugin identifier
     * @param priority     application priority; lower values apply first
     * @param contribution plugin contribution callback
     * @return named process engine plugin
     */
    static ProcessEnginePlugin of(String id, int priority, Consumer<ProcessEnginePluginContext> contribution) {
        Objects.requireNonNull(contribution, "contribution");
        return new ProcessEnginePlugin() {
            @Override
            public void apply(ProcessEnginePluginContext context) {
                contribution.accept(context);
            }

            @Override
            public String id() {
                return id;
            }

            @Override
            public int priority() {
                return priority;
            }
        };
    }

    /**
     * Applies this plugin's typed capability contributions.
     * <p>
     * Implementations must be reusable and side-effect free beyond writing to the supplied
     * context. An explicitly registered instance may be applied more than once when an application
     * reuses a configuration builder.
     *
     * @param context isolated context collecting this plugin's typed contributions
     */
    void apply(ProcessEnginePluginContext context);

    /**
     * Returns the stable plugin identifier used for duplicate detection and explicit replacement.
     * Identifiers are exact, well-formed Unicode values of at most 256 code points, without
     * surrounding whitespace, control characters, or Unicode format characters.
     *
     * @return non-blank stable plugin identifier
     */
    String id();

    /**
     * Returns the application priority within the plugin's source layer. Lower values apply
     * first, which determines event-listener order only. Named capability identities never use
     * priority for replacement: duplicates fail closed. Explicitly registered plugins form a
     * higher-precedence layer than discovered plugins regardless of this value. Default is
     * {@code 100}.
     *
     * @return application priority
     */
    default int priority() {
        return 100;
    }
}
