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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.spi.ProcessEnginePlugin;
import com.alibaba.compileflow.engine.spi.ProcessEnginePluginContext;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.FailureResolution;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import com.alibaba.compileflow.engine.spi.observability.TraceIdProvider;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingContext;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.io.IOException;
import java.util.Optional;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessEngineConfigPluginTest {
    private static final String PLUGIN_SERVICE_RESOURCE = "META-INF/services/" + ProcessEnginePlugin.class.getName();

    @Test
    void discoversClasspathPluginsWhenExplicitlyEnabled() {
        ProcessEngineConfig config = ProcessEngineConfig.builder().discoverPlugins(true).build();

        assertThat(config.getEventListeners())
            .extracting(listener -> ((NamedListener) listener).name)
            .containsExactly("discovered", "late");
        assertThat(config.getScriptExecutors()).extracting(ScriptExecutor::name).containsExactly("test-script");
    }

    @Test
    void disablingDiscoverySkipsClasspathPlugins() {
        ProcessEngineConfig config = ProcessEngineConfig.builder().discoverPlugins(false).build();

        assertThat(config.getEventListeners()).isEmpty();
        assertThat(config.getScriptExecutors()).isEmpty();
    }

    @Test
    void explicitBuilderContributionsComposeWithDistinctPluginLanguages() {
        NamedExecutor explicitMvel = new NamedExecutor("mvel");
        NamedListener explicitListener = new NamedListener("explicit");

        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(true)
            .scriptExecutor(explicitMvel)
            .eventListener(explicitListener)
            .build();

        assertThat(config.getScriptExecutors()).extracting(ScriptExecutor::name).containsExactly("test-script", "mvel");
        // Listeners append: plugin contributions first, explicit contributions after.
        assertThat(config.getEventListeners())
            .extracting(listener -> ((NamedListener) listener).name)
            .containsExactly("discovered", "late", "explicit");
    }

    @Test
    void explicitPluginsComposeAfterDiscoveredPluginsWithDistinctLanguages() {
        NamedExecutor explicit = new NamedExecutor("mvel");
        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(true)
            .plugin(ProcessEnginePlugin.of("test.explicit", Integer.MIN_VALUE, context -> context.scriptExecutor(
                    explicit)))
            .build();

        assertThat(config.getScriptExecutors()).extracting(ScriptExecutor::name).containsExactly("test-script", "mvel");
    }

    @Test
    void ordersExplicitPluginsByPriorityThenStableId() {
        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.zulu", context -> context.eventListener(new NamedListener("zulu"))))
            .plugin(ProcessEnginePlugin.of("test.alpha", context -> context.eventListener(new NamedListener("alpha"))))
            .build();

        assertThat(config.getEventListeners())
            .extracting(listener -> ((NamedListener) listener).name)
            .containsExactly("alpha", "zulu");
    }

    @Test
    void pluginFailureFailsFast() {
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.failure", context -> {
                throw new IllegalStateException("bad plugin");
            }))
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("ProcessEnginePlugin 'test.failure' failed")
            .hasRootCauseMessage("bad plugin");
    }

    @Test
    void pluginContributesEngineWideCapabilities() {
        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.listener", context -> {
                context.eventListener(new NamedListener("all-definitions"));
            }))
            .build();

        assertThat(config.getEventListeners()).hasSize(1);
    }

    @Test
    void explicitPluginReplacesDiscoveredPluginWithSameId() {
        ProcessEnginePlugin explicit = new DiscoveredPlugin("explicit-replacement");
        ProcessEngineConfig config = ProcessEngineConfig.builder().discoverPlugins(true).plugin(explicit).build();

        assertThat(config.getEventListeners())
            .extracting(listener -> ((NamedListener) listener).name)
            .containsExactly("late", "explicit-replacement");
        assertThat(config.getScriptExecutors()).hasSize(1);
    }

    @Test
    void duplicateExplicitPluginIdsFailFast() {
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(new NamedPlugin("duplicate", "first"))
            .plugin(new NamedPlugin("duplicate", "second"))
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Duplicate explicit ProcessEnginePlugin id 'duplicate'");
    }

    @Test
    void rejectsUnsafePluginIdentifiers() {
        for (String id : new String[] {"\u00a0plugin", "plugin\u00a0", "plug\u200bin", "plug\ud800in"}) {
            assertThatThrownBy(() -> ProcessEngineConfig
                .builder()
                .plugin(ProcessEnginePlugin.of(id, context -> {}))
                .build())
                .isInstanceOf(CompileFlowException.ConfigurationException.class)
                .hasMessageContaining("ProcessEnginePlugin id");
        }
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of(" padded ", context -> {}))
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("surrounding whitespace");

        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("bad\nplugin", context -> {}))
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("must not contain control characters");

        String oversized = "x".repeat(257);
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of(oversized, context -> {}))
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("must not exceed 256 characters");
    }

    @Test
    void snapshotsPluginMetadataOncePerConfigurationBuild() {
        AtomicInteger idReads = new AtomicInteger();
        AtomicInteger priorityReads = new AtomicInteger();
        ProcessEnginePlugin plugin = new ProcessEnginePlugin() {
            @Override
            public void apply(ProcessEnginePluginContext context) {
                context.eventListener(new NamedListener("stable"));
            }

            @Override
            public String id() {
                idReads.incrementAndGet();
                return "test.stable-metadata";
            }

            @Override
            public int priority() {
                priorityReads.incrementAndGet();
                return 50;
            }
        };

        ProcessEngineConfig config = ProcessEngineConfig.builder().discoverPlugins(false).plugin(plugin).build();

        assertThat(config.getEventListeners()).hasSize(1);
        assertThat(idReads).hasValue(1);
        assertThat(priorityReads).hasValue(1);
    }

    @Test
    void wrapsPluginMetadataFailuresAsConfigurationErrors() {
        ProcessEnginePlugin plugin = new ProcessEnginePlugin() {
            @Override
            public void apply(ProcessEnginePluginContext context) {}

            @Override
            public String id() {
                throw new IllegalStateException("metadata failure");
            }
        };

        assertThatThrownBy(() -> ProcessEngineConfig.builder().discoverPlugins(false).plugin(plugin).build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Failed to read explicit ProcessEnginePlugin metadata")
            .hasRootCauseMessage("metadata failure");
    }

    @Test
    void scriptExecutorRegistrationRejectsDuplicateCanonicalLanguage() {
        NamedExecutor first = new NamedExecutor("groovy");
        NamedExecutor second = new NamedExecutor("groovy");

        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .scriptExecutor(first)
            .scriptExecutor(second))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Duplicate script executors for language 'groovy'");
    }

    @Test
    void scriptLanguageDuplicatesAcrossPluginsFailClosedRegardlessOfPriority() {
        NamedExecutor first = new NamedExecutor("groovy");
        NamedExecutor second = new NamedExecutor("groovy");

        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.first", 1, context -> context.scriptExecutor(first)))
            .plugin(ProcessEnginePlugin.of("test.second", 2, context -> context.scriptExecutor(second)))
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Duplicate script executors for language 'groovy'");
    }

    @Test
    void directRegistrationCannotSilentlyOverrideAPluginScriptLanguage() {
        NamedExecutor plugin = new NamedExecutor("groovy");
        NamedExecutor direct = new NamedExecutor("groovy");

        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.plugin", context -> context.scriptExecutor(plugin)))
            .scriptExecutor(direct)
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Duplicate script executors for language 'groovy'");
    }

    @Test
    void configurationsFreezeIndependentCapabilitySnapshots() {
        NamedExecutor first = new NamedExecutor("first");
        NamedExecutor second = new NamedExecutor("second");

        ProcessEngineConfig firstConfig =
                ProcessEngineConfig.builder().discoverPlugins(false).scriptExecutor(first).build();
        ProcessEngineConfig secondConfig =
                ProcessEngineConfig.builder().discoverPlugins(false).scriptExecutor(second).build();

        assertThat(firstConfig.getScriptExecutors()).containsExactly(first);
        assertThat(secondConfig.getScriptExecutors()).containsExactly(second);
        assertThatThrownBy(() -> firstConfig.getScriptExecutors().add(second))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void retainedPluginContextCannotMutateBuiltConfiguration() {
        AtomicReference<ProcessEnginePluginContext> retainedContext = new AtomicReference<>();
        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.retained-context", context -> {
                retainedContext.set(context);
                context.eventListener(new NamedListener("initial"));
            }))
            .build();

        retainedContext.get().eventListener(new NamedListener("late"));

        assertThat(config.getEventListeners())
            .extracting(listener -> ((NamedListener) listener).name)
            .containsExactly("initial");
    }

    @Test
    void replacingObservabilitySnapshotReplacesScalarBehavior() {
        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .observability(ProcessObservabilityConfig.builder().eventsAsync(false).build())
            .observability(ProcessObservabilityConfig
                .builder()
                .eventsAsync(true)
                .mdcPropagationEnabled(true)
                .build())
            .build();

        assertThat(config.getObservabilityConfig().isEventsAsync()).isTrue();
        assertThat(config.getObservabilityConfig().isMdcPropagationEnabled()).isTrue();
        assertThat(config.getEventListeners()).isEmpty();
    }

    @Test
    void singletonAuthoritiesAreConfiguredDirectly() {
        TraceIdProvider directProvider = () -> "direct";
        ProcessContextPropagator contextPropagator = ProcessContextPropagator.none();
        NamedListener directListener = new NamedListener("direct");

        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.layering", context -> context.eventListener(
                    new NamedListener("plugin"))))
            .eventListener(directListener)
            .traceIdProvider(directProvider)
            .contextPropagator(contextPropagator)
            .build();

        assertThat(config.getEventListeners())
            .extracting(listener -> ((NamedListener) listener).name)
            .containsExactly("plugin", "direct");
        assertThat(config.getTraceIdProvider()).isSameAs(directProvider);
        assertThat(config.getContextPropagator()).isSameAs(contextPropagator);
    }

    @Test
    void aliasRouteAuthorityIsConfiguredDirectly() {
        ProcessAliasRouteSource source = alias -> Optional.empty();

        ProcessEngineConfig config =
                ProcessEngineConfig.builder().discoverPlugins(false).aliasRouteSource(source).build();

        assertThat(config.getAliasRouteSource()).isSameAs(source);
    }

    @Test
    void pluginsAndDirectConfigurationRegisterNamedExecutionPolicies() {
        RetryPolicy pluginRetry = failure -> false;
        RetryPolicy directRetry = failure -> true;
        FailureHandler pluginFailure = context -> FailureResolution.FAIL_PROCESS;
        FailureHandler directFailure = context -> FailureResolution.CONTINUE_PROCESS;

        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.execution-policies", context -> context
                .retryPolicy("custom-retry", pluginRetry)
                .failureHandler("custom-failure", pluginFailure)))
            .retryPolicy("custom-retry", directRetry)
            .failureHandler("custom-failure", directFailure)
            .build();

        assertThat(config.getRetryPolicies()).containsEntry("custom-retry", directRetry);
        assertThat(config.getFailureHandlers()).containsEntry("custom-failure", directFailure);
        assertThatThrownBy(() -> config.getRetryPolicies().put("late", pluginRetry))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void retryPolicyDuplicatesAcrossPluginsFailClosedRegardlessOfPriority() {
        RetryPolicy first = failure -> false;
        RetryPolicy second = failure -> true;

        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.first", 1, context -> context.retryPolicy("network", first)))
            .plugin(ProcessEnginePlugin.of("test.second", 2, context -> context.retryPolicy("network", second)))
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Duplicate retry policies for name 'network'");
    }

    @Test
    void failureHandlerDuplicatesAcrossPluginsFailClosedRegardlessOfPriority() {
        FailureHandler first = context -> FailureResolution.FAIL_PROCESS;
        FailureHandler second = context -> FailureResolution.CONTINUE_PROCESS;

        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.first", 1, context -> context.failureHandler("network", first)))
            .plugin(ProcessEnginePlugin.of("test.second", 2, context -> context.failureHandler("network", second)))
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Duplicate failure handlers for name 'network'");
    }

    @Test
    void duplicateSemanticNamesFailEvenWhenBothRegistrationsUseTheSameInstance() {
        RetryPolicy retry = failure -> false;
        FailureHandler failure = context -> FailureResolution.FAIL_PROCESS;
        ProcessAliasTargetingPolicy targeting = new ProcessAliasTargetingPolicy() {
            @Override
            public String name() {
                return "tenant-cohort";
            }

            @Override
            public Optional<ProcessAliasTarget> target(ProcessAliasTargetingContext context) {
                return Optional.empty();
            }
        };

        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.first", context -> context.retryPolicy("network", retry)))
            .plugin(ProcessEnginePlugin.of("test.second", context -> context.retryPolicy("network", retry)))
            .build())
            .hasMessageContaining("Duplicate retry policies for name 'network'");
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .plugin(ProcessEnginePlugin.of("test.first", context -> context.failureHandler("network", failure)))
            .plugin(ProcessEnginePlugin.of("test.second", context -> context.failureHandler("network", failure)))
            .build())
            .hasMessageContaining("Duplicate failure handlers for name 'network'");
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .aliasTargetingPolicy(targeting)
            .aliasTargetingPolicy(targeting))
            .hasMessageContaining("Duplicate Alias targeting policies for name 'tenant-cohort'");
    }

    @Test
    void rejectsReservedOrUnsafeExecutionPolicyNames() {
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .retryPolicy("always", failure -> true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reserved");
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .failureHandler("propagate", context -> FailureResolution.FAIL_PROCESS))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reserved");
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .failureHandler("bad\nname", context -> FailureResolution.FAIL_PROCESS))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lowercase kebab-case");
    }

    @Test
    void fallsBackToTheApiClassLoaderWhenTheThreadContextLoaderIsNull() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            ProcessEngineConfig config = ProcessEngineConfig.builder().discoverPlugins(false).build();

            assertThat(config.getClassLoader()).isSameAs(ProcessEngineConfig.class.getClassLoader());
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    void pluginDiscoveryUsesTheConfiguredClassLoader() {
        ClassLoader noPluginResources = new ClassLoader(ProcessEngineConfigPluginTest.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (PLUGIN_SERVICE_RESOURCE.equals(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getResources(name);
            }
        };

        ProcessEngineConfig config =
                ProcessEngineConfig.builder().discoverPlugins(true).classLoader(noPluginResources).build();

        assertThat(config.getEventListeners()).isEmpty();
        assertThat(config.getScriptExecutors()).isEmpty();
        assertThat(config.getClassLoader()).isSameAs(noPluginResources);
    }

    @Test
    void malformedServiceProviderFailsAsConfigurationError(@TempDir Path tempDir) throws IOException {
        Path serviceFile = tempDir.resolve(PLUGIN_SERVICE_RESOURCE);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, "com.example.MissingProcessEnginePlugin\n");
        URL serviceResource = serviceFile.toUri().toURL();
        ClassLoader malformedServiceLoader = new ClassLoader(ProcessEngineConfigPluginTest.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (PLUGIN_SERVICE_RESOURCE.equals(name)) {
                    return Collections.enumeration(Collections.singleton(serviceResource));
                }
                return super.getResources(name);
            }
        };

        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .discoverPlugins(true)
            .classLoader(malformedServiceLoader)
            .build())
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Failed to discover ProcessEnginePlugin providers")
            .hasCauseInstanceOf(ServiceConfigurationError.class);
    }

    /**
     * Discovered via META-INF/services in test resources.
     */
    public static final class DiscoveredPlugin implements ProcessEnginePlugin {
        private final String listenerName;

        public DiscoveredPlugin() {
            this("discovered");
        }

        private DiscoveredPlugin(String listenerName) {
            this.listenerName = listenerName;
        }

        @Override
        public void apply(ProcessEnginePluginContext context) {
            context.eventListener(new NamedListener(listenerName));
            context.scriptExecutor(new NamedExecutor("test-script"));
        }

        @Override
        public int priority() {
            return 10;
        }

        @Override
        public String id() {
            return "test.discovered";
        }
    }

    /**
     * Discovered via META-INF/services in test resources; applies after DiscoveredPlugin.
     */
    public static final class LateDiscoveredPlugin implements ProcessEnginePlugin {
        @Override
        public void apply(ProcessEnginePluginContext context) {
            context.eventListener(new NamedListener("late"));
        }

        @Override
        public int priority() {
            return 20;
        }

        @Override
        public String id() {
            return "test.discovered.late";
        }
    }

    private static final class NamedListener implements ProcessEventListener {
        private final String name;

        private NamedListener(String name) {
            this.name = name;
        }

        @Override
        public void onEvent(ProcessEvent event) {}
    }

    private static final class NamedPlugin implements ProcessEnginePlugin {
        private final String id;
        private final String listenerName;

        private NamedPlugin(String id, String listenerName) {
            this.id = id;
            this.listenerName = listenerName;
        }

        @Override
        public void apply(ProcessEnginePluginContext context) {
            context.eventListener(new NamedListener(listenerName));
        }

        @Override
        public String id() {
            return id;
        }
    }

    private static final class NamedExecutor implements ScriptExecutor {
        private final String name;

        private NamedExecutor(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void validate(ScriptProgramSpec spec) {}

        @Override
        public ScriptProgram compile(ScriptProgramSpec spec) {
            return new NamedProgram(ScriptExecutor.requireCanonicalName(name), spec.source());
        }

        @Override
        public Object evaluate(ScriptProgram script, Map<String, Object> context) {
            return ((NamedProgram) script).source();
        }
    }

    private record NamedProgram(String language, String source) implements ScriptProgram {}
}
