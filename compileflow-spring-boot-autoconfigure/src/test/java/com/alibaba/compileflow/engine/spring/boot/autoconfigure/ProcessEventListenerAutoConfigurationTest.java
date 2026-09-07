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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.ProcessEnginePlugin;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.FailureResolution;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import com.alibaba.compileflow.engine.spi.observability.TraceIdProvider;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.resolution.SpringProcessComponentResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ProcessEventListenerAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                CompileFlowContextPropagationAutoConfiguration.class, CompileFlowEngineAutoConfiguration.class))
        // Engine creation is exercised elsewhere; this test focuses on config assembly.
        .withPropertyValues("compileflow.engine.enabled=true");

    @Test
    void collectsTypedSpiBeansIntoTheEngineConfiguration() {
        runner
            .withUserConfiguration(TypedSpiBeans.class)
            .run(context -> {
                ProcessEngineConfig config = context.getBean(ProcessEngineConfig.class);

                List<ProcessEventListener> listeners = config.getEventListeners();
                assertThat(listeners).contains(context.getBean("recordingListener", ProcessEventListener.class));

                assertThat(config.getTraceIdProvider()).isSameAs(context.getBean(TraceIdProvider.class));
                assertThat(config.getContextPropagator()).isSameAs(context.getBean(ProcessContextPropagator.class));

                assertThat(config.getScriptExecutors()).extracting(ScriptExecutor::name).contains("aviator");

                assertThat(config.getRetryPolicies()).containsEntry("business-retry", context.getBean(RetryPolicy.class));
                assertThat(config.getFailureHandlers()).containsEntry("business-failure",
                        context.getBean(FailureHandler.class));
            });
    }

    @Test
    void listenerBeansReceiveDispatchedEvents() {
        runner
            .withUserConfiguration(TypedSpiBeans.class)
            .run(context -> {
                ProcessEngineConfig config = context.getBean(ProcessEngineConfig.class);
                RecordingListener listener = context.getBean(RecordingListener.class);

                config
                    .getEventListeners()
                    .forEach(l -> l.onEvent(
                            new ProcessEvent.ExecutionStarted("default", "demo", "invocation-1", null, Instant.now())));

                assertThat(listener.received)
                    .singleElement()
                    .isInstanceOfSatisfying(ProcessEvent.ExecutionStarted.class, event -> assertThat(event.processCode())
                        .isEqualTo("demo"));
            });
    }

    @Test
    void customContextPropagatorReplacesTheMicrometerDefault() {
        ProcessContextPropagator custom = ProcessContextPropagator.none();

        runner
            .withBean(ProcessContextPropagator.class, () -> custom)
            .run(context -> {
                assertThat(context).hasSingleBean(ProcessContextPropagator.class);
                assertThat(context.getBean(ProcessEngineConfig.class).getContextPropagator()).isSameAs(custom);
            });
    }

    @Test
    void exposesNoSpringBeansByDefault() {
        runner
            .withUserConfiguration(ComponentBeans.class)
            .run(context -> {
                assertThat(context).doesNotHaveBean(ProcessComponentResolver.class);
                ProcessEngineConfig config = context.getBean(ProcessEngineConfig.class);
                assertThatThrownBy(() -> config.getComponentResolver().resolve("allowedComponent", Object.class))
                    .isInstanceOf(CompileFlowException.ConfigurationException.class);
            });
    }

    @Test
    void exposesOnlyExplicitlyAllowedSpringBeans() {
        runner
            .withUserConfiguration(ComponentBeans.class)
            .withPropertyValues("compileflow.engine.components.allowed-beans[0]=allowedComponent")
            .run(context -> {
                ProcessComponentResolver resolver = context.getBean(ProcessEngineConfig.class).getComponentResolver();
                assertThat(resolver).isInstanceOf(SpringProcessComponentResolver.class);
                assertThat(resolver.resolve("allowedComponent", Object.class)).isSameAs(context.getBean(
                        "allowedComponent"));
                assertThatThrownBy(() -> resolver.resolve("hiddenComponent", Object.class))
                    .isInstanceOf(CompileFlowException.ConfigurationException.class);
            });
    }

    @Test
    void failsForUnknownAllowedBeanOrAmbiguousSingleCollaborator() {
        runner
            .withPropertyValues("compileflow.engine.components.allowed-beans[0]=missingComponent")
            .run(context -> assertThat(context).hasFailed());

        runner
            .withUserConfiguration(CustomComponentResolver.class)
            .withPropertyValues("compileflow.engine.components.allowed-beans[0]=allowedComponent")
            .run(context -> assertThat(context).hasFailed());

        runner
            .withBean("firstAliasRouteSource", ProcessAliasRouteSource.class, () -> alias -> Optional.empty())
            .withBean("secondAliasRouteSource", ProcessAliasRouteSource.class, () -> alias -> Optional.empty())
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void mapsPluginDiscoveryProperty() {
        runner
            .withPropertyValues("compileflow.engine.plugins.discovery-enabled=false")
            .run(context -> assertThat(context).hasSingleBean(ProcessEngineConfig.class));
    }

    @Test
    void collectsAndDeterministicallyOrdersPluginBeans() {
        runner
            .withUserConfiguration(PluginBeans.class)
            .withPropertyValues("compileflow.engine.plugins.discovery-enabled=false")
            .run(context -> assertThat(context.getBean(ProcessEngineConfig.class).getScriptExecutors())
                .extracting(ScriptExecutor::name)
                .containsExactly("alpha", "zulu"));
    }

    @Test
    void rejectsDuplicateScriptLanguageRegistrationsEvenForTheSameInstance() {
        ScriptExecutor executor = PluginBeans.namedExecutor("groovy");
        runner
            .withBean("firstGroovy", ScriptExecutor.class, () -> executor)
            .withBean("secondGroovy", ScriptExecutor.class, () -> executor)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(CompileFlowException.ConfigurationException.class)
                    .hasRootCauseMessage(
                            "Duplicate script executors for language 'groovy': " + executor.getClass().getName()
                            + " and " + executor.getClass().getName());
            });
    }

    @Test
    void rejectsAmbiguousScriptExecutorBeansForTheSameLanguage() {
        runner
            .withBean("firstGroovy", ScriptExecutor.class, () -> PluginBeans.namedExecutor("groovy"))
            .withBean("secondGroovy", ScriptExecutor.class, () -> PluginBeans.namedExecutor("groovy"))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(CompileFlowException.ConfigurationException.class)
                    .hasRootCauseMessage(
                            "Duplicate script executors for language 'groovy': "
                            + PluginBeans.namedExecutor("groovy").getClass().getName() + " and "
                            + PluginBeans.namedExecutor("groovy").getClass().getName());
            });
    }

    @Test
    void assemblesSharedVersionStateBeforePublishingTheEngineBean() {
        runner
            .withUserConfiguration(VersionStateBeans.class)
            .run(context -> {
                LocalRoutingState localRoutingState = context.getBean(LocalRoutingState.class);
                ProcessEngine engine = context.getBean(ProcessEngine.class);
                ProcessRef.Version ref = ProcessRef.version("default", "autoconfigure.shared-state", "v1");

                engine
                    .runtime()
                    .load(ref,
                            ProcessDefinition.inline(ProcessModelType.TBBPM, ref.code(),
                                    """
                <bpm code="autoconfigure.shared-state">
                    <start id="start" name="Start" g="50,50,32,32">
                        <transition to="end"/>
                    </start>
                    <end id="end" name="End" g="180,50,32,32"/>
                </bpm>
                """));

                assertThat(localRoutingState
                    .getInstalledVersionState()
                    .contains(ref.namespace(), ref.code(), ref.version())).isTrue();
            });
    }

    @Test
    void autoConfiguredEngineIncludesBuiltInJavaScriptExecutor() {
        runner.run(context -> {
            ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "autoconfigure.java-script",
                    """
                <bpm code="autoconfigure.java-script">
                    <var name="left" dataType="java.lang.Integer" inOutType="param"/>
                    <var name="right" dataType="java.lang.Integer" inOutType="param"/>
                    <var name="result" dataType="java.lang.Integer" inOutType="return"/>
                    <start id="start" g="0,0,32,32"><transition to="sum"/></start>
                    <scriptTask id="sum" g="64,0,100,40">
                        <action type="script" language="java">
                                <input target="left" dataType="java.lang.Integer"
                                     source="left"/>
                                <input target="right" dataType="java.lang.Integer"
                                     source="right"/>
                                <output dataType="java.lang.Integer"
                                     target="result"/>
                                <code>return left + right;</code>

                        </action>
                        <transition to="end"/>
                    </scriptTask>
                    <end id="end" g="200,0,32,32"/>
                </bpm>
                """);

            ProcessResult<Map<String, Object>> result =
                    context.getBean(ProcessEngine.class).execute(definition, Map.of("left", 20, "right", 22));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("result", 42);
        });
    }

    @Test
    void backsOffWhenTheApplicationProvidesACompleteEngine() {
        runner
            .withUserConfiguration(CustomEngineBean.class)
            .run(context -> {
                ProcessEngine customEngine = context.getBean("customEngine", ProcessEngine.class);

                assertThat(context).hasSingleBean(ProcessEngine.class);
                assertThat(context.getBean(ProcessEngine.class)).isSameAs(customEngine);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class TypedSpiBeans {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }

        @Bean
        TraceIdProvider fixedTraceIdProvider() {
            return () -> "trace-fixed";
        }

        @Bean
        ScriptExecutor aviatorExecutor() {
            return TestScriptExecutors.of("aviator", (source, context) -> source);
        }

        @Bean
        RetryPolicy businessRetryPolicy() {
            return failure -> true;
        }

        @Bean
        FailureHandler businessFailureHandler() {
            return context -> FailureResolution.CONTINUE_PROCESS;
        }

        @Bean
        ProcessEnginePlugin businessPolicies(RetryPolicy retryPolicy, FailureHandler failureHandler) {
            return ProcessEnginePlugin.of("business-policies", plugin -> plugin
                .retryPolicy("business-retry", retryPolicy)
                .failureHandler("business-failure", failureHandler));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ComponentBeans {
        @Bean
        Object allowedComponent() {
            return new Object();
        }

        @Bean
        Object hiddenComponent() {
            return new Object();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomComponentResolver {
        @Bean
        ProcessComponentResolver customResolver() {
            return ProcessComponentResolver.disabled();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class VersionStateBeans {
        @Bean
        LocalRoutingState localRoutingState() {
            return new LocalRoutingState();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomEngineBean {
        @Bean
        ProcessEngine customEngine() {
            ProcessEngine engine = mock(ProcessEngine.class);
            when(engine.runtime()).thenReturn(mock(ProcessRuntimeManager.class));
            when(engine.tooling()).thenReturn(mock(ProcessToolingService.class));
            return engine;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PluginBeans {
        private static ScriptExecutor namedExecutor(String name) {
            return TestScriptExecutors.of(name, (source, context) -> source);
        }

        @Bean
        ProcessEnginePlugin zuluPlugin() {
            return ProcessEnginePlugin.of("test.zulu", context -> context.scriptExecutor(namedExecutor("zulu")));
        }

        @Bean
        ProcessEnginePlugin alphaPlugin() {
            return ProcessEnginePlugin.of("test.alpha", context -> context.scriptExecutor(namedExecutor("alpha")));
        }
    }

    static class RecordingListener implements ProcessEventListener {
        final List<ProcessEvent> received = new CopyOnWriteArrayList<>();

        @Override
        public void onEvent(ProcessEvent event) {
            this.received.add(event);
        }
    }
}
