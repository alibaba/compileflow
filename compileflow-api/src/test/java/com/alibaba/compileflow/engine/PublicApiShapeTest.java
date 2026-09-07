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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.ProcessEnginePlugin;
import com.alibaba.compileflow.engine.spi.ProcessEnginePluginContext;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.FailureResolution;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import com.alibaba.compileflow.engine.spi.observability.TraceIdProvider;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class PublicApiShapeTest {
    private static final Map<String, Set<String>> PUBLIC_TOP_LEVEL_TYPES = Map.ofEntries(Map.entry("com.alibaba."
                    + "compileflow.engine",
                    Set.of("CompileFlowException", "ErrorCode", "ProcessAliasTarget", "ProcessDataMapper",
                            "ProcessDefinition", "ProcessEngine", "ProcessEngineFactory", "ProcessError",
                            "ProcessExecution", "ProcessExecutionOptions", "ProcessModelType", "ProcessRef",
                            "ProcessResult", "AliasRoutingOptions", "ProcessDefinitionDigest", "ProcessIdentifiers",
                            "ProcessExecutionException", "ProcessRuntimeManager", "ProcessText", "ProcessToolingService",
                            "ProcessTrigger")),
            Map.entry("com.alibaba.compileflow.engine.config",
                    Set.of("JavaDiagnosticsConfig", "ProcessDefinitionConfig", "ProcessEngineConfig",
                            "ProcessExecutorConfig", "ProcessObservabilityConfig", "ProcessRuntimeMode")),
            Map.entry("com.alibaba.compileflow.engine.preflight",
                    Set.of("ProcessPreflightOptions", "ProcessPreflightReport")),
            Map.entry("com.alibaba.compileflow.engine.spi",
                    Set.of("ProcessComponentResolver", "ProcessEnginePlugin", "ProcessEnginePluginContext",
                            "ProcessEngineProvider")),
            Map.entry("com.alibaba.compileflow.engine.spi.event", Set.of("ProcessEvent", "ProcessEventListener")),
            Map.entry("com.alibaba.compileflow.engine.spi.execution",
                    Set.of("ActionExecutionContext", "FailureContext", "FailureHandler", "FailureResolution",
                            "ProcessContextPropagator", "RetryPolicy")),
            Map.entry("com.alibaba.compileflow.engine.spi.observability", Set.of("TraceIdProvider")),
            Map.entry("com.alibaba.compileflow.engine.spi.routing",
                    Set.of("AliasTargeting", "ProcessAliasRoute", "ProcessAliasRouteSource",
                            "ProcessAliasTargetingContext", "ProcessAliasTargetingPolicy")),
            Map.entry("com.alibaba.compileflow.engine.spi.script",
                    Set.of("ScriptProgram", "ScriptException", "ScriptExecutor", "ScriptProgramSpec")));

    private static String[] recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toArray(String[]::new);
    }

    private static Set<String> instanceFieldNames(Class<?> type) {
        return Arrays
            .stream(type.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(Field::getName)
            .collect(java.util.stream.Collectors.toSet());
    }

    private static Set<String> publicGetterNames(Class<?> type) {
        return Arrays
            .stream(type.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .filter(method -> !Modifier.isStatic(method.getModifiers()))
            .filter(method -> method.getParameterCount() == 0)
            .map(Method::getName)
            .filter(name -> name.startsWith("get"))
            .collect(java.util.stream.Collectors.toSet());
    }

    private static Set<String> declaredMethodNames(Class<?> type) {
        return Arrays
            .stream(type.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .map(Method::getName)
            .collect(java.util.stream.Collectors.toSet());
    }

    private static Map<String, Set<String>> publicTopLevelTypes() throws Exception {
        URL location = ProcessEngine.class.getProtectionDomain().getCodeSource().getLocation();
        Path codeSource = Path.of(location.toURI());
        Set<String> classNames = new HashSet<>();
        String packagePath = "com/alibaba/compileflow/engine/";
        if (Files.isDirectory(codeSource)) {
            Path packageDirectory = codeSource.resolve(packagePath);
            try (var files = Files.walk(packageDirectory)) {
                files
                    .filter(Files::isRegularFile)
                    .map(codeSource::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(PublicApiShapeTest::isTopLevelClassFile)
                    .map(PublicApiShapeTest::className)
                    .forEach(classNames::add);
            }
        } else {
            try (JarFile jar = new JarFile(codeSource.toFile())) {
                jar
                    .stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith(packagePath))
                    .filter(PublicApiShapeTest::isTopLevelClassFile)
                    .map(PublicApiShapeTest::className)
                    .forEach(classNames::add);
            }
        }
        classNames.removeIf(name -> !Modifier.isPublic(loadType(name).getModifiers()));

        Map<String, Set<String>> typesByPackage = new HashMap<>();
        for (String name : classNames) {
            int separator = name.lastIndexOf('.');
            String packageName = name.substring(0, separator);
            String simpleName = name.substring(separator + 1);
            typesByPackage
                .computeIfAbsent(packageName, ignored -> new HashSet<>())
                .add(simpleName);
        }
        typesByPackage.replaceAll((ignored, types) -> Set.copyOf(types));
        return Map.copyOf(typesByPackage);
    }

    private static boolean isTopLevelClassFile(String name) {
        return name.endsWith(".class") && !name.contains("$") && !name.endsWith("module-info.class")
                && !name.endsWith("package-info.class");
    }

    private static String className(String classFileName) {
        return classFileName.substring(0, classFileName.length() - ".class".length()).replace('/', '.');
    }

    private static Class<?> loadType(String name) {
        try {
            return Class.forName(name, false, ProcessEngine.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Unable to load public API type " + name, exception);
        }
    }

    @Test
    void keepsEveryPublicTopLevelTypeExplicitlyAllowlisted() throws Exception {
        assertThat(publicTopLevelTypes())
            .as("complete public API package and type allowlist")
            .isEqualTo(PUBLIC_TOP_LEVEL_TYPES);
    }

    @Test
    void keepsEngineAndToolingFormatNeutral() {
        assertThat(ProcessEngine.class.getTypeParameters()).isEmpty();
        assertThat(ProcessToolingService.class.getTypeParameters()).isEmpty();
        assertThat(Arrays
            .stream(ProcessToolingService.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::getName))
            .containsExactlyInAnyOrder("generateJavaCode", "preflight");
    }

    @Test
    void separatesReferencesFromDefinitionSources() {
        assertThat(ProcessRef.class.getPermittedSubclasses())
            .containsExactlyInAnyOrder(ProcessRef.Version.class, ProcessRef.Alias.class);
        assertThat(ProcessDefinition.class.getPermittedSubclasses())
            .containsExactlyInAnyOrder(ProcessDefinition.Inline.class, ProcessDefinition.Classpath.class);
        assertThat(recordComponentNames(ProcessRef.Version.class)).containsExactly("namespace", "code", "version");
        assertThat(recordComponentNames(ProcessRef.Alias.class)).containsExactly("namespace", "code", "alias");

        assertThat(recordComponentNames(ProcessDefinition.Inline.class)).containsExactly("modelType", "code", "content");
        assertThat(recordComponentNames(ProcessDefinition.Classpath.class))
            .containsExactly("modelType", "code", "resourcePath");
        assertThat(Arrays
            .stream(ProcessEngine.class.getMethods())
            .filter(method -> method.getName().equals("execute"))
            .map(Method::getParameterTypes)
            .map(types -> types[0]))
            .doesNotContain(String.class);
    }

    @Test
    void keepsResultModelTypedAndBounded() {
        assertThat(instanceFieldNames(ProcessResult.class)).containsExactlyInAnyOrder("output", "error", "execution");
        assertThat(instanceFieldNames(ProcessError.class)).containsExactlyInAnyOrder("code", "message");
        assertThat(instanceFieldNames(ProcessExecution.class))
            .containsExactlyInAnyOrder("traceId", "invocationId", "namespace", "processCode", "processVersion",
                    "startedAt", "completedAt");
        assertThat(publicGetterNames(ProcessExecution.class))
            .containsExactlyInAnyOrder("getTraceId", "getInvocationId", "getNamespace", "getProcessCode",
                    "getProcessVersion", "getStartedAt", "getCompletedAt");
        assertThat(declaredMethodNames(ProcessResult.class))
            .containsExactlyInAnyOrder("success", "failure", "isSuccess", "isFailure", "getOutput", "getError",
                    "getExecution", "map", "orElse", "orElseGet", "orElseThrow", "toString");
    }

    @Test
    void keepsLifecycleEventsTypedAndBounded() {
        assertThat(ProcessEvent.class.isSealed()).isTrue();
        assertThat(recordComponentNames(ProcessEvent.ExecutionStarted.class))
            .containsExactly("namespace", "processCode", "invocationId", "traceId", "occurredAt");
        assertThat(recordComponentNames(ProcessEvent.ExecutionCompleted.class))
            .containsExactly("execution", "attribution", "durationMs", "occurredAt");
        assertThat(recordComponentNames(ProcessEvent.ExecutionFailed.class))
            .containsExactly("execution", "attribution", "durationMs", "error", "occurredAt");
        assertThat(recordComponentNames(ProcessEvent.TriggerStarted.class))
            .containsExactly("namespace", "processCode", "invocationId", "trigger", "traceId", "occurredAt");
        assertThat(recordComponentNames(ProcessEvent.TriggerCompleted.class))
            .containsExactly("execution", "attribution", "trigger", "durationMs", "occurredAt");
        assertThat(recordComponentNames(ProcessEvent.TriggerFailed.class))
            .containsExactly("execution", "attribution", "trigger", "durationMs", "error", "occurredAt");
        assertThat(Arrays
            .stream(ProcessEvent.class.getPermittedSubclasses())
            .flatMap(type -> Arrays.stream(type.getRecordComponents()))
            .map(RecordComponent::getName))
            .doesNotContain("routingKey", "variables", "metadata", "exception", "cause");
    }

    @Test
    void exposesSupportedSpiContracts() {
        assertThat(ProcessEnginePlugin.class).isInterface();
        assertThat(ProcessEnginePluginContext.class).isInterface();
        assertThat(ProcessComponentResolver.class).isInterface();
        assertThat(ProcessEventListener.class).isInterface();
        assertThat(RetryPolicy.class).isInterface();
        assertThat(FailureHandler.class).isInterface();
        assertThat(FailureResolution.class.isEnum()).isTrue();
        assertThat(ProcessContextPropagator.class).isInterface();
        assertThat(TraceIdProvider.class).isInterface();
        assertThat(ProcessAliasRoute.class.isRecord()).isTrue();
        assertThat(ProcessAliasRouteSource.class).isInterface();
        assertThat(declaredMethodNames(ProcessEnginePluginContext.class))
            .containsExactlyInAnyOrder("eventListener", "scriptExecutor", "aliasTargetingPolicy", "retryPolicy",
                    "failureHandler");
        assertThat(ScriptExecutor.class).isInterface();
        assertThat(declaredMethodNames(ScriptExecutor.class))
            .containsExactlyInAnyOrder("requireCanonicalName", "name", "validate", "compile", "evaluate");
        assertThat(Arrays
            .stream(ScriptExecutor.class.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())).hasSize(5);
    }
}
