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
package com.alibaba.compileflow.engine.config;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.spi.ProcessEnginePlugin;
import com.alibaba.compileflow.engine.spi.ProcessEnginePluginContext;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Resolves and applies the immutable plugin layer used by one engine configuration build.
 *
 * @author yusu
 */
final class ProcessEnginePluginResolver {
    private ProcessEnginePluginResolver() {
    }

    static Contributions resolve(ClassLoader classLoader, boolean discoverPlugins,
            List<ProcessEnginePlugin> explicitPlugins) {
        List<PluginDescriptor> ordered = resolvePlugins(classLoader, discoverPlugins, explicitPlugins);
        Contributions aggregate = new Contributions();
        for (PluginDescriptor descriptor : ordered) {
            ProcessEnginePlugin plugin = descriptor.plugin();
            Contributions contribution = new Contributions();
            try {
                plugin.apply(contribution);
            } catch (CompileFlowException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                        "ProcessEnginePlugin '" + descriptor.id() + "' failed: " + plugin.getClass().getName(),
                        exception);
            }
            aggregate.merge(contribution);
        }
        return aggregate;
    }

    private static List<PluginDescriptor> resolvePlugins(ClassLoader classLoader, boolean discoverPlugins,
            List<ProcessEnginePlugin> explicitPlugins) {
        List<ProcessEnginePlugin> discovered = discoverPlugins ? discover(classLoader) : List.of();
        Map<String, PluginDescriptor> discoveredById = indexPlugins(discovered, PluginSource.DISCOVERED);
        Map<String, PluginDescriptor> explicitById = indexPlugins(explicitPlugins, PluginSource.EXPLICIT);
        explicitById.keySet().forEach(discoveredById::remove);

        List<PluginDescriptor> ordered = new ArrayList<>(discoveredById.values());
        ordered.addAll(explicitById.values());
        ordered.sort(Comparator
            .comparingInt((PluginDescriptor descriptor) -> descriptor.source().precedence())
            .thenComparingInt(PluginDescriptor::priority)
            .thenComparing(PluginDescriptor::id));
        return ordered;
    }

    private static List<ProcessEnginePlugin> discover(ClassLoader classLoader) {
        List<ProcessEnginePlugin> discovered = new ArrayList<>();
        try {
            for (ProcessEnginePlugin plugin : ServiceLoader.load(ProcessEnginePlugin.class, classLoader)) {
                discovered.add(plugin);
            }
            return discovered;
        } catch (ServiceConfigurationError error) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Failed to discover ProcessEnginePlugin providers using class loader: " + classLoader, error);
        }
    }

    private static Map<String, PluginDescriptor> indexPlugins(List<ProcessEnginePlugin> plugins, PluginSource source) {
        Map<String, PluginDescriptor> byId = new LinkedHashMap<>();
        for (ProcessEnginePlugin plugin : plugins) {
            PluginDescriptor descriptor = describePlugin(Objects.requireNonNull(plugin, "plugin"), source);
            PluginDescriptor existing = byId.putIfAbsent(descriptor.id(), descriptor);
            if (existing != null) {
                throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                        "Duplicate " + source.label() + " ProcessEnginePlugin id '" + descriptor.id() + "': "
                        + existing.plugin().getClass().getName() + " and " + plugin.getClass().getName());
            }
        }
        return byId;
    }

    private static PluginDescriptor describePlugin(ProcessEnginePlugin plugin, PluginSource source) {
        String className = plugin.getClass().getName();
        try {
            String id = requirePluginId(plugin.id(), className);
            return new PluginDescriptor(source, id, plugin.priority(), plugin);
        } catch (CompileFlowException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Failed to read " + source.label() + " ProcessEnginePlugin metadata: " + className, exception);
        }
    }

    private static String requirePluginId(String id, String className) {
        try {
            return ProcessIdentifiers.requireExactIdentity(id, "ProcessEnginePlugin id",
                    ProcessExtensionNames.MAX_LENGTH);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    invalid.getMessage() + ": " + className, invalid);
        }
    }

    private enum PluginSource {
        DISCOVERED(0, "discovered"),
        EXPLICIT(1, "explicit");
        private final int precedence;
        private final String label;

        PluginSource(int precedence, String label) {
            this.precedence = precedence;
            this.label = label;
        }

        private int precedence() {
            return precedence;
        }

        private String label() {
            return label;
        }
    }

    private record PluginDescriptor(PluginSource source, String id, int priority, ProcessEnginePlugin plugin) {}

    /**
     * Contributions collected from plugins before explicit builder overrides apply.
     */
    static final class Contributions implements ProcessEnginePluginContext {
        private final List<ProcessEventListener> eventListeners = new ArrayList<>();
        private final Map<String, ScriptExecutor> scriptExecutors = new LinkedHashMap<>();
        private final Map<String, ProcessAliasTargetingPolicy> aliasTargetingPolicies = new LinkedHashMap<>();
        private final Map<String, RetryPolicy> retryPolicies = new LinkedHashMap<>();
        private final Map<String, FailureHandler> failureHandlers = new LinkedHashMap<>();

        @Override
        public ProcessEnginePluginContext eventListener(ProcessEventListener listener) {
            eventListeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        @Override
        public ProcessEnginePluginContext scriptExecutor(ScriptExecutor executor) {
            Objects.requireNonNull(executor, "scriptExecutor");
            String language = ScriptExecutor.requireCanonicalName(executor.name());
            ScriptExecutor existing = scriptExecutors.putIfAbsent(language, executor);
            if (existing != null) {
                throw duplicateScriptExecutor(language, existing, executor);
            }
            return this;
        }

        @Override
        public ProcessEnginePluginContext aliasTargetingPolicy(ProcessAliasTargetingPolicy policy) {
            ProcessAliasTargetingPolicy candidate = Objects.requireNonNull(policy, "aliasTargetingPolicy");
            String name = ProcessAliasTargetingPolicy.requireCanonicalName(candidate.name());
            ProcessAliasTargetingPolicy existing = aliasTargetingPolicies.putIfAbsent(name, candidate);
            if (existing != null) {
                throw duplicateAliasTargetingPolicy(name, existing, candidate);
            }
            return this;
        }

        @Override
        public ProcessEnginePluginContext retryPolicy(String name, RetryPolicy policy) {
            String key = ProcessExtensionNames.retryPolicy(name);
            RetryPolicy candidate = Objects.requireNonNull(policy, "retryPolicy");
            RetryPolicy existing = retryPolicies.putIfAbsent(key, candidate);
            if (existing != null) {
                throw duplicateRetryPolicy(key, existing, candidate);
            }
            return this;
        }

        @Override
        public ProcessEnginePluginContext failureHandler(String name, FailureHandler handler) {
            String key = ProcessExtensionNames.failureHandler(name);
            FailureHandler candidate = Objects.requireNonNull(handler, "failureHandler");
            FailureHandler existing = failureHandlers.putIfAbsent(key, candidate);
            if (existing != null) {
                throw duplicateFailureHandler(key, existing, candidate);
            }
            return this;
        }

        List<ProcessEventListener> eventListeners() {
            return eventListeners;
        }

        Map<String, ScriptExecutor> scriptExecutors() {
            return scriptExecutors;
        }

        Map<String, ProcessAliasTargetingPolicy> aliasTargetingPolicies() {
            return aliasTargetingPolicies;
        }

        Map<String, RetryPolicy> retryPolicies() {
            return retryPolicies;
        }

        Map<String, FailureHandler> failureHandlers() {
            return failureHandlers;
        }

        private void merge(Contributions contribution) {
            eventListeners.addAll(contribution.eventListeners);
            contribution.scriptExecutors.forEach((language, executor) -> {
                ScriptExecutor existing = scriptExecutors.putIfAbsent(language, executor);
                if (existing != null) {
                    throw duplicateScriptExecutor(language, existing, executor);
                }
            });
            contribution.aliasTargetingPolicies.forEach((name, policy) -> {
                ProcessAliasTargetingPolicy existing = aliasTargetingPolicies.putIfAbsent(name, policy);
                if (existing != null) {
                    throw duplicateAliasTargetingPolicy(name, existing, policy);
                }
            });
            contribution.retryPolicies.forEach((name, policy) -> {
                RetryPolicy existing = retryPolicies.putIfAbsent(name, policy);
                if (existing != null) {
                    throw duplicateRetryPolicy(name, existing, policy);
                }
            });
            contribution.failureHandlers.forEach((name, handler) -> {
                FailureHandler existing = failureHandlers.putIfAbsent(name, handler);
                if (existing != null) {
                    throw duplicateFailureHandler(name, existing, handler);
                }
            });
        }

        private static CompileFlowException.ConfigurationException duplicateScriptExecutor(String language,
                ScriptExecutor existing, ScriptExecutor duplicate) {
            return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Duplicate script executors for language '" + language + "': " + existing.getClass().getName()
                    + " and " + duplicate.getClass().getName());
        }

        private static CompileFlowException.ConfigurationException duplicateAliasTargetingPolicy(String name,
                ProcessAliasTargetingPolicy existing, ProcessAliasTargetingPolicy duplicate) {
            return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Duplicate Alias targeting policies for name '" + name + "': " + existing.getClass().getName()
                    + " and " + duplicate.getClass().getName());
        }

        private static CompileFlowException.ConfigurationException duplicateRetryPolicy(String name,
                RetryPolicy existing, RetryPolicy duplicate) {
            return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Duplicate retry policies for name '" + name + "': " + existing.getClass().getName() + " and "
                    + duplicate.getClass().getName());
        }

        private static CompileFlowException.ConfigurationException duplicateFailureHandler(String name,
                FailureHandler existing, FailureHandler duplicate) {
            return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Duplicate failure handlers for name '" + name + "': " + existing.getClass().getName() + " and "
                    + duplicate.getClass().getName());
        }
    }
}
